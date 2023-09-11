# solr_demo_plugin

## 概要

Solrプラグインのデモです。
ぜひ以下のチュートリアルをご覧ください。

## [Solrプラグイン開発チュートリアル](./docs/index.md)

## LICENSE

Some code in this repository were derived and modified from [Apache Solr 7.3.1](https://github.com/apache/lucene-solr/tree/branch_7_3) under the [Apache License 2.0](https://github.com/apache/lucene-solr/blob/branch_7_3/solr/LICENSE.txt). Here is the list of correspondence between them:

| This Repository                                                                | Apache Solr 7.3.1                                                                       |
|--------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| jp.co.yahoo.solr.demo.DemoMergeStrategy#unmarshalSortValues                    | org.apache.solr.handler.component.QueryComponent#unmarshalSortValues                    |
| jp.co.yahoo.solr.demo.DemoMergeStrategy#populateNextCursorMarkFromMergedShards | org.apache.solr.handler.component.QueryComponent#populateNextCursorMarkFromMergedShards |
| jp.co.yahoo.solr.demo.DemoMergeStrategy#merge                                  | org.apache.solr.handler.component.QueryComponent#mergeIds                               |
| jp.co.yahoo.solr.demo.DemoMergeStrategy$FakeScorer                             | org.apache.solr.handler.component.QueryComponent$FakeScorer                             |
| jp.co.yahoo.solr.demo.DemoMergeStrategy#handleMergeFields                      | org.apache.solr.handler.component.QueryComponent#doFieldSortValues                      |
| jp.co.yahoo.solr.demo.DemoRankQuery#getTopDocsCollector<br>jp.co.yahoo.solr.demo.Demo2RankQuery#getTopDocsCollector | org.apache.solr.search.SolrIndexSearcher#buildTopDocsCollector |

This repository itself is published under the [MIT License](./LICENSE).
