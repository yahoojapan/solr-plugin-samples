package jp.co.yahoo.solr.demo;

import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.LeafFieldComparator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.InPlaceMergeSorter;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.component.MergeStrategy;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.ShardDoc;
import org.apache.solr.handler.component.ShardFieldSortedHitQueue;
import org.apache.solr.handler.component.ShardRequest;
import org.apache.solr.handler.component.ShardResponse;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.CursorMark;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.SortSpec;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DemoMergeStrategy implements MergeStrategy {
  private final DemoContext demoContext;

  public DemoMergeStrategy(DemoContext demoContext) {
    this.demoContext = demoContext;
  }

  /*
   * @see org.apache.solr.handler.component.QueryComponent#unmarshalSortValues(SortSpec, NamedList, IndexSchema)
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private NamedList unmarshalSortValues(SortSpec sortSpec, NamedList sortFieldValues) {
    NamedList unmarshalledSortValsPerField = new NamedList();

    if (0 == sortFieldValues.size()) return unmarshalledSortValsPerField;

    List<SchemaField> schemaFields = sortSpec.getSchemaFields();
    SortField[] sortFields = sortSpec.getSort().getSort();

    int marshalledFieldNum = 0;
    for (int sortFieldNum = 0; sortFieldNum < sortFields.length; sortFieldNum++) {
      final SortField sortField = sortFields[sortFieldNum];
      final SortField.Type type = sortField.getType();

      // :TODO: would be simpler to always serialize every position of SortField[]
      if (type == SortField.Type.SCORE || type == SortField.Type.DOC) continue;

      final String sortFieldName = sortField.getField();
      final String valueFieldName = sortFieldValues.getName(marshalledFieldNum);
      assert sortFieldName.equals(
          valueFieldName) : "sortFieldValues name key does not match expected SortField.getField";

      List sortVals = (List) sortFieldValues.getVal(marshalledFieldNum);

      final SchemaField schemaField = schemaFields.get(sortFieldNum);
      if (null == schemaField) {
        unmarshalledSortValsPerField.add(sortField.getField(), sortVals);
      } else {
        FieldType fieldType = schemaField.getType();
        List unmarshalledSortVals = new ArrayList();
        for (Object sortVal : sortVals) {
          unmarshalledSortVals.add(fieldType.unmarshalSortValue(sortVal));
        }
        unmarshalledSortValsPerField.add(sortField.getField(), unmarshalledSortVals);
      }
      marshalledFieldNum++;
    }
    return unmarshalledSortValsPerField;
  }

  /*
   * @see org.apache.solr.handler.component.QueryComponent#populateNextCursorMarkFromMergedShards(ResponseBuilder)
   */
  @SuppressWarnings({"ConstantConditions", "unchecked"})
  protected void populateNextCursorMarkFromMergedShards(ResponseBuilder rb) {

    final CursorMark lastCursorMark = rb.getCursorMark();
    if (null == lastCursorMark) {
      // Not a cursor based request
      return; // NOOP
    }

    assert null != rb.resultIds : "resultIds was not set in ResponseBuilder";

    Collection<ShardDoc> docsOnThisPage = rb.resultIds.values();

    if (0 == docsOnThisPage.size()) {
      // nothing more matching query, re-use existing totem so user can "resume"
      // search later if it makes sense for this sort.
      rb.setNextCursorMark(lastCursorMark);
      return;
    }

    ShardDoc lastDoc = null;
    // ShardDoc and rb.resultIds are weird structures to work with...
    for (ShardDoc eachDoc : docsOnThisPage) {
      if (null == lastDoc || lastDoc.positionInResponse  < eachDoc.positionInResponse) {
        lastDoc = eachDoc;
      }
    }
    SortField[] sortFields = lastCursorMark.getSortSpec().getSort().getSort();
    List<Object> nextCursorMarkValues = new ArrayList<>(sortFields.length);
    for (SortField sf : sortFields) {
      if (sf.getType().equals(SortField.Type.SCORE)) {
        nextCursorMarkValues.add(lastDoc.score);
      } else {
        assert null != sf.getField() : "SortField has null field";
        List<Object> fieldVals = (List<Object>) lastDoc.sortFieldValues.get(sf.getField());
        nextCursorMarkValues.add(fieldVals.get(lastDoc.orderInShard));
      }
    }
    CursorMark nextCursorMark = lastCursorMark.createNext(nextCursorMarkValues);
    assert null != nextCursorMark : "null nextCursorMark";
    rb.setNextCursorMark(nextCursorMark);
  }

  @Override
  public boolean mergesIds() {
    return true;
  }

  /**
   * Perform the default merging then deduping based on field values.
   *
   * @see org.apache.solr.handler.component.QueryComponent#mergeIds(ResponseBuilder, ShardRequest)
   */
  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void merge(ResponseBuilder rb, ShardRequest sreq) {
    SortSpec ss = rb.getSortSpec();
    Sort sort = ss.getSort();

    SortField[] sortFields;
    if (sort != null) {
      sortFields = sort.getSort();
    } else {
      sortFields = new SortField[] {SortField.FIELD_SCORE};
    }

    IndexSchema schema = rb.req.getSchema();
    SchemaField uniqueKeyField = schema.getUniqueKeyField();

    // id to shard mapping, to eliminate any accidental dups
    HashMap<Object, String> uniqueDoc = new HashMap<>();
    // id to field value (in long bits) mapping, to rerank documents later
    HashMap<Object, Long> demoFieldValueMap = new HashMap<>();

    // Merge the docs via a priority queue so we don't have to sort *all* of the
    // documents... we only need to order the top (rows+start)
    final ShardFieldSortedHitQueue queue = new ShardFieldSortedHitQueue(sortFields, ss.getOffset() + ss.getCount(),
                                                                        rb.req.getSearcher());

    NamedList<Object> shardInfo = null;
    if (rb.req.getParams().getBool(ShardParams.SHARDS_INFO, false)) {
      shardInfo = new SimpleOrderedMap<>();
      rb.rsp.getValues().add(ShardParams.SHARDS_INFO, shardInfo);
    }

    long numFound = 0;
    Float maxScore = null;
    boolean partialResults = false;
    Boolean segmentTerminatedEarly = null;
    for (ShardResponse srsp : sreq.responses) {
      SolrDocumentList docs = null;
      NamedList<?> responseHeader = null;

      if (shardInfo != null) {
        SimpleOrderedMap<Object> nl = new SimpleOrderedMap<>();

        if (srsp.getException() != null) {
          Throwable t = srsp.getException();
          if (t instanceof SolrServerException) {
            t = t.getCause();
          }
          nl.add("error", t.toString());
          StringWriter trace = new StringWriter();
          t.printStackTrace(new PrintWriter(trace));
          nl.add("trace", trace.toString());
          if (srsp.getShardAddress() != null) {
            nl.add("shardAddress", srsp.getShardAddress());
          }
        } else {
          responseHeader = (NamedList<?>) srsp.getSolrResponse().getResponse().get("responseHeader");
          final Object rhste = (responseHeader == null ? null : responseHeader.get(
              SolrQueryResponse.RESPONSE_HEADER_SEGMENT_TERMINATED_EARLY_KEY));
          if (rhste != null) {
            nl.add(SolrQueryResponse.RESPONSE_HEADER_SEGMENT_TERMINATED_EARLY_KEY, rhste);
          }
          docs = (SolrDocumentList) srsp.getSolrResponse().getResponse().get("response");
          nl.add("numFound", docs.getNumFound());
          nl.add("maxScore", docs.getMaxScore());
          nl.add("shardAddress", srsp.getShardAddress());
        }
        if (srsp.getSolrResponse() != null) {
          nl.add("time", srsp.getSolrResponse().getElapsedTime());
        }

        shardInfo.add(srsp.getShard(), nl);
      }
      // now that we've added the shard info, let's only proceed if we have no error.
      if (srsp.getException() != null) {
        partialResults = true;
        continue;
      }

      if (docs == null) { // could have been initialized in the shards info block above
        docs = (SolrDocumentList) srsp.getSolrResponse().getResponse().get("response");
      }

      if (responseHeader == null) { // could have been initialized in the shards info block above
        responseHeader = (NamedList<?>) srsp.getSolrResponse().getResponse().get("responseHeader");
      }

      if (responseHeader != null) {
        if (Boolean.TRUE.equals(responseHeader.get(SolrQueryResponse.RESPONSE_HEADER_PARTIAL_RESULTS_KEY))) {
          partialResults = true;
        }
        if (!Boolean.TRUE.equals(segmentTerminatedEarly)) {
          final Object ste = responseHeader.get(SolrQueryResponse.RESPONSE_HEADER_SEGMENT_TERMINATED_EARLY_KEY);
          if (Boolean.TRUE.equals(ste)) {
            segmentTerminatedEarly = Boolean.TRUE;
          } else if (Boolean.FALSE.equals(ste)) {
            segmentTerminatedEarly = Boolean.FALSE;
          }
        }
      }

      // calculate global maxScore and numDocsFound
      if (docs.getMaxScore() != null) {
        maxScore = maxScore == null ? docs.getMaxScore() : Math.max(maxScore, docs.getMaxScore());
      }
      numFound += docs.getNumFound();

      NamedList sortFieldValues = (NamedList) (srsp.getSolrResponse().getResponse().get("sort_values"));
      NamedList unmarshalledSortFieldValues = unmarshalSortValues(ss, sortFieldValues);
      // Field values in this per-shard response
      List<String> demoFieldValues = (List<String>) (srsp.getSolrResponse().getResponse().get(
          DemoParams.DEMO_RESPONSE_KEY));

      // go through every doc in this response, construct a ShardDoc, and
      // put it in the priority queue so it can be ordered.
      for (int i = 0; i < docs.size(); i++) {
        SolrDocument doc = docs.get(i);
        Object id = doc.getFieldValue(uniqueKeyField.getName());

        String prevShard = uniqueDoc.put(id, srsp.getShard());
        if (prevShard != null) {
          // duplicate detected
          numFound--;

          // For now, just always use the first encountered since we can't currently
          // remove the previous one added to the priority queue.  If we switched
          // to the Java5 PriorityQueue, this would be easier.
          continue;
          // make which duplicate is used deterministic based on shard
          // if (prevShard.compareTo(srsp.shard) >= 0) {
          //  TODO: remove previous from priority queue
          //  continue;
          // }
        }

        ShardDoc shardDoc = new ShardDoc();
        shardDoc.id = id;
        shardDoc.shard = srsp.getShard();
        shardDoc.orderInShard = i;
        Object scoreObj = doc.getFieldValue("score");
        if (scoreObj != null) {
          if (scoreObj instanceof String) {
            shardDoc.score = Float.parseFloat((String) scoreObj);
          } else {
            shardDoc.score = (Float) scoreObj;
          }
        }

        shardDoc.sortFieldValues = unmarshalledSortFieldValues;

        queue.insertWithOverflow(shardDoc);

        demoFieldValueMap.put(id, Long.valueOf(demoFieldValues.get(i)));
      } // end for-each-doc-in-response
    } // end for-each-response

    // The queue now has 0 -> queuesize docs, where queuesize <= start + rows
    // So we want to pop the last documents off the queue to get
    // the docs offset -> queuesize
    int resultSize = queue.size() - ss.getOffset();
    resultSize = Math.max(0, resultSize);  // there may not be any docs in range

    Map<Object, ShardDoc> resultIds = new HashMap<>();
    List<ShardDoc> reversedResultShardDocs = new ArrayList<>();
    List<ShardDoc> reversedPriorShardDocs = new ArrayList<>();
    for (int i = resultSize - 1; i >= 0; i--) {
      ShardDoc shardDoc = queue.pop();
      // Need the toString() for correlation with other lists that must
      // be strings (like keys in highlighting, explain, etc)
      resultIds.put(shardDoc.id.toString(), shardDoc);
      if (demoFieldValueMap.get(shardDoc.id) == demoContext.fieldValue) {
        reversedPriorShardDocs.add(shardDoc);
      } else {
        reversedResultShardDocs.add(shardDoc);
      }
    }
    reversedResultShardDocs.addAll(reversedPriorShardDocs);
    for (int i = 0; i < reversedResultShardDocs.size(); i++) {
      reversedResultShardDocs.get(i).positionInResponse = reversedResultShardDocs.size() - i - 1;
    }

    // Add hits for distributed requests
    // https://issues.apache.org/jira/browse/SOLR-3518
    rb.rsp.addToLog("hits", numFound);

    SolrDocumentList responseDocs = new SolrDocumentList();
    if (maxScore != null) responseDocs.setMaxScore(maxScore);
    responseDocs.setNumFound(numFound);
    responseDocs.setStart(ss.getOffset());
    // size appropriately
    for (int i = 0; i < resultSize; i++) responseDocs.add(null);

    // save these results in a private area so we can access them
    // again when retrieving stored fields.
    // TODO: use ResponseBuilder (w/ comments) or the request context?
    rb.resultIds = resultIds;
    rb.setResponseDocs(responseDocs);

    populateNextCursorMarkFromMergedShards(rb);

    if (partialResults) {
      if (rb.rsp.getResponseHeader().get(SolrQueryResponse.RESPONSE_HEADER_PARTIAL_RESULTS_KEY) == null) {
        rb.rsp.getResponseHeader().add(SolrQueryResponse.RESPONSE_HEADER_PARTIAL_RESULTS_KEY, Boolean.TRUE);
      }
    }
    if (segmentTerminatedEarly != null) {
      final Object existingSegmentTerminatedEarly = rb.rsp.getResponseHeader().get(
          SolrQueryResponse.RESPONSE_HEADER_SEGMENT_TERMINATED_EARLY_KEY);
      if (existingSegmentTerminatedEarly == null) {
        rb.rsp.getResponseHeader().add(SolrQueryResponse.RESPONSE_HEADER_SEGMENT_TERMINATED_EARLY_KEY,
                                       segmentTerminatedEarly);
      } else if (!Boolean.TRUE.equals(existingSegmentTerminatedEarly) && Boolean.TRUE.equals(segmentTerminatedEarly)) {
        rb.rsp.getResponseHeader().remove(SolrQueryResponse.RESPONSE_HEADER_SEGMENT_TERMINATED_EARLY_KEY);
        rb.rsp.getResponseHeader().add(SolrQueryResponse.RESPONSE_HEADER_SEGMENT_TERMINATED_EARLY_KEY, true);
      }
    }
  }

  /*
   * @see org.apache.solr.handler.component.QueryComponent#FakeScorer
   */
  protected static class FakeScorer extends Scorer {
    final int docid;
    final float score;

    FakeScorer(int docid, float score) {
      super(null);
      this.docid = docid;
      this.score = score;
    }

    @Override
    public int docID() {
      return docid;
    }

    @Override
    public float score() {
      return score;
    }

    @Override
    public DocIdSetIterator iterator() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Weight getWeight() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Collection<ChildScorer> getChildren() {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public boolean handlesMergeFields() {
    return true;
  }

  /**
   * Respond field values for deduping in addition to default field values.
   *
   * @see org.apache.solr.handler.component.QueryComponent#doFieldSortValues(ResponseBuilder, SolrIndexSearcher)
   */
  @Override
  public void handleMergeFields(ResponseBuilder rb, SolrIndexSearcher searcher) throws IOException {
    SolrQueryRequest req = rb.req;
    SolrQueryResponse rsp = rb.rsp;
    // The query cache doesn't currently store sort field values, and SolrIndexSearcher doesn't
    // currently have an option to return sort field values.  Because of this, we
    // take the documents given and re-derive the sort values.
    //
    // TODO: See SOLR-5595
    boolean fsv = req.getParams().getBool(ResponseBuilder.FIELD_SORT_VALUES, false);
    if (fsv) {
      NamedList<Object[]> sortVals = new NamedList<>(); // order is important for the sort fields
      IndexReaderContext topReaderContext = searcher.getTopReaderContext();
      List<LeafReaderContext> leaves = topReaderContext.leaves();
      LeafReaderContext currentLeaf = null;
      if (leaves.size() == 1) {
        // if there is a single segment, use that subReader and avoid looking up each time
        currentLeaf = leaves.get(0);
        leaves = null;
      }

      DocList docList = rb.getResults().docList;

      // sort ids from lowest to highest so we can access them in order
      int nDocs = docList == null ? 0 : docList.size();
      final long[] sortedIds = new long[nDocs];
      final float[] scores = new float[nDocs]; // doc scores, parallel to sortedIds
      DocList docs = rb.getResults().docList;
      DocIterator it = docs == null ? null : docs.iterator();
      for (int i = 0; i < nDocs; i++) {
        assert it != null;
        sortedIds[i] = (((long) it.nextDoc()) << 32) | i;
        scores[i] = docs.hasScores() ? it.score() : Float.NaN;
      }

      // sort ids and scores together
      new InPlaceMergeSorter() {
        @Override
        protected void swap(int i, int j) {
          long tmpId = sortedIds[i];
          float tmpScore = scores[i];
          sortedIds[i] = sortedIds[j];
          scores[i] = scores[j];
          sortedIds[j] = tmpId;
          scores[j] = tmpScore;
        }

        @Override
        protected int compare(int i, int j) {
          return Long.compare(sortedIds[i], sortedIds[j]);
        }
      }.sort(0, sortedIds.length);

      SortSpec sortSpec = rb.getSortSpec();
      Sort sort = searcher.weightSort(sortSpec.getSort());
      SortField[] sortFields = sort == null ? new SortField[] {SortField.FIELD_SCORE} : sort.getSort();
      List<SchemaField> schemaFields = sortSpec.getSchemaFields();

      for (int fld = 0; fld < schemaFields.size(); fld++) {
        SchemaField schemaField = schemaFields.get(fld);
        FieldType ft = null == schemaField ? null : schemaField.getType();
        SortField sortField = sortFields[fld];

        SortField.Type type = sortField.getType();
        // :TODO: would be simpler to always serialize every position of SortField[]
        if (type == SortField.Type.SCORE || type == SortField.Type.DOC) continue;

        FieldComparator<?> comparator = sortField.getComparator(1, 0);
        LeafFieldComparator leafComparator = null;
        Object[] vals = new Object[nDocs];

        int lastIdx = -1;
        int idx = 0;

        for (int i = 0; i < sortedIds.length; ++i) {
          long idAndPos = sortedIds[i];
          float score = scores[i];
          int doc = (int) (idAndPos >>> 32);
          int position = (int) idAndPos;

          if (leaves != null) {
            idx = ReaderUtil.subIndex(doc, leaves);
            currentLeaf = leaves.get(idx);
            if (idx != lastIdx) {
              // we switched segments.  invalidate leafComparator.
              lastIdx = idx;
              leafComparator = null;
            }
          }

          if (leafComparator == null) {
            leafComparator = comparator.getLeafComparator(currentLeaf);
          }

          doc -= currentLeaf.docBase;  // adjust for what segment this is in
          leafComparator.setScorer(new FakeScorer(doc, score));
          leafComparator.copy(0, doc);
          Object val = comparator.value(0);
          if (null != ft) val = ft.marshalSortValue(val);
          vals[position] = val;
        }

        sortVals.add(sortField.getField(), vals);
      }

      rsp.add("sort_values", sortVals);

      // Modification for demo starts here
      NumericDocValuesAccessor numericDocValuesAccessor = null;
      String[] docValuesStrs = new String[nDocs];

      int lastIdx = -1;
      int idx;

      for (long idAndPos : sortedIds) {
        int doc = (int) (idAndPos >>> 32);
        int position = (int) idAndPos;

        if (leaves != null) {
          idx = ReaderUtil.subIndex(doc, leaves);
          currentLeaf = leaves.get(idx);
          if (idx != lastIdx) {
            // we switched segments. Invalidate numeridDocValuesAccessor.
            lastIdx = idx;
            numericDocValuesAccessor = null;
          }
        }

        if (numericDocValuesAccessor == null) {
          numericDocValuesAccessor = new NumericDocValuesAccessor(demoContext.fieldName, currentLeaf);
        }

        doc -= currentLeaf.docBase;  // adjust for what segment this is in
        Long docValue = numericDocValuesAccessor.getLongValue(doc);
        docValuesStrs[position] = String.valueOf(docValue);
      }

      rsp.add(DemoParams.DEMO_RESPONSE_KEY, docValuesStrs);
    }
  }

  @Override
  public int getCost() {
    return 0;
  }
}
