package jp.co.yahoo.solr.demo2;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import java.io.IOException;
import java.util.Set;

public class Demo2Weight extends Weight {
  private final Weight wrappedWeight;  // The original weight wrapped in this weight
  private final Demo2Context demo2Context;

  public Demo2Weight(Demo2RankQuery demo2RankQuery, Weight wrappedWeight) {
    super(demo2RankQuery);
    this.wrappedWeight = wrappedWeight;
    this.demo2Context = demo2RankQuery.getDemo2Context();
  }

  public Demo2Context getDemo2Context() {
    return demo2Context;
  }

  @Override
  public void extractTerms(Set<Term> terms) {
    wrappedWeight.extractTerms(terms);
  }

  @Override
  public Explanation explain(LeafReaderContext context, int doc) throws IOException {
    return wrappedWeight.explain(context, doc);
  }

  @Override
  public Scorer scorer(LeafReaderContext leafReaderContext) throws IOException {
    return new Demo2Scorer(this, leafReaderContext, wrappedWeight.scorer(leafReaderContext));
  }

  @Override
  public boolean isCacheable(LeafReaderContext leafReaderContext) {
    return wrappedWeight.isCacheable(leafReaderContext);
  }
}
