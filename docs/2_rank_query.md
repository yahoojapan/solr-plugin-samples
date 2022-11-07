# 2. 検索結果上位のドキュメントを並べ替える (`RankQuery`)

題材のプラグインは検索結果上位のドキュメントを並べ替えるプラグインでした。
実はSolrには、この並べ替えのための抽象クラス `RankQuery` が用意されており、題材のプラグインもこれを利用して実装されています。
本章では、`RankQuery` の基本と、具体的な実装 `DemoRankQuery` を説明します。

まず、題材のプラグインで並べ替えの対象となるドキュメントについて詳しい仕様を説明します。
次に、`RankQuery` の基本を説明します。
以後、具体的な実装 `DemoRankQuery` を説明します。
まず、通常の場合に並べ替えを行うための、`DemoRankQuery#getTopDocsCollector` の実装について説明します。
次に、複数シャード構成の場合に並べ替えを行うための、`DemoRankQuery#getMergeStrategy` の実装について説明します。
最後に、`RankQuery` に関わる細かい補足を行います。


## 並べ替えの対象となるドキュメント

本チュートリアルではここまで、題材のプラグインで並べ替えの対象となるドキュメントについて詳しい仕様を説明してきませんでした。
本節では、それを説明します。


### 通常の場合

例えばスコアの降順 (`sort=score desc`) で、0はじまりで20件目 (`start=20`) から10件 (`rows=10`) のドキュメントをリクエストする場合を考えます。
このとき、Solrの内部ではサイズ30の優先度つきキューが用意されます。

> 優先度つきキューとは、決まったサイズを持ち、それを超えた数の要素をエンキュー（入力）すると、優先度が低い要素から順にデキュー（出力）されるデータ構造です。

サイズ30の優先度つきキューに、スコアを優先度として全てのドキュメントをエンキューし、デキューされたものを捨てれば、スコアの降順で上位30件のドキュメントが残ります。
ここから全てのドキュメントをスコア順にデキューすると、スコアの降順で上位30件のドキュメントのランキングができます。
あとは、このうち0はじまりで20件目以降を切り出せば、元のリクエストに対するレスポンスになっているわけです。

通常の場合、題材のプラグインによる並べ替えの対象は、この優先度つきキューに残っているドキュメントとします。

> 実際のプラグインでは、この仕様には問題があります。
> 例えば0件目から10件のドキュメントをリクエストすると、10件のドキュメントを並べ替えることになります。
> 次に、10件目から10件のドキュメントをリクエストすると、20件のドキュメントを並び替えて、下位10件を切り出すことになります。
> このとき、並べ替えの対象のドキュメントの集合が異なるため、前者の検索結果の「続き」が後者の検索結果にはならず、両者に重複するドキュメントやどちらにも表示されないドキュメントがありえます。
> 
> 実際のプラグインでは、例えば常に固定件数のドキュメントを並べ替えるように工夫する必要があります。


### 複数シャード構成の場合

複数シャード構成の場合、検索結果はサーチヘッドが生成するため、検索結果ドキュメントの順序もサーチヘッドが決めます（サーチヘッドについては[プラグインの動作の起点をつくる](./1_search_component.md)参照）。
このため、各シャードは優先度つきキューに残ったドキュメントの情報をサーチヘッドに返します。
つまりサーチヘッドには（シャード数）かける（優先度つきキューのサイズ）ぶんのドキュメントが集まります。
複数シャード構成の場合、題材のプラグインによる並べ替えの対象は、これらのドキュメントとします。

> サーチヘッドに余分なドキュメントが集まっているように思えますが、検索結果の全てのドキュメントが特定のシャードに固まっている最悪ケースを考えると、サーチヘッドにこれだけの数のドキュメントを集める必要があります。


## `RankQuery` の基本

`RankQuery` 抽象クラスは `solr-core` パッケージで提供されています。

同クラスは3つの抽象メソッドを持ちます：

- `RankQuery#getTopDocsCollector`：優先度つきキューの準備に介入します。
- `RankQuery#getMergeStrategy`：各シャードからのレスポンスの生成と、それらのサーチヘッドにおけるマージに介入します。
- `RankQuery#wrap`：元のクエリをラップします。

つまり、`RankQuery` は、元のクエリをラップし、ドキュメントの順序を元の順序とは変更する（並べ替えを行う）ものです。


## `DemoRankQuery` における実装の例

