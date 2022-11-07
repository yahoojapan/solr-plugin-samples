package jp.co.yahoo.solr.demo;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocsCollector;
import org.apache.lucene.search.TopFieldCollector;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.Weight;
import org.apache.solr.handler.component.MergeStrategy;
import org.apache.solr.search.CursorMark;
import org.apache.solr.search.QueryCommand;
import org.apache.solr.search.QueryUtils;
import org.apache.solr.search.RankQuery;
import org.apache.solr.search.SolrIndexSearcher;

import java.io.IOException;
import java.util.Objects;

public class DemoRankQuery extends RankQuery {
  private Query wrappedQuery;  // The original query wrapped in this query
  private final DemoMergeStrategy demoMergeStrategy;
  public DemoContext demoContext;

  /**
   * Constructor for demo rank query.
   *
   * @param demoMergeStrategy related merge strategy
   * @param demoContext related context object
   */
  public DemoRankQuery(DemoMergeStrategy demoMergeStrategy, DemoContext demoContext) {
    this.demoMergeStrategy = demoMergeStrategy;
    this.demoContext = demoContext;
  }

  @SuppressWarnings("rawtypes")
  @Override
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

  @Override
  public MergeStrategy getMergeStrategy() {
    return demoMergeStrategy;
  }

  @Override
  public RankQuery wrap(Query query) {
    if (query != null) {
      // NOTE: workaround for negative query
      this.wrappedQuery = QueryUtils.makeQueryable(query);
    }
    return this;
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
    return wrappedQuery.createWeight(searcher, needsScores, boost);
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    Query q = wrappedQuery.rewrite(reader);
    if (q == wrappedQuery) {
      return this;
    } else {
      return new DemoRankQuery(demoMergeStrategy, demoContext).wrap(q);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;

    DemoRankQuery that = (DemoRankQuery) obj;
    return Objects.equals(wrappedQuery, that.wrappedQuery) && Objects.equals(demoMergeStrategy, that.demoMergeStrategy)
           && Objects.equals(demoContext, that.demoContext);
  }

  @Override
  public int hashCode() {
    return Objects.hash(wrappedQuery, demoMergeStrategy, demoContext);
  }
}
