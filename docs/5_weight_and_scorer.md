# 5. スコアを変更する (`Weight`, `Scorer`)

本プラグインではスコアを変更する必要がありました。
スコアを変更するには名前の通り `Scorer` というクラスのオブジェクトを変更することになります。

## オブジェクトの使い分け

まず `Scorer` を含めたオブジェクトの使い分けをまとめます。

### `IndexSearcher`

ある時点でのインデックスの状態に対応するオブジェクトです。
これはLuceneのクラスで、Solrでは拡張した `SolrIndexSearcher` を使います。
具体的には検索対象のインデックスセグメントのリスト (`leafContexts`) や、各ドキュメントの論理削除フラグ (`liveDocs`) などを保持します。

`Searcher` の名前の通り、さまざまな検索処理の起点でもあります。

ちなみに、（Solrの用語で）soft commitを行うと、新しい `IndexSearcher` が作られます。

### `Query`

ある検索リクエストに対応するオブジェクトです。

インデックスの状態とは独立に扱うことができます。
例えばSolrの `queryResultCache` は、主に `Query` をキー、ある時点での検索結果を値とする連想配列になっています。
このキャッシュを新しいインデックスの状態（新しい `IndexSearcher`）について作成するとき（Solr の用語で `autowarm` と呼びます）は、古い `queryResultCache` のキーの `Query` を取り出し、新しい `IndexSearcher` 上の検索を行って検索結果を取得することになります。

### `Weight`

ある検索リクエスト (`Query`) を、ある時点でのインデックス (`IndexSearcher`) について実行するためのオブジェクトです。

`Weight` という名前の意味が分かりにくいですが、これは `Query` そのものやクエリキーワードの重み（たとえばinverse document frequency, IDF）を計算し保持するというところから来ていると思われます。

### `Scorer`

ある検索リクエストを、あるインデックス**セグメント**（インデックスの一部）について実行するためのオブジェクトです。

直接ドキュメントを意識するという意味で末端のオブジェクトにあたり、`Scorer` という名前の通り、ドキュメントのスコア計算も行います。

## `Weight`, `Scorer` の差し替え

各クラスについては以上の通りですが、これらのオブジェクトの生成チェーンについて説明します。

### `Weight` の差し替え

説明の通り、`Weight` は `Query` と `IndexSearcher` から生成されます。
具体的には、`Query#createWeight(IndexSearcher, boolean, float)` で対応する `Weight` を返す必要があります。
本プラグインの `Demo2RankQuery` では、以下の通り `Demo2Weight` を返しています。

```java
  @Override
  public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
    return new Demo2Weight(this, wrappedQuery.createWeight(searcher, needsScores, boost));
  }
```

`Demo2RankQuery` は元の `Query` を内包していました。
ここでのポイントは、内包する `Query` についても対応する `Weight` を生成し、`Demo2Weight` に内包しておくところです。
これにより、`Weight` には多くのメソッドがありますが、ほとんどの処理を内包する `Weight` に移譲することができます。

> `needsScores` はこの検索リクエストの実行にスコアが必要か否かのフラグです。
> しかし、本プラグインはスコアを変更するプラグインです。
> そのコードに処理が移っているが、スコアが不要、という状況が考えにくいため、フラグを単に無視しています。

> `boost` はクエリの重みです。
> こちらも単に無視しています。
> 例えばこの重みをコサイン類似度にかける実装も考えられます。

### `Scorer` の差し替え

`Scorer` は `Weight` と `LeafReaderContext`（あるインデックスセグメントに関するコンテキストオブジェクト）から生成されます。
具体的には、`Weight#scorer(LeafReaderContext)` で対応する `Scorer` を返す必要があります。
本プラグインの `Demo2Weight` では、以下の通り `Demo2Scorer` を返しています。

```java
  @Override
  public Scorer scorer(LeafReaderContext leafReaderContext) throws IOException {
    return new Demo2Scorer(this, leafReaderContext, wrappedWeight.scorer(leafReaderContext));
  }
```

`Demo2Query` が元の `Query` を、`Demo2Weight` が元の `Weight` を、それぞれ内包していたのと同様に、ここでも元の `Scorer` を生成し内包しておきます。

## `Scorer` における処理

このようにして本プラグイン独自の `Demo2Scorer` に処理が移りました。
ここでは `Scorer` について押さえるべき点を説明します。

### `Scorer` はイテレータを内包する

まず押さえるべきは、`Scorer` は `Query` にマッチするドキュメントのイテレータを内包するという点です。
これにより `Scorer` は、現在どのドキュメントに注目しているかという情報を保持しており、`Scorer#score()`（引数なしであることに注意）が呼ばれると、注目しているドキュメントのスコアを返します。

