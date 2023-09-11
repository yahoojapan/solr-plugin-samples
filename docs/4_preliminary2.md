# 4. ふたたび準備

ここからは、以下の動作をする新たなプラグインを題材に、引き続きSolrのプラグイン開発の基本を説明したいと思います。

- ベクトルをDocValuesに保存し、ベクトル演算によってドキュメントをスコアする

## 題材のプラグインをインストールする

これまでの題材のプラグイン（区別の必要がある場合は `demo` と呼びます） と、ここからの題材のプラグイン（同様に `demo2` と呼びます）は、同じ.jarファイルにパッケージされています。
ですので、パッケージの配置までは、[準備](0_preliminary.md)を参照してください。

### プラグインごとの設定

`demo` と同じく `demo2` も `SearchComponent` を起点に動作しますので、この点では設定もほぼ同じです。
具体的な `SearchComponent` の実装のみが異なります（[本リポジトリ内、テスト用のsolrconfig_demo2.xml](../src/test-files/solr/collection1/conf/solrconfig_demo2.xml)より抜粋）。

```xml
  <searchComponent name="demo2Component" class="jp.co.yahoo.solr.demo2.Demo2SearchComponent"/>

  <requestHandler name="/select" class="solr.SearchHandler">
    <arr name="last-components">
      <str>demo2Component</str>
    </arr>
  </requestHandler>
```

`demo` と `demo2` の相違点として、後者はSolrに保存するドキュメントも拡張します。
Solrのドキュメントは一般に複数のフィールド型 (`fieldType`) の複数のフィールド (`field`) から成ります。
`demo2` は新たなフィールド型を実装することで、ドキュメントを拡張します。

どのようなフィールド型のどのようなフィールドがありうるかは、`schema.xml` で宣言します。
`demo2` が実装する `FloatArrayField` 型を宣言する場合は以下のようになります（[本リポジトリ内、テスト用のschema_demo2.xml](../src/test-files/solr/collection1/conf/schema_demo2.xml)より抜粋）。

```xml
  <fieldType name="float_array" class="jp.co.yahoo.solr.demo2.FloatArrayField">
    <analyzer>
      <tokenizer class="jp.co.yahoo.solr.demo2.Base64ToFloatArrayTokenizerFactory" length="3"/>
      <filter class="jp.co.yahoo.solr.demo2.VectorNormalizerTokenFilterFactory"/>
    </analyzer>
  </fieldType>
```

さらに、この型のフィールドを宣言する場合は以下のようになります。

```xml
  <field name="vector" type="float_array" docValues="true"/>
```

これらの配置と設定をした後、SolrコアをRELOADするとプラグインをインストールしたことになります。

## 題材のプラグインの外部仕様と動作確認

`demo2` の外部仕様を確認します。

### ドキュメントの仕様

`FloatArrayField` 型のフィールドの値は、その名の通りJavaの `float[]` をBase64エンコードして与えます。
これがドキュメントベクトルとなります。

リクエスト全体としてはXMLでフォーマットする場合の例は以下の通りです。

```xml
<add>
  <doc>
    <field name="id">0</field>
    <field name="vector">AAAAAEBAAABAgAAA</field>
  </doc>
</add>
```

ちなみに、Base64エンコードを行うコードの例は以下の通りです（[本リポジトリ内、`Demo2SearchComponentTest.java`](../src/test/java/jp/co/yahoo/solr/demo2/Demo2SearchComponentTest.java)より抜粋）

```java
  private static String base64StringOf(float... vector) {
    byte[] bytes = new byte[Float.BYTES * vector.length];
    ByteBuffer.wrap(bytes).asFloatBuffer().put(vector);
    return Base64.getEncoder().encodeToString(bytes);
  }
```

### リクエストの仕様

通常のSolrの検索リクエストに加えて、以下の2つのリクエストパラメータを追加することにします。

| パラメータ名                          | 説明                                                                        |
|---------------------------------|---------------------------------------------------------------------------|
| `demo2.field.name`              | ドキュメントベクトルのフィールド名。`docValues="true"` の `FloatArrayField` 型のフィールドである必要がある。 |
| `demo2.query.vector` | クエリベクトル。こちらもJavaの `float[]` をBase64エンコードして与える。                            |

追加後の検索リクエストの例：`q=*:*&fl=id,score&demo2.field.name=vector&demo2.query.vector=AAAAAEBAAABAgAAA`

### レスポンスの仕様

前述のリクエストパラメータをどちらも追加しない場合は、通常のレスポンスが返ります。

前述のリクエストパラメータを共に追加した場合は、検索結果のドキュメントのスコアを、ドキュメントベクトルとクエリベクトルとのコサイン類似度とします。
Solrは例えばデフォルトではスコアの降順にドキュメントをソートするので、このときコサイン類似度の降順にドキュメントをソートすることになります。

フィールド値が存在しなかったり、その他の異常値の場合は、コサイン類似度の代わりに `float` の取りうる最小値を返すこととします。

### 動作確認（動作例）

`id` 順に `[0, 3, 4]`, `[0, -5, 0]`, `[0, 0, 0]`,（存在しない）の4つのドキュメントベクトルと、`[0, 3, 4]` のクエリベクトルに対するレスポンスは以下のようになります。

```json
{
  "responseHeader":{
    "status":0,
    "QTime":17},
  "response":{"numFound":4,"start":0,"maxScore":1.0,"docs":[
      {
        "id":"0",
        "score":1.0},
      {
        "id":"2",
        "score":0.0},
      {
        "id":"1",
        "score":-0.6},
      {
        "id":"3",
        "score":-3.4028235E38}]
  }}
```

