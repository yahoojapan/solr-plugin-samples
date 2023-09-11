package jp.co.yahoo.solr.demo2;

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

import java.nio.ByteBuffer;
import java.util.Base64;

import static org.hamcrest.Matchers.is;

public class Demo2SearchComponentTest extends BaseDistributedSearchTestCase {
  public Demo2SearchComponentTest() {
    stress = 0;
  }

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    initCore("solrconfig_demo2.xml", "schema_demo2.xml");
  }

  private static String base64StringOf(float... vector) {
    byte[] bytes = new byte[Float.BYTES * vector.length];
    ByteBuffer.wrap(bytes).asFloatBuffer().put(vector);
    return Base64.getEncoder().encodeToString(bytes);
  }

  @Before
  public void setUpBefore() throws Exception {
    index("id", "0", "vector", base64StringOf(0, 3, 4));  // -> (0, 0.6, 0.8)
    index("id", "1", "vector", base64StringOf(0, -5, 0));  // -> (0, -1.0, 0)
    index("id", "2", "vector", base64StringOf(0, 0, 0));
    index("id", "3");
    commit();
  }

  @Test
  @ShardsFixed(num = 3)
  public void testWithoutPlugin() throws Exception {
    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "*:*");
    params.set(CommonParams.FL, "id");
    params.set(CommonParams.SORT, "id asc");
    setDistributedParams(params);

    QueryResponse actualResponse = queryServer(params);
    SolrDocumentList actualDocuments = actualResponse.getResults();

    assertEquals(4, actualDocuments.getNumFound());
    assertThat(actualDocuments.get(0).get("id").toString(), is("0"));
    assertThat(actualDocuments.get(1).get("id").toString(), is("1"));
    assertThat(actualDocuments.get(2).get("id").toString(), is("2"));
    assertThat(actualDocuments.get(3).get("id").toString(), is("3"));
  }

  @Test
  @ShardsFixed(num = 3)
  public void testWithPlugin() throws Exception {
    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "*:*");
    params.set(CommonParams.FL, "id, score");
    params.set(CommonParams.SORT, "score desc, id asc");
    params.set(Demo2Params.DEMO2_FIELD_NAME, "vector");
    params.set(Demo2Params.DEMO2_QUERY_VECTOR, base64StringOf(0, 3, 4));  // -> (0, 0.6, 0.8)
    setDistributedParams(params);

    QueryResponse actualResponse = queryServer(params);
    SolrDocumentList actual = actualResponse.getResults();

    assertEquals(4, actual.getNumFound());

    assertThat(actual.get(0).get("id").toString(), is("0"));
    assertThat(actual.get(1).get("id").toString(), is("2"));
    assertThat(actual.get(2).get("id").toString(), is("1"));
    assertThat(actual.get(3).get("id").toString(), is("3"));

    assertThat(actual.get(0).get("score"), is(1.0f));
    assertThat(actual.get(1).get("score"), is(0.0f));
    assertThat(actual.get(2).get("score"), is(-0.6f));
    assertThat(actual.get(3).get("score"), is(-Float.MAX_VALUE));
  }

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  @ShardsFixed(num = 3)
  public void testNoFieldName() throws Exception {
    expectedException.expect(HttpSolrClient.RemoteSolrException.class);
    expectedException.expectMessage("Only demo2.field.name or demo2.query.vector is specified.");

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "*:*");
    params.set(Demo2Params.DEMO2_QUERY_VECTOR, base64StringOf(0, 3, 4));
    setDistributedParams(params);

    queryServer(params);
  }

  @Test
  @ShardsFixed(num = 3)
  public void testNoQueryVector() throws Exception {
    expectedException.expect(HttpSolrClient.RemoteSolrException.class);
    expectedException.expectMessage("Only demo2.field.name or demo2.query.vector is specified.");

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "*:*");
    params.set(Demo2Params.DEMO2_FIELD_NAME, "vector");
    setDistributedParams(params);

    queryServer(params);
  }

  @Test
  @ShardsFixed(num = 3)
  public void testUnknownField() throws Exception {
    expectedException.expect(HttpSolrClient.RemoteSolrException.class);
    expectedException.expectMessage("Unknown demo2.field.name: unknown");

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "*:*");
    params.set(Demo2Params.DEMO2_FIELD_NAME, "unknown");
    params.set(Demo2Params.DEMO2_QUERY_VECTOR, base64StringOf(0, 3, 4));
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
    params.set(Demo2Params.DEMO2_FIELD_NAME, "no_docValues");
    params.set(Demo2Params.DEMO2_QUERY_VECTOR, base64StringOf(0, 3, 4));
    setDistributedParams(params);

    queryServer(params);
  }

  @Test
  @ShardsFixed(num = 3)
  public void testUnsupportedFieldType() throws Exception {
    expectedException.expect(HttpSolrClient.RemoteSolrException.class);
    expectedException.expectMessage("Target field is not float array field: some_string");

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "*:*");
    params.set(Demo2Params.DEMO2_FIELD_NAME, "some_string");
    params.set(Demo2Params.DEMO2_QUERY_VECTOR, base64StringOf(0, 3, 4));
    setDistributedParams(params);

    queryServer(params);
  }
}
