# 3. ドキュメントのフィールド値にアクセスする (`DocValues`)

多くのプラグインでは、ドキュメントのフィールド値を参照する必要があると思います。
題材のプラグインでもそうです。
本章では、題材のプラグインのコードを追いながら、この参照の方法について説明します。

Solrにドキュメントのフィールド値を保存する方法は、大きく分けて2通りあります。
スキーマでフィールドに `stored="true"` と指定する方法と、`docValues="true"` と指定する方法です。
通常、プラグインから参照するにはdocValuesのほうが便利ですので、本章ではこちらを説明します。

> Storedであっても、`FieldCache` に入れればdocValuesとほぼ同等に扱えますが、このキャッシュのウォームアップのコストが余分にかかります。

まずドキュメントを特定するためのIDについて説明します。
次にdocValuesを参照するための `DocValues` オブジェクトについて説明します。
シャードでなくサーチヘッドでdocValuesを参照する場合は、シャードからサーチヘッドへdocValuesを明示的に送る必要がありますので、その処理についても説明します。


## 3種類のドキュメントID

ドキュメントのフィールド値を参照するには、まずどのドキュメントかを特定する必要があります。
Solrには少なくとも3種類のドキュメントIDがあり、少し複雑ですので、事前準備としてこれらの使い分けについて説明します。

まず、Solrのインデックスは複数のシャードに分割されていることがあり、さらに、それぞれのシャード内でも**複数のインデックスセグメントに分割**されていることがあります。
このうち通用する範囲によって3種類のドキュメントIDがあります：

- uniqueKey: 特定のフィールドの値です。どのフィールドをuniqueKeyとするかはスキーマで指定します。通常、シャードをまたいでもユニークなドキュメントIDとして機能します。Solrユーザの目に触れるのはこの値です。
- シャードごとの通し番号：シャード内でのドキュメントの通し番号です。
- セグメントごとの通し番号：インデックスセグメント内でのドキュメントの通し番号です。

インデックスセグメントごとに `docBase` という値があり：

（シャードごとの通し番号）=（セグメントごとの通し番号）+（そのセグメントの `docBase`）

が成り立ちます。


## DocValuesオブジェクト

では、docValuesの参照について説明します。
まず、各シャード（サーチヘッドではない）でdocValuesを参照する方法を説明します。


### 生成・取得チェーン