## ここまでの復習

`demo` と `demo2` の外部仕様は大幅に異なりますが、実装としては再利用できる部分も大きいです（これがプラグイン開発をするメリットの一つです）。
この節では、ここまでの復習をかねて、再利用できる部分について簡単に説明します。

- `demo` のコードは本リポジトリ内 [`demo` Javaパッケージ](../src/main/java/jp/co/yahoo/solr/demo)にあります。
- `demo2` のコードは本リポジトリ内 [`demo2` Javaパッケージ](../src/main/java/jp/co/yahoo/solr/demo2)にあります。

### プラグインの動作の起点をつくる

プラグインの動作の起点は、`demo` については `DemoSearchComponent` でした。
詳細は[プラグインの動作の起点をつくる](1_search_component.md)を参照してください。

`demo2` でも `SearchComponent` (具体的には `Demo2SearchComponent`) であることは、すでに説明しました。
しかし、その内容は少し異なっています。

まず、`demo` では数値型フィールド値をパースしていたところ、独自の `float[]` 型フィールド値をパースする必要があります。

```java
    Analyzer analyzer = schemaField.getType().getQueryAnalyzer();
    float[] queryVector;
    try (TokenStream tokenStream = analyzer.tokenStream(fieldName, queryVectorBase64String)) {
      tokenStream.reset();
      if (!tokenStream.incrementToken()) {
        throw new IOException("In demo2, just one token is expected");
      }
      queryVector = tokenStream.getAttribute(FloatArrayAttribute.class).getFloatArray();
      if (tokenStream.incrementToken()) {
        throw new IOException("In demo2, just one token is expected");
      }
      tokenStream.end();
    }
```

詳細は[文字列処理を変更する](7_tokenizer_and_token_filter.md)で説明します。

次に、`demo` ではドキュメントをフィールド値によって並べ替えていたところ、`demo2` ではスコアを変更して並べ替えます。
シャードごとのレスポンスをマージする際、スコアを考慮するのはSolrの通常の動作ですので、`demo2` では独自の `MergeStrategy` を実装する必要が**ありません**。

ただし、スコアを変更するためにも `Query` は必要ですので、`RankQuery` は実装しセットします。

```java
    if (!rb.isDistributed()) {  // This is a request for one shard. This node generates a per-shard response.
      Demo2Context demo2Context = new Demo2Context(fieldName, queryVector);
      RankQuery demoRankQuery = new Demo2RankQuery(demo2Context);
      rb.setRankQuery(demoRankQuery);  // Set RankQuery for scoring at generation of the per-shard response
    }
```

なお、実際にスコアを計算するのは各シャードですので、そこでだけセットする必要があります。

### `Query` オブジェクトを変更する

`RankQuery` について詳細は[検索結果上位のドキュメントを並べ替える](2_rank_query.md)で説明しました。
その基本的な動作には、`MergeStrategy` を返すほか、コレクタ（優先度つきキューを内包するオブジェクト）を返すというものがありました。
そして、`demo` ではフィールド値によってドキュメントを並べ替えるため、独自のコレクタが必要でした。
しかしスコアによってドキュメントを並べ替えるのは、やはりSolrの通常の動作ですので、`demo2` では独自のコレクタを実装する必要も**ありません**。

こうなると `demo2` では `RankQuery` の必要性が薄いように思われますが、説明の簡略化のために `demo` と共通化しています。
またこうしておくと、まず独自のスコアで並べ替え、その上位のドキュメントをフィールド値で並べ替えるプラグイン（`demo` と `demo2` の連携）に容易に拡張できます。

`demo` にはなく `demo2` にある `Query` の責務もあります。
具体的には、`Query#createWeight` において独自の `Demo2Weight` を返しています。

```java
  @Override
  public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
    return new Demo2Weight(this, wrappedQuery.createWeight(searcher, needsScores, boost));
  }
```

さらに `Demo2Weight#scorer` において独自の `Demo2Scorer` を返すことで、スコアの変更につながります。

```java
  @Override
  public Scorer scorer(LeafReaderContext leafReaderContext) throws IOException {
    return new Demo2Scorer(this, leafReaderContext, wrappedWeight.scorer(leafReaderContext));
  }
```

詳細は[スコアを変更する](5_weight_and_scorer.md)で説明します。

### ドキュメントのフィールド値にアクセスする

`DocValues` の扱いについては、[ドキュメントのフィールド値にアクセスする](3_doc_values.md)で説明しました。

`demo` においては数値型フィールド値にアクセスしていました。
これに対して、`demo2` においては、独自の `float[]` 型フィールド値にアクセスする必要があります。
`DocValues` オブジェクトのメソッドで言うと、`NumericDocValues#longValue` を呼んでいたところ、`BinaryDocValues#binaryValue` を呼ぶことになります。

```java
      BytesRef bytesRef = binaryDocValues.binaryValue();
```

通し番号順にアクセスする、型を変換するなど、使い方のコツは共通しています。

ところで、Solrはインデックスをインデックスセグメントに分割します。
このため、`demo` においては、セグメントごとの `DocValuesAccessor` オブジェクトを独自に書いてコードを整理していました。
一方 `demo2` においては、もともとセグメントごとの `Scorer` オブジェクトを扱います。
よって `demo2` においては、`DocValuesAccessor` オブジェクトも不要になります。
