package jp.co.yahoo.solr.demo;

import org.apache.solr.BaseDistributedSearchTestCase;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.Matchers.is;

public class DemoSearchComponentTest extends BaseDistributedSearchTestCase {
  public DemoSearchComponentTest() {
    stress = 0;
  }

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    initCore("solrconfig.xml", "schema.xml");
  }

  @Before
  public void setUpBefore() throws Exception {
    index("id", "0", "price", "2600");
    index("id", "1", "price", "2600");
    index("id", "2", "price", "980");
    index("id", "3", "price", "3500");
    index("id", "4", "price", "680");
    index("id", "5", "price", "1880");
    index("id", "6", "price", "2880");
    index("id", "7", "price", "2600");
    index("id", "8", "price", "1380");
    index("id", "9", "price", "780");
    commit();
  }

  @Test
  @BaseDistributedSearchTestCase.ShardsFixed(num = 3)
  public void testWithoutPlugin() throws Exception {
    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "{!func}field(price_float)");
    params.set(CommonParams.FL, "id");
    params.set(CommonParams.SORT, "score desc, id asc");
    setDistributedParams(params);

    QueryResponse actualResponse = queryServer(params);
    SolrDocumentList actualDocuments = actualResponse.getResults();

    assertEquals(10, actualDocuments.getNumFound());
    assertThat(actualDocuments.get(0).get("id").toString(), is("3"));
    assertThat(actualDocuments.get(1).get("id").toString(), is("6"));
    assertThat(actualDocuments.get(2).get("id").toString(), is("0"));
    assertThat(actualDocuments.get(3).get("id").toString(), is("1"));
    assertThat(actualDocuments.get(4).get("id").toString(), is("7"));
    assertThat(actualDocuments.get(5).get("id").toString(), is("5"));
    assertThat(actualDocuments.get(6).get("id").toString(), is("8"));
    assertThat(actualDocuments.get(7).get("id").toString(), is("2"));
    assertThat(actualDocuments.get(8).get("id").toString(), is("9"));
    assertThat(actualDocuments.get(9).get("id").toString(), is("4"));
  }

  @Test
  @BaseDistributedSearchTestCase.ShardsFixed(num = 3)
  public void testWithPlugin() throws Exception {
    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "{!func}field(price_float)");
    params.set(CommonParams.FL, "id");
    params.set(CommonParams.SORT, "score desc, id asc");
    params.set(DemoParams.DEMO_FIELD_NAME, "price_int");
    params.set(DemoParams.DEMO_FIELD_VALUE, "2600");
    setDistributedParams(params);

    QueryResponse actualResponse = queryServer(params);
    SolrDocumentList actual = actualResponse.getResults();

    assertEquals(10, actual.getNumFound());

    // Documents which have the specified field value
    assertThat(actual.get(0).get("id").toString(), is("0"));
    assertThat(actual.get(1).get("id").toString(), is("1"));
    assertThat(actual.get(2).get("id").toString(), is("7"));

    // Documents which does not
    assertThat(actual.get(3).get("id").toString(), is("3"));
    assertThat(actual.get(4).get("id").toString(), is("6"));
    assertThat(actual.get(5).get("id").toString(), is("5"));
    assertThat(actual.get(6).get("id").toString(), is("8"));
    assertThat(actual.get(7).get("id").toString(), is("2"));
    assertThat(actual.get(8).get("id").toString(), is("9"));
    assertThat(actual.get(9).get("id").toString(), is("4"));
  }

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  @ShardsFixed(num = 3)
  public void testNoFieldName() throws Exception {
    expectedException.expect(HttpSolrClient.RemoteSolrException.class);
    expectedException.expectMessage("Only demo.field.name or demo.field.value is specified.");

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "*:*");
    params.set(DemoParams.DEMO_FIELD_VALUE, "2600");
    setDistributedParams(params);

    queryServer(params);
  }

  @Test
  @ShardsFixed(num = 3)
  public void testNoFieldValue() throws Exception {
    expectedException.expect(HttpSolrClient.RemoteSolrException.class);
    expectedException.expectMessage("Only demo.field.name or demo.field.value is specified.");

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "*:*");
    params.set(DemoParams.DEMO_FIELD_NAME, "price_int");
    setDistributedParams(params);

    queryServer(params);
  }

  @Test
  @ShardsFixed(num = 3)
  public void testUnknownField() throws Exception {
    expectedException.expect(HttpSolrClient.RemoteSolrException.class);
    expectedException.expectMessage("Unknown demo.field.name: unknown");

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "*:*");
    params.set(DemoParams.DEMO_FIELD_NAME, "unknown");
    params.set(DemoParams.DEMO_FIELD_VALUE, "2600");
    setDistributedParams(params);

    queryServer(params);
  }

  @Test
  @ShardsFixed(num = 3)
  public void testNoDocValues() throws Exception {
    expectedException.expect(HttpSolrClient.RemoteSolrException.class);
    expectedException.expectMessage("Target field must have docValues: no_docValues");

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "*:*");
    params.set(DemoParams.DEMO_FIELD_NAME, "no_docValues");
    params.set(DemoParams.DEMO_FIELD_VALUE, "2600");
    setDistributedParams(params);

    queryServer(params);
  }

  @Test
  @ShardsFixed(num = 3)
  public void testUnsupportedNumberType() throws Exception {
    expectedException.expect(HttpSolrClient.RemoteSolrException.class);
    expectedException.expectMessage("Unsupported number type: DATE");

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "*:*");
    params.set(DemoParams.DEMO_FIELD_NAME, "some_date");
    params.set(DemoParams.DEMO_FIELD_VALUE, "2600");
    setDistributedParams(params);

    queryServer(params);
  }
}
