# 6. 任意のバイナリを保存する (`FieldType`)

これまでプラグインの動作の起点は `SearchComponent` だと説明してきました。
しかし本プラグインでは、正確にはもう一つの起点があります。
`SearchComponent` は検索の起点であって、更新（ドキュメントの追加や部分更新）の起点は別にあります。
それは `FieldType` です。

## `FieldType`

Solrの基本を簡単に説明します。
Solrのドキュメントは一般に複数のフィールド型 (`fieldType`) の複数のフィールド (`field`) から成ります。
どのようなフィールド型のどのようなフィールドがありうるかは、`schema.xml` で宣言します。

本プラグインが実装する `FloatArrayField` 型を宣言する場合は以下のようになります（[本リポジトリ内、テスト用のschema_demo2.xml](../src/test-files/solr/collection1/conf/schema_demo2.xml)より抜粋）。

```xml
  <fieldType name="float_array" class="jp.co.yahoo.solr.demo2.FloatArrayField">
    <analyzer>
      <tokenizer class="jp.co.yahoo.solr.demo2.Base64ToFloatArrayTokenizerFactory" length="3"/>
      <filter class="jp.co.yahoo.solr.demo2.VectorNormalizerTokenFilterFactory"/>
    </analyzer>
  </fieldType>
```

`FieldType` は、このような `schema.xml` におけるフィールド型に対応するオブジェクトのクラスです。

### `SchemaField` およびLuceneの `Field` との違い

`FieldType` の周辺には似たような名前のクラスもあり、分かりづらくなっていますので違いを説明します。

#### `SchemaField`

`FieldType` は `schema.xml` における `fieldType` 要素に対応するのに対して、`SchemaField` は `field` 要素に対応します。

前述の型のフィールドを宣言する場合は以下の要素になります。

```xml
  <field name="vector" type="float_array" docValues="true"/>
```

#### Luceneの `Field`

`SchemaField` と `field` は、名前としては完全には対応していませんでした。
なぜ `SchemaField` には `Schema` とプレフィックスが付いているかというと、ただの `Field` というクラスも存在するためだと思われます。

ただの `Field` はLuceneで定義されているクラスで、`FieldType` や `SchemaField` とは異なり、具体的な値が入っています。
本プラグインで言えば、`float[]` が入っているということです。

以下、`SchemaField` と明確に区別したい場合は**Luceneの** `Field` と呼びます。

> これらのクラスは、より具体的なクラスに継承されており、そのことが更に全体像を分かりづらくしているため注意が必要です。
> 例えば `BinaryField` は（`SchemaField` でも `Lucene` の `Field` でもなく）`FieldType` を継承しています。
> つまり、フィールド型の一種です。

## `FloatArrayField` の実装

では、本プラグインで利用する `FloatArrayField` フィールド型の実装を説明します。
全体的な方針としては、既存の `BinaryField` フィールド型を継承して実装します。
これはSolrに任意のバイナリを保存するためのフィールド型です。

> 本来、`FloatArrayFieldType` および `BinaryFieldType` と命名したほうが分かりやすそうですが、Solrの命名に合わせています。

`BinaryField` には任意のバイナリを保存できるので、そのまま使えば良さそうですが、このクラスは `schema.xml` におけるオプション `stored="true"` をサポートするのに対して、本プラグインのためには `docValues="true"` のほうが向いていると思われるため、この点をサポートするクラスとして `FloatArrayField` を実装します。

### StoredとDocValues

`stored="true"` と `docValues="true"` の使い分けについて説明します。
これらはどちらも、フィールドの値をSolrに保存する場合のフォーマットです。

#### Stored

フィールドの値をドキュメントごとにまとめて保存するフォーマットです。
ふつう更新リクエストはドキュメント単位で送りますし、検索結果もドキュメント単位で返るため、直感的なフォーマットだと言えます。
このフォーマットは、すでに検索結果に含めるドキュメントが決まっていて、それらのドキュメントの複数のフィールドの値を取得して検索結果を生成する場合には高速に動作します。

#### DocValues

フィールドの値をフィールドごとにまとめて保存するフォーマットです。
前述の `vector` フィールドを宣言した場合は、インデックスの中に各ドキュメントの `float[]` の値だけをまとめて保存した領域ができるということです。
これにより、多くのドキュメントの特定のフィールドの値だけを参照したい場合、例えば特定のフィールドの値のファセッティングや、その値によるソートやグルーピングにおいて高速に動作します。

本プラグインにおいても、`FloatArrayField` の値だけを参照すればコサイン類似度は計算できるので、こちらが本プラグインに向いているフォーマットだと言えます。