以下に具体的なメソッドを紹介します。

#### `Demo2Scorer#docID()`

注目しているドキュメントの通し番号を返します。
`Scorer` はインデックスセグメントごとのオブジェクトなので、ここではドキュメントをインデックスセグメント内での通し番号で扱います。

```java
  @Override
  public int docID() {
    return wrappedScorer.docID();
  }
```

#### `Demo2Scorer#iterator()`

内包するイテレータ（具体的な実装としては `DocIdSetIterator`）を返します。

```java
  @Override
  public DocIdSetIterator iterator() {
    return wrappedScorer.iterator();
  }
```

`Scorer` は、このイテレータを外部から進められつつ（注目しているドキュメントを変更されつつ）、`Scorer#score` を呼ばれたときに注目しているドキュメントのスコアを返す、という挙動をします。

> これは `Scorer` の内部表現の暴露と思われますが、パフォーマンスのためか、このような実装になっています。

### `Scorer#score` の実装

いよいよ `Scorer` のコアな処理に入ります。
`Demo2Scorer#score` の実装は以下のようになっています。

```java
  @Override
  public float score() throws IOException {
    if (null == binaryDocValues) {
      return -Float.MAX_VALUE;
    }
    if (cachedDocID == docID()) {
      return cachedScore;
    }

    if (binaryDocValues.docID() < docID()) {
      binaryDocValues.advance(docID());
    }
    cachedDocID = docID();
    if (binaryDocValues.docID() == docID()) {
      BytesRef bytesRef = binaryDocValues.binaryValue();
      FloatBuffer floatBuffer = ByteBuffer.wrap(bytesRef.bytes, bytesRef.offset, bytesRef.length).asFloatBuffer();
      if (floatBuffer.limit() == queryVector.length) {
        cachedScore = 0.0f;
        for (int i = 0; i < floatBuffer.limit(); ++i) {
          cachedScore += floatBuffer.get(i) * queryVector[i];
        }
      } else {
        cachedScore = -Float.MAX_VALUE;
      }
    } else {
      cachedScore = -Float.MAX_VALUE;
    }
    return cachedScore;
  }
```

`DocValues` の扱いについては、[ドキュメントのフィールド値にアクセスする](3_doc_values.md)で説明しました。

そのとき、使い方のポイントとして、注目しているフィールドの値が注目しているインデックスセグメント中に全くない場合、`DocValues` オブジェクトそのものが存在しない（ここでは `binaryDocValues` メンバが `null` になる）というものがありました。
この場合、常にエラー値である `-Float.MAX_VALUE` を返すものとしてearly returnしています。

他のポイントとして、シャードごとの通し番号順にアクセスするというものもありました。
`Scorer` が内包するイテレータも、この制約を満たしつつ順々にドキュメントに注目していくため、単に `DocValues#docID()` と `Scorer#docID()` を同期すれば良いです。
またこのとき、この `docID` について既にスコアしている場合は、キャッシュしておいたスコアを返す実装としています。
これにより、コサイン類似度の不要な再計算を防止できます。

同期は `binaryDocValues.advance(docID())` で試みます。
同期は、`DocValues#docID() < Scorer#docID()` の時のみ試みれば良いです。
そうでない時は、すでに同期できているか、すでに値が存在しないと分かっているためです。

うまく同期できれば、`DocValues` が注目しているフィールドの値が、`Scorer` が注目しているドキュメントについて存在するということです。
このとき、フィールドの値を取得し、キャストし、リクエストパラメータの値とともにベクトルと見なしてドット積を計算しています。
なお、ベクトルと見なすと長さが異なる場合は、エラー値を返す実装としています。

> ここでドット積を計算するのに対して、外部仕様はコサイン類似度を計算するというものでした。
> しかし、`Scorer#score` はドキュメント数ぶん呼び出されるため、なるべく軽い処理にしておきたいところです。
> コサイン類似度は2ベクトルを正規化したあとのドット積に相当しますが、ドキュメントベクトルは検索リクエストが来る前に正規化できるため、そのような実装にし、ここではドット積を計算しています。
> ちなみにクエリベクトルは `Demo2SearchComponent#prepare` で正規化しています。
> 詳細は[文字列処理を変更する](7_tokenizer_and_token_filter.md)で説明します。

うまく同期できなければ、`DocValues` が注目しているフィールドの値が、`Scorer` が注目しているドキュメントについて存在しないということです。
このときもエラー値を返す実装としています。