以下、具体的な実装 `DemoRankQuery`（[ソースコード](../src/main/java/jp/co/yahoo/solr/demo/DemoRankQuery.java)）を参照しつつ、`RankQuery` の各メソッドについて説明します。


### `DemoRankQuery#getTopDocsCollector` の実装

`RankQuery#getTopDocsCollector` は `TopDocsCollector`（以下コレクタ）を返すメソッドです。
コレクタは、本質的には優先度つきキューを内包するオブジェクトです。
独自のコレクタを実装し、このメソッドで返すことで、優先度つきキューから全てのドキュメントをデキューするメソッド `TopDocsCollector#topDocs` をオーバーライドできるので、これが呼ばれたタイミングで任意の並べ替えを行えるようになります。

というわけで、`DemoRankQuery#getTopDocsCollector` は独自のコレクタである `DemoTopDocsCollector` を返します。
ただし、独自のコレクタは並べ替えだけを行えば良いので、他の処理はデフォルトのコレクタに移譲する実装としています。
そこで、まずデフォルトのコレクタを生成し、独自のコレクタに内包しておきます。

デフォルトのコレクタの生成のプロセスは `SolrIndexSearcher#buildTopDocsCollector` に実装されています（[ソースコード](https://github.com/apache/lucene-solr/blob/branch_7_3/solr/core/src/java/org/apache/solr/search/SolrIndexSearcher.java#L1511-L1525)）。
ただし、このメソッドはprivateなので、`DemoRankQuery#getTopDocsCollector` では単に同等のコードを書いています。

```java
  public TopDocsCollector getTopDocsCollector(int len, QueryCommand cmd, IndexSearcher searcher) throws IOException {
    TopDocsCollector wrappedTopDocsCollector;

    // cf. SolrIndexSearcher#buildTopDocsCollector
    if (null == cmd.getSort()) {
      assert null == cmd.getCursorMark() : "have cursor but no sort";
      wrappedTopDocsCollector = TopScoreDocCollector.create(len);
    } else {
      // we have a sort
      final boolean needScores = (cmd.getFlags() & SolrIndexSearcher.GET_SCORES) != 0;
      final Sort weightedSort = (cmd.getSort() == null) ? null : cmd.getSort().rewrite(searcher);
      final CursorMark cursor = cmd.getCursorMark();

      // :TODO: make fillFields its own QueryCommand flag? ...
      // ... see comments in populateNextCursorMarkFromTopDocs for cache issues (SOLR-5595)
      final boolean fillFields = (null != cursor);
      final FieldDoc searchAfter = (null != cursor ? cursor.getSearchAfterFieldDoc() : null);
      assert weightedSort != null;
      wrappedTopDocsCollector = TopFieldCollector.create(weightedSort, len, searchAfter, fillFields, needScores,
                                                         needScores, true);
    }

    return new DemoTopDocsCollector(searcher, wrappedTopDocsCollector, demoContext);
  }
```

`DemoTopDocsCollector#topDocs` では、まずデフォルトのコレクタから全てのドキュメントをソート順にデキューします。
続いて、独自の処理として、各ドキュメントのフィールド値にアクセスし、フィールド値に応じてドキュメントの並べ替えを行います。
このアクセスについては[ドキュメントのフィールド値にアクセスする](./3_doc_values.md)で説明します。
この並べ替えについては、実際のプラグインでは必要な機能で置き換えることになり、本質的ではない処理なので説明を割愛します。


### `DemoRankQuery#getMergeStrategy` の実装

`RankQuery#getMergeStrategy` は `MergeStrategy` を返すメソッドです。
Solrの `MergeStrategy` は、各シャードからのレスポンスの生成と、それらのサーチヘッドにおけるマージに関わるオブジェクトです。
例によって、`DemoRankQuery#getMergeStrategy` は独自の `MergeStrategy` である `DemoMergeStrategy` を返します。
これにより、題材のプラグインは以下の2つの動作を行います。

- 各シャードからサーチヘッドへのレスポンスに、各ドキュメントのフィールド値を含める。詳細は[ドキュメントのフィールド値にアクセスする](./3_doc_values.md)で説明します。
- 各シャードから返ってきたドキュメントを、そのフィールド値に基づいて並べ替え、検索結果ドキュメントを確定する。このあと説明します。

各シャードから返ってきたドキュメントの並べ替えは `MergeStrategy#merge` で行います。
デフォルトの処理は `QueryComponent#mergeIds` に実装されています（[ソースコード](https://github.com/apache/lucene-solr/blob/branch_7_3/solr/core/src/java/org/apache/solr/handler/component/QueryComponent.java#L783-L975)）。
`DemoMergeStrategy#merge` では、デフォルトの処理に加えて、各シャードから各ドキュメントのフィールド値を受け取り、フィールド値に応じてドキュメントの並べ替えを行います。
ただし、`QueryComponent#mergeIds` はprotectedなので、デフォルトの処理は単に同等のコードを書くことで実現しています。
また、このメソッドが依存する他のメソッドについても、アクセス修飾子による制約で参照できないもの (`unmarshalSortValues`, `populateNextCursorMarkFromMergedShards`) は単に同等のコードを書いています。

> なお、`MergeStrategy#merge` を実装した場合は、`MergeStrategy#mergesIds` も `true` を返すように実装する必要があります。

`DemoMergeStrategy#merge` ではデフォルトの処理で（当然）各シャードからのレスポンスのイテレートや各ドキュメントのイテレートを行うため、効率の観点から、デフォルトの処理の各所に独自の処理を埋め込んでいます。
そのため、独自の処理を抜粋して示すことは難しいのですが、概要としては以下の独自の処理を行っていますので、これらの識別子を手がかりにコードを追っていただけると読みやすいと思います。

- ローカル変数の `HashMap<Object, Long> demoFieldValueMap` に、ドキュメントのuniqueKeyをキーとして、そのドキュメントのフィールド値を詰める
- ドキュメントの順位 (`ShardDoc` の `positionInResponse` メンバの値)を、ドキュメントのフィールド値を参照して決める

また、以下のように、`QueryComponent#mergeIds` と `DemoMergeStrategy#merge` のdiffを取ることも理解の助けになると思います。

```java
+    // id to field value (in long bits) mapping, to rerank documents later
+    HashMap<Object, Long> demoFieldValueMap = new HashMap<>();
...
+        demoFieldValueMap.put(id, Long.valueOf(demoFieldValues.get(i)));
```

```java
+    List<ShardDoc> reversedResultShardDocs = new ArrayList<>();
+    List<ShardDoc> reversedPriorShardDocs = new ArrayList<>();
...
+      if (demoFieldValueMap.get(shardDoc.id) == demoContext.fieldValue) {
+        reversedPriorShardDocs.add(shardDoc);
+      } else {
+        reversedResultShardDocs.add(shardDoc);
+      }
+    }
+    reversedResultShardDocs.addAll(reversedPriorShardDocs);
+    for (int i = 0; i < reversedResultShardDocs.size(); i++) {
+      reversedResultShardDocs.get(i).positionInResponse = reversedResultShardDocs.size() - i - 1;
```


### その他の補足

- `RankQuery` は本来 `rq` リクエストパラメータに指定するとSolr (`QueryComponent#prepare`) が処理してくれます（[ソースコード](https://github.com/apache/lucene-solr/blob/branch_7_3/solr/core/src/java/org/apache/solr/handler/component/QueryComponent.java#L168-L185)）が、この場合 `RankQuery` を返す `QParser` を独自に書く必要があり、複雑になるので、題材のプラグインでは `QueryComponent#prepare` と同等の処理を`DemoSearchComponent#prepare` で行っています。
- `RankQuery` には他にも `Query` としてのメソッド (`createWeight`, `rewrite`) がありますが、基本的にラップした元のクエリに処理を移譲すれば良いです。また、`Query` はレスポンスのキャッシュのキーとしても使われるため、同一性に関するメソッド (`equals`, `hashCode`) も正しく実装する必要があります。それぞれ、詳しくは `DemoRankQuery` のコードを参照してください。
- `MergeStrategy#getCost` は複数の `MergeStrategy` 間の並べ替えに関わりますが、題材のプラグインでは複数の `MergeStrategy` が共存することを想定していないので、`DemoMergeStrategy#getCost` は単に0を返しています。


## 参考文献

- [Query Re-Ranking | Apache Solr Reference Guide 7.3](https://lucene.apache.org/solr/guide/7_3/query-re-ranking.html)
- [SolrとランキングとRankQuery - Qiita](https://qiita.com/yuyano/items/80a24b714f5a3efed384)
- [SolrのRankQueryによるドキュメントの並べ替え - Qiita](https://qiita.com/tomanabe/items/a927e61232b1d3ad06cd)