> なお、検索結果の生成にdocValuesを利用することもできます。
> これは `useDocValuesAsStored="true"` と宣言しますが、デフォルト設定にもなっていますので、実際にはdocValuesでも高速に動作することが多いと思われます。

> 逆に、storedであっても、`FieldCache` に入れればdocValuesとほぼ同等に扱えます。
> しかし、このキャッシュのウォームアップのコストが余分にかかるため、本チュートリアルでは考慮しません。
> また、このキャッシュはJavaのヒープ内に確保され、ヒープを圧迫することにも注意する必要があります。

### 具体的な実装

では `FloatArrayField` の具体的な実装を見ていきます。

```java
  @Override
  protected void checkSupportsDocValues() {}  // Indicates docValues support by not throwing an Exception

  @Override
  public IndexableField createField(SchemaField field, Object val) {
    if (val == null) return null;
    if (!field.hasDocValues()) {
      logger.trace("Ignoring float array field without docValues: " + field);
      return null;
    }
    byte[] buf;
    int offset = 0, len;
    if (val instanceof byte[]) {
      buf = (byte[]) val;
      len = buf.length;
    } else if (val instanceof ByteBuffer && ((ByteBuffer) val).hasArray()) {
      ByteBuffer byteBuf = (ByteBuffer) val;
      buf = byteBuf.array();
      offset = byteBuf.position();
      len = byteBuf.limit() - byteBuf.position();
    } else {
      String strVal = val.toString();
      Analyzer analyzer = field.getType().getIndexAnalyzer();
      float[] documentVector;
      try (TokenStream tokenStream = analyzer.tokenStream(field.getName(), strVal)) {
        tokenStream.reset();
        if (!tokenStream.incrementToken()) {
          throw new IOException("In demo2, just one token is expected");
        }
        documentVector = tokenStream.getAttribute(FloatArrayAttribute.class).getFloatArray();
        if (tokenStream.incrementToken()) {
          throw new IOException("In demo2, just one token is expected");
        }
        tokenStream.end();
      } catch (IOException e) {
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                                "Error while creating field '" + field + "' from value '" + strVal + "'", e);
      }
      buf = new byte[Float.BYTES * documentVector.length];
      ByteBuffer.wrap(buf).asFloatBuffer().put(documentVector);
      len = buf.length;
    }
    return new BinaryDocValuesField(field.getName(), new BytesRef(buf, offset, len));
  }
```

まずdocValuesをサポートしていることを示すために、`checkSupportsDocValues` をオーバーライドして例外を上げないようにしています。
デフォルトでは以下の通り、例外を上げる（docValuesをサポートして**いない**ことを示す）実装になっています。

```java
  protected void checkSupportsDocValues() {
    throw new SolrException(ErrorCode.SERVER_ERROR, "Field type " + this + " does not support doc values");
  }
```

### `FloatArrayField#createField`

`BinaryField` からのその他ほとんどの差分は `createField` にあります。
このメソッドは `SchemaField field` と具体的なフィールドの値 `Object val` を引数に取り、Luceneの `Field`を返します。
あとはLucene/Solrの機能によって、Luceneの `Field` がインデックスに保存されることになります。

まず値が `null` のとき、early returnしています。
ここは `BinaryField` と同じです。

```java
    if (val == null) return null;
```

次に `docValues="false"` であれば、することがないので、これもearly returnしています（例外を上げても良いかもしれません）。
なお、これらオプションは `fieldType` でも `field` でも宣言できますが、後者が優先されます。
そこで、ここでは `field` の値を参照しています。
ここも `docValues` か `stored` かの違いはありますが、`BinaryField` と同じです。

```java
    if (!field.hasDocValues()) {
      logger.trace("Ignoring float array field without docValues: " + field);
      return null;
    }
```

次の処理は `val` の型により分岐します。

#### 通常の更新

このメソッドが呼ばれる最も分かりやすいケースは、直接フィールドの値が含まれる更新リクエストを受け取るケースです。

このとき、`val` の型は `String`（更新リクエストのフォーマットがJSONであれば文字列、XMLであればテキストノードの内容、……）で、直感的です。
実装上は `else` 節に入ります。

```java
    } else {
      String strVal = val.toString();
```

`String` を `float[]` にパースしベクトルとして正規化する処理は `Analyzer` に任せています。

```java
      Analyzer analyzer = field.getType().getIndexAnalyzer();
      float[] documentVector;
      try (TokenStream tokenStream = analyzer.tokenStream(field.getName(), strVal)) {
        ...
        documentVector = tokenStream.getAttribute(FloatArrayAttribute.class).getFloatArray();
```

