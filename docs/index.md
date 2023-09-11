# Solrプラグイン開発チュートリアル

本チュートリアルではSolrのプラグイン開発の基本を説明します。
現在、多くの需要が想定される以下のプラグインを題材にしています。

- 検索結果上位のドキュメントを、そのフィールド値に基づいて並べ替える
- ベクトルをDocValuesに保存し、ベクトル演算によってドキュメントをスコアする

参照するSolrのバージョンは**7.3.1**としていますが、他のバージョンでもプラグイン開発の基本は同様ですので参考にしてください。

一方、Solrの基本的な利用法については説明しませんので、[公式のチュートリアル](https://solr.apache.org/guide/7_3/solr-tutorial.html)などを参考にしてください。

## 目次
- 検索結果上位のドキュメントを、そのフィールド値に基づいて並べ替える
  - [0. 準備](0_preliminary.md)
  - [1. プラグインの動作の起点をつくる](1_search_component.md) (`SearchComponent`)
  - [2. 検索結果上位のドキュメントを並べ替える](2_rank_query.md) (`RankQuery`)
  - [3. ドキュメントのフィールド値にアクセスする](3_doc_values.md) (`DocValues`)
- ベクトルをDocValuesに保存し、ベクトル演算によってドキュメントをスコアする
  - [4. ふたたび準備](4_preliminary2.md)
  - [5. スコアを変更する](5_weight_and_scorer.md) (`Weight`, `Scorer`)
  - [6. 任意のバイナリを保存する](6_field_type.md) (`FieldType`)
  - [7. 文字列処理を変更する](7_tokenizer_and_token_filter.md) (`Tokenizer`, `TokenFilter`)
