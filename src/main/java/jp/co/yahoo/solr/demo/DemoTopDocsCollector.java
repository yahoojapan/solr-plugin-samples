package jp.co.yahoo.solr.demo;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopDocsCollector;
import org.apache.solr.common.SolrException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DemoTopDocsCollector extends TopDocsCollector<ScoreDoc> {
  private final IndexSearcher indexSearcher;
  @SuppressWarnings("rawtypes")
  private final TopDocsCollector wrappedTopDocsCollector;
  private final DemoContext demoContext;

  @SuppressWarnings("rawtypes")
  protected DemoTopDocsCollector(IndexSearcher indexSearcher, TopDocsCollector topDocsCollector,
                                 DemoContext demoContext) {
    super(null);
    this.indexSearcher = indexSearcher;
    this.wrappedTopDocsCollector = topDocsCollector;
    this.demoContext = demoContext;
  }

  @Override
  public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
    return wrappedTopDocsCollector.getLeafCollector(context);
  }

  @Override
  public boolean needsScores() {
    return wrappedTopDocsCollector.needsScores();
  }

  @Override
  public int getTotalHits() {
    return wrappedTopDocsCollector.getTotalHits();
  }

  @Override
  public TopDocs topDocs(int start, int rows) {
    TopDocs wrappedTopDocs = wrappedTopDocsCollector.topDocs();
    ScoreDoc[] wrappedScoreDocs = wrappedTopDocs.scoreDocs;

    if (start < 0 || wrappedScoreDocs.length <= start || rows <= 0) {
      return EMPTY_TOPDOCS;
    }

    ScoreDoc[] sortedScoreDocs = wrappedScoreDocs.clone();
    Arrays.sort(sortedScoreDocs, 0, sortedScoreDocs.length, Comparator.comparingInt(d -> d.doc));

    List<LeafReaderContext> leaves = indexSearcher.getTopReaderContext().leaves();
    LeafReaderContext currentLeaf = null;
    if (leaves.size() == 1) {
      // if there is a single segment, use that subReader and avoid looking up each time
      currentLeaf = leaves.get(0);
      leaves = null;
    }

    NumericDocValuesAccessor numericDocValuesAccessor = null;
    Map<Integer, Long> fieldValues = new HashMap<>();

    int lastIdx = -1;
    int idx;

    for (ScoreDoc scoreDoc : sortedScoreDocs) {
      int doc = scoreDoc.doc;

      if (leaves != null) {
        idx = ReaderUtil.subIndex(doc, leaves);
        currentLeaf = leaves.get(idx);
        if (idx != lastIdx) {
          // We switched index segments. invalidate the accessor.
          lastIdx = idx;
          numericDocValuesAccessor = null;
        }
      }

      try {
        if (numericDocValuesAccessor == null) {
          numericDocValuesAccessor = new NumericDocValuesAccessor(demoContext.fieldName, currentLeaf);
        }

        doc -= currentLeaf.docBase;
        Long docValue = numericDocValuesAccessor.getLongValue(doc);
        if (docValue != null) {
          fieldValues.put(scoreDoc.doc, docValue);
        }
      } catch (IOException e) {
        throw new SolrException(SolrException.ErrorCode.SERVICE_UNAVAILABLE, "failed to read docValues", e);
      }
    }

    List<ScoreDoc> resultScoreDocs = new ArrayList<>();
    List<ScoreDoc> posteriorScoreDocs = new ArrayList<>();
    for (ScoreDoc scoreDoc : wrappedScoreDocs) {
      if (fieldValues.containsKey(scoreDoc.doc) && fieldValues.get(scoreDoc.doc) == demoContext.fieldValue) {
        resultScoreDocs.add(scoreDoc);
      } else {
        posteriorScoreDocs.add(scoreDoc);
      }
    }
    resultScoreDocs.addAll(posteriorScoreDocs);

    return new TopDocs(wrappedTopDocs.totalHits, resultScoreDocs.toArray(new ScoreDoc[wrappedTopDocs.scoreDocs.length]),
                       wrappedTopDocs.getMaxScore());
  }
}