これにより、これらの処理を `FieldType` の実装とは分離できるメリットがあります。
詳細は[文字列処理を変更する](7_tokenizer_and_token_filter.md)で説明します。

その後、`float[]` を `byte[]` にキャストし、docValuesに含められる型（後述）にします。

```java
      buf = new byte[Float.BYTES * documentVector.length];
      ByteBuffer.wrap(buf).asFloatBuffer().put(documentVector);
```

#### 部分更新

他のケースとして、他のフィールドの値の部分更新（Solrの用語でatomic update）リクエストを受け取るケースがあります。

Solrにおけるドキュメントの更新は、基本的には（Solrの用語でin-place updateでなければ）既存のドキュメントを論理削除し、新しいドキュメントを追加することで行います。
このとき、部分更新リクエストに含まれないフィールドの値は、古いインデックスを参照して、新しいインデックスに追加する必要があります。
このとき、`val` の型が `byte[]` や `ByteBuffer` になることがあります。
単に値を参照して追加するだけなので、特別な処理はしていません。
ここも `BinaryField` と同じです。

#### Luceneの `Field` を返す

最後に型による分岐は合流し、具体的な `byte[]` を含むLuceneのフィールドを返します。

```java
    return new BinaryDocValuesField(field.getName(), new BytesRef(buf, offset, len));
```

実際には `BytesRef` にラップしています。
これは `byte[]` の一部を参照するためのクラスです。
例えばLucene/Solrは複数のドキュメントのdocValuesをまとめて長大な `byte[]` に読み込み、特定のドキュメントの値は `BytesRef` で参照することがあります。

## `DocValuesType`

[ドキュメントのフィールド値にアクセスする](3_doc_values.md)では `NumericDocValues` を扱い、前述のコードでは `BinaryDocValuesField` を扱いました。
これらNUMERICやBINARYは `DocValuesType` です。
フィールド型 `FieldType` とは別にdocValuesの型 `DocValuesType` があるということです。
この点も分かりづらいので、以下に簡単にまとめます。

### NUMERIC

数値型です。
ありうる値のテーブルを保存し、各ドキュメントの値は、ありうる値へのポインタで保存します。
このため、ありうる値のバリエーションが少ない場合に圧縮効率が良くなります。

### BINARY

バイナリ型です。
基本的には各ドキュメントの値として直接バイナリを持ちます。より正確には、プレフィックス圧縮が可能であれば行います。

### SORTED

バイナリ型です。
ありうる値のテーブルを保存し、各ドキュメントの値は、ありうる値へのポインタで保存します。
NUMERICと同様、ありうる値のバリエーションが少ない場合に圧縮効率が良くなります。

> 一般に、ありうるベクトルのバリエーションは多いため、本プラグインではSORTEDを利用しません。

### SORTED_NUMERIC

NUMERICのバリエーションで、ドキュメントごとのポインタが複数である点が異なります。
これら複数のポインタは圧縮のためにソートされるので、値の順序は保存されないことに注意が必要です。

> 一般に、ベクトルでは要素の順序に情報量があるため、本プラグインではSORTED_NUMERICを利用しません。

### SORTED_SET

SORTEDのバリエーションで、ドキュメントごとのポインタが複数である点が異なります。
これも値の順序は保存されません。

## `FieldType` と `DocValuesType` の対応

フィールドがドキュメントあたり複数の値を許容しない（`multiValued="false"`, デフォルト）か、する (`multiValued="true"`) かによって異なります。

|`FieldType`|`multiValued="false"` のときの `DocValuesType`|`multiValued="true"` のときの `DocValuesType`|
|----|----|----|
|`*PointField`（主に数値型）|NUMERIC|SORTED_NUMERIC|
|`Trie*Field`（主に数値型、旧形式）|NUMRTIC|SORTED_SET|
|（本プラグイン独自の）`FloatArrayField`|BINARY|未サポート|
|`StrField`（文字列型）|SORTED|SORTED_SET|

## 参考文献

- [DocValues | Apache Solr Reference Guide 7.3](https://solr.apache.org/guide/7_3/docvalues.html)
- 以下、本チュートリアルの他の箇所で参照するSolr 7.3.1よりも新しいLucene 8.6.3に関する情報ですが、基本は同様です。
  - [Lucene80DocValuesFormat (Lucene 8.6.3 API)](https://lucene.apache.org/core/8_6_3/core/org/apache/lucene/codecs/lucene80/Lucene80DocValuesFormat.html)
  - [Lucene (Elasticsearch, Solr) のインデックスには結局どんな情報が保存されているのか](https://qiita.com/tomanabe/items/d64ded175b468cd43142)
