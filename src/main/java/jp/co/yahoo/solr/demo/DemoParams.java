package jp.co.yahoo.solr.demo;

public interface DemoParams {
  String DEMO_FIELD_NAME = "demo.field.name";  // Target field name
  String DEMO_FIELD_VALUE = "demo.field.value";  // Target field value in string

  String DEMO_RESPONSE_KEY = "demo_response_key";  // Key for docValues exchange between Solr nodes
}
