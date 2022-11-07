package jp.co.yahoo.solr.demo;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.RankQuery;

public class DemoSearchComponent extends SearchComponent {
  @Override
  public void prepare(ResponseBuilder rb) {
    final SolrParams params = rb.req.getParams();

    // Second-trip requests should skip this component
    if (params.get("ids") != null) {
      return;
    }

    String fieldName = params.get(DemoParams.DEMO_FIELD_NAME, null);
    String fieldValueString = params.get(DemoParams.DEMO_FIELD_VALUE, null);
    if (StringUtils.isBlank(fieldName) && (StringUtils.isBlank(fieldValueString))) {
      return;
    } else if ((StringUtils.isBlank(fieldName) || (StringUtils.isBlank(fieldValueString)))) {
      throw new IllegalArgumentException(
          "Only " + DemoParams.DEMO_FIELD_NAME + " or " + DemoParams.DEMO_FIELD_VALUE + " is specified.");
    }

    SchemaField schemaField = rb.req.getSchema().getFieldOrNull(fieldName);
    if (schemaField == null) {
      throw new IllegalArgumentException("Unknown " + DemoParams.DEMO_FIELD_NAME + ": " + fieldName);
    }
    if (!schemaField.hasDocValues()) {
      throw new IllegalArgumentException("Target field must have docValues: " + fieldName);
    }
    if (schemaField.getType().getNumberType() == null) {
      throw new IllegalArgumentException("Target field is not numeric field: " + fieldName);
    }
    long fieldValue;
    switch (schemaField.getType().getNumberType()) {
      case INTEGER:
        fieldValue = Integer.parseInt(fieldValueString);
        break;
      case LONG:
        fieldValue = Long.parseLong(fieldValueString);
        break;
      case FLOAT:
        fieldValue = Float.floatToIntBits(Float.parseFloat(fieldValueString));
        break;
      case DOUBLE:
        fieldValue = Double.doubleToLongBits(Double.parseDouble(fieldValueString));
        break;
      default:
        throw new IllegalArgumentException("Unsupported number type: " + schemaField.getType().getNumberType().name());
    }
    DemoContext demoContext = new DemoContext(fieldName, fieldValue);
    DemoMergeStrategy demoMergeStrategy = new DemoMergeStrategy(demoContext);

    if (rb.isDistributed()) {  // This is a request for multiple shards. This node merges multiple per-shard responses.
      if (rb.getMergeStrategies() != null && 0 < rb.getMergeStrategies().size()) {
        throw new SolrException(SolrException.ErrorCode.INVALID_STATE,
                                "Other components merging per-shard responses conflicts with this demo plug-in.");
      }
      // Set the strategy for the merging
      rb.addMergeStrategy(demoMergeStrategy);
    } else {  // This is a request for one shard. This node generates a per-shard response.
      if (null == params.get(ShardParams.IS_SHARD)) {  // This collection consists of only one shard.
        RankQuery demoRankQuery = new DemoRankQuery(demoMergeStrategy, demoContext);
        rb.setRankQuery(demoRankQuery);  // Set RankQuery for deduping at generation of the per-shard response
      } else {  // This collection consists of multiple shards.
        rb.mergeFieldHandler = demoMergeStrategy;  // Return docValues for deduping at merging
      }
    }
  }

  @Override
  public void process(ResponseBuilder rb) {}

  @Override
  public String getDescription() {
    return "A demo search component.";
  }
}
