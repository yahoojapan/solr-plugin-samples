# Solrプラグイン開発チュートリアル

本チュートリアルではSolrのプラグイン開発の基本を説明します。
現在、多くの需要が想定される「検索結果上位のドキュメントを、そのフィールド値に基づいて並べ替える」プラグインを題材にしています。
参照するSolrのバージョンは**7.3.1**としていますが、他のバージョンでもプラグイン開発の基本は同様ですので参考にしてください。

一方、Solrの基本的な利用法については説明しませんので、[公式のチュートリアル](https://solr.apache.org/guide/7_3/solr-tutorial.html)などを参考にしてください。

## 目次
- [0. 準備](0_preliminary.md)
- [1. プラグインの動作の起点をつくる (`SearchComponent`)](1_search_component.md)
- [2. 検索結果上位のドキュメントを並べ替える (`RankQuery`)](2_rank_query.md)
- [3. ドキュメントのフィールド値にアクセスする (`DocValues`)](3_doc_values.md)
<!--
- [4. 検索結果のスコアを差し替える (`Weight`, `Scorer`)](4_weight_and_scorer.md)
- [5. フィールドに任意の値を入れる (`BinaryDocValuesField`)](5_binary_doc_values_field.md)
-->
