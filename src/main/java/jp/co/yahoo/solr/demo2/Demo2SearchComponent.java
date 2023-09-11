package jp.co.yahoo.solr.demo2;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.RankQuery;

import java.io.IOException;

public class Demo2SearchComponent extends SearchComponent {
  @Override
  public void prepare(ResponseBuilder rb) throws IOException {
    final SolrParams params = rb.req.getParams();

    // Second-trip requests should skip this component
    if (params.get("ids") != null) {
      return;
    }

    String fieldName = params.get(Demo2Params.DEMO2_FIELD_NAME, null);
    String queryVectorBase64String = params.get(Demo2Params.DEMO2_QUERY_VECTOR, null);
    if (StringUtils.isBlank(fieldName) && (StringUtils.isBlank(queryVectorBase64String))) {
      return;
    } else if ((StringUtils.isBlank(fieldName) || (StringUtils.isBlank(queryVectorBase64String)))) {
      throw new IllegalArgumentException(
          "Only " + Demo2Params.DEMO2_FIELD_NAME + " or " + Demo2Params.DEMO2_QUERY_VECTOR + " is specified.");
    }

    SchemaField schemaField = rb.req.getSchema().getFieldOrNull(fieldName);
    if (schemaField == null) {
      throw new IllegalArgumentException("Unknown " + Demo2Params.DEMO2_FIELD_NAME + ": " + fieldName);
    }
    if (!schemaField.hasDocValues()) {
      throw new IllegalArgumentException("Target field must have docValues: " + fieldName);
    }
    if (!(schemaField.getType() instanceof FloatArrayField)) {
      throw new IllegalArgumentException("Target field is not float array field: " + fieldName);
    }
    Analyzer analyzer = schemaField.getType().getQueryAnalyzer();
    float[] queryVector;
    try (TokenStream tokenStream = analyzer.tokenStream(fieldName, queryVectorBase64String)) {
      tokenStream.reset();
      if (!tokenStream.incrementToken()) {
        throw new IOException("In demo2, just one token is expected");
      }
      queryVector = tokenStream.getAttribute(FloatArrayAttribute.class).getFloatArray();
      if (tokenStream.incrementToken()) {
        throw new IOException("In demo2, just one token is expected");
      }
      tokenStream.end();
    }

    if (!rb.isDistributed()) {  // This is a request for one shard. This node generates a per-shard response.
      Demo2Context demo2Context = new Demo2Context(fieldName, queryVector);
      RankQuery demoRankQuery = new Demo2RankQuery(demo2Context);
      rb.setRankQuery(demoRankQuery);  // Set RankQuery for scoring at generation of the per-shard response
    }
  }

  @Override
  public void process(ResponseBuilder rb) {}

  @Override
  public String getDescription() {
    return "Another demo search component.";
  }
}