DocValuesは、そのまま `DocValues` というオブジェクトを経由して参照します。
DocValuesには型があり、題材のプラグインでサポートする「多値属性ではない数値型フィールド」であればその型は `NUMERIC` （[公式ドキュメント](https://solr.apache.org/guide/7_3/docvalues.html#enabling-docvalues)）で、対応するオブジェクトは `NumericDocValues` です。
Solrのリクエスト処理の起点となる `IndexSearcher` から、`NumericDocValues` までのオブジェクト生成・取得チェーンは以下の通りです。

```
IndexSearcher
↓ IndexSearcher#getTopReaderContext
IndexReaderContext
↓ IndexReaderContext#leaves
List<LeafReaderContext>
↓ List#get
LeafReaderContext
↓ LeafReaderContext#reader
LeafReader
↓ LeafReader#getNumericDocValues
NumericDocValues
```

ただし、`LeafReader`（および `LeafReaderContext`）は1つのインデックスセグメントに対応するオブジェクトです。
そこから生成される `DocValues` も1つのインデックスセグメントに対応します。


### DocValuesオブジェクトの使い方

では、`DemoTopDocsCollector#topDocs` メソッド（[ソースコード](../src/main/java/jp/co/yahoo/solr/demo/DemoTopDocsCollector.java)）を題材に、`DocValues` オブジェクトの使い方を説明します。

DocValuesの参照は、シャードごとの通し番号順に行うと効率が良くなります（同時には1つの `DocValues` オブジェクトのみ覚えておけば良くなり、また、`DocValues` オブジェクトはセグメントごとの通し番号順に値を参照する前提のイテレータのため）。
通常、ドキュメントはこの順序では与えられないため、独自にソートする必要があります。
シャードごとの通し番号は、例えば `ScoreDoc` の `doc` メンバに入っています。

```java
    ScoreDoc[] sortedScoreDocs = wrappedScoreDocs.clone();
    Arrays.sort(sortedScoreDocs, 0, sortedScoreDocs.length, Comparator.comparingInt(d -> d.doc));
```

ある `doc` のドキュメントがどのインデックスセグメントに含まれるかは、`ReaderUtil#subIndex` で取得します。
`List<LeafReaderContext>` の添字として取得できます。

```java
        idx = ReaderUtil.subIndex(doc, leaves);
        currentLeaf = leaves.get(idx);
```

題材のプラグインでは、以降の処理は `NumericDocValuesAccessor` に切り出しています。
まず、コンストラクタで `NumericDocValues` オブジェクトを生成します。
ただしここで、注目するインデックスセグメントに注目するフィールドのdocValuesが全く存在しない場合、関連するオブジェクトも `null` になるため注意が必要です。

```java
    final FieldInfo fieldInfo = context.reader().getFieldInfos().fieldInfo(fieldName);
    if (fieldInfo == null) {  // All documents in this index segment do not have docValues in this field
      docValues = null;
      return;
    }
    ...
    this.docValues = context.reader().getNumericDocValues(fieldName);
```

`NumericDocValues` オブジェクトを実際に使うのは `NumericDocValuesAccessor#getLongValue` です。
その名の通り、`NUMERIC` docValuesの値は `long` として取得できます。
`DocValues#advance` で注目するドキュメントまでイテレータを進めます。
返り値は実際にイテレータがどのドキュメントまで進んだかを示します。
実際のドキュメントが注目するドキュメントであれば、`DocValues#getLongValue` で値を取得できます。
実際のドキュメントが注目するドキュメントよりも先のドキュメントであれば、注目するドキュメントに対応する値は存在しないことを示します。

```java
    int currentDocId = docValues.docID();
    if (currentDocId < targetDocId) {
      currentDocId = docValues.advance(targetDocId);  // while currentDocId < targetDocId
    }
    if (currentDocId == targetDocId) {  // There is a value
      return docValues.longValue();
    } else {  // No value
      return null;
    }
```

ここで、`DocValues` はインデックスセグメントごとのオブジェクトなので、ドキュメントは**セグメントごとの**通し番号で指定します。
シャードごとの通し番号からセグメントごとの通し番号を逆算するには、そのセグメントの `docBase` を減算します。 
題材のプラグインでは `DemoTopDocsCollector#topDocs` の以下の箇所で逆算しています。

```java
        doc -= currentLeaf.docBase;
        Long docValue = numericDocValuesAccessor.getLongValue(doc);
```


## DocValuesの値のキャスト

前述の通り多値属性ではない数値型フィールドのdocValuesの値は `long` として取得できます。
これを正しい型に変換するにはキャストするだけですが、まず正しい型をスキーマ（`IndexSchema` オブジェクト）を参照して知る必要があります。

題材のプラグインの場合は、リクエストパラメータで与えられた値をドキュメントの値と比較するという動作をします。
この場合、ドキュメントの値を全てキャストするよりも、リクエストパラメータで与えられた値のほうを `long` にキャストした方が効率が良いため、そのような実装にしています。
このキャストは `DemoSearchComponent#prepare` で行っています。
ここでは、`ResponseBuilder` オブジェクトが参照できるので、そこから `SolrQueryRequest` オブジェクトを経由して `IndexSchema` オブジェクトを取得しています (`rb.req.getSchema()`)。

```java
    SchemaField schemaField = rb.req.getSchema().getFieldOrNull(fieldName);
    if (schemaField == null) {
      throw new IllegalArgumentException("Unknown " + DemoParams.DEMO_FIELD_NAME + ": " + fieldName);
    }
    if (!schemaField.hasDocValues()) {
      throw new IllegalArgumentException("Target field must have docValues: " + fieldName);
    }
    if (schemaField.getType().getNumberType() == null) {
      throw new IllegalArgumentException("Target field is not numeric field: " + fieldName);
    }
    long fieldValue;
    switch (schemaField.getType().getNumberType()) {
      case INTEGER:
        fieldValue = Integer.parseInt(fieldValueString);
        break;
      case LONG:
        fieldValue = Long.parseLong(fieldValueString);
        break;
      case FLOAT:
        fieldValue = Float.floatToIntBits(Float.parseFloat(fieldValueString));
        break;
      case DOUBLE:
        fieldValue = Double.doubleToLongBits(Double.parseDouble(fieldValueString));
        break;
      default:
        throw new IllegalArgumentException("Unsupported number type: " + schemaField.getType().getNumberType().name());
    }
```


## シャードからサーチヘッドへ値を送る

これまで各シャードでdocValuesを参照する方法を説明してきましたが、サーチヘッドで参照したいこともあります。
例えば題材のプラグインでは、複数シャード構成の場合はサーチヘッドでdocValuesの値を参照してドキュメントの並べ替えを行います。
この場合、各シャードからサーチヘッドへdocValuesの値を送る必要があります。

> 題材のプラグインの動作をする場合は、リクエストパラメータの値とdocValuesの値が一致するかどうかの情報だけを送れば良いですが、サンプルコードなので分かりやすくdocValuesの値をそのまま送っています。

この動作は、送信側、受信側、どちらも `MergeStrategy` に記述します（`MergeStrategy` の基本は[検索結果上位のドキュメントを並べ替える](2_rank_query.md)参照）。
Solrのレスポンスは要するに連想配列なので、送信側と受信側で共通のキー (題材のプラグインでは `DemoParams.DEMO_RESPONSE_KEY`) を決めておき、そのキーに紐づけてdocValuesの値のリストを送受信します。

送信は `MergeStrategy#handleMergeFields` で行います。
デフォルトの処理は `QueryComponent#doFieldSortValues` に実装されています（[ソースコード](https://github.com/apache/lucene-solr/blob/branch_7_3/solr/core/src/java/org/apache/solr/handler/component/QueryComponent.java#L380-L483)）。

> ちなみに、このデフォルトの処理にはシャードごとの通し番号順のドキュメントの走査が含まれるので、その公式なサンプルとして読むこともできます。

`DemoMergeStrategy#handleMergeFields` では、まずデフォルトの処理を行った後、独自に前述 (`DemoTopDocsCollector#topDocs`) と同様のDocValuesの参照を行い、最後にレスポンスにキーバリューペアを追加します。
ただし、ここではシャードごとの通し番号が特殊なフォーマット（`long` の上位ビット）で扱われているので、その点には注意する必要があります。
この `long` の下位ビットにはドキュメントの順位が入っており、これを利用して、レスポンス上の特定のドキュメントと特定のdocValueの値の対応を取ります。

```java
      // Modification for demo starts here
      NumericDocValuesAccessor numericDocValuesAccessor = null;
      String[] docValuesStrs = new String[nDocs];

      int lastIdx = -1;
      int idx;

      for (long idAndPos : sortedIds) {
        int doc = (int) (idAndPos >>> 32);
        int position = (int) idAndPos;

        if (leaves != null) {
          idx = ReaderUtil.subIndex(doc, leaves);
          currentLeaf = leaves.get(idx);
          if (idx != lastIdx) {
            // we switched segments. Invalidate numeridDocValuesAccessor.
            lastIdx = idx;
            numericDocValuesAccessor = null;
          }
        }

        if (numericDocValuesAccessor == null) {
          numericDocValuesAccessor = new NumericDocValuesAccessor(demoContext.fieldName, currentLeaf);
        }

        doc -= currentLeaf.docBase;  // adjust for what segment this is in
        Long docValue = numericDocValuesAccessor.getLongValue(doc);
        docValuesStrs[position] = String.valueOf(docValue);
      }

      rsp.add(DemoParams.DEMO_RESPONSE_KEY, docValuesStrs);
```

> なお、`MergeStrategy#handleMergeFields` を実装した場合は、`MergeStrategy#handlesMergeFields`（よく見ると別名のメソッドです）も `true` を返すように実装する必要があります。

受信は `MergeStrategy#merge` で行います。
以下、`DemoMergeStrategy#merge` の該当箇所です。

```java
      // Field values in this per-shard response
      List<String> demoFieldValues = (List<String>) (srsp.getSolrResponse().getResponse().get(
          DemoParams.DEMO_RESPONSE_KEY));
```

サーチヘッドには複数シャードからのドキュメントが集まるため、uniqueKeyをキーとするデータ構造に詰め替えています。

```java
        demoFieldValueMap.put(id, Long.valueOf(demoFieldValues.get(i)));
```

以降の処理については、[2. 検索結果上位のドキュメントを並べ替える](2_rank_query.md)の通りです。


## 参考文献

- [DocValues | Apache Solr Reference Guide 7.3](https://solr.apache.org/guide/7_3/docvalues.html)
