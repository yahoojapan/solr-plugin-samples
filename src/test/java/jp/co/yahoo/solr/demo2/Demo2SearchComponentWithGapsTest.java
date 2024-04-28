package jp.co.yahoo.solr.demo2;

import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.util.RestTestBase;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Base64;

public class Demo2SearchComponentWithGapsTest extends RestTestBase {
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    File testHome = createTempDir().toFile();
    FileUtils.copyDirectory(getFile("solr"), testHome);

    createJettyAndHarness(testHome.getAbsolutePath(), "solrconfig_demo2.xml", "schema_demo2.xml", "/solr", true, null);
  }

  private static String base64StringOf(float... vector) {
    byte[] bytes = new byte[Float.BYTES * vector.length];
    ByteBuffer.wrap(bytes).asFloatBuffer().put(vector);
    return Base64.getEncoder().encodeToString(bytes);
  }

  @Before
  public void setUpBefore() {
    assertU(adoc("id", "0", "vector", base64StringOf(0, 3, 4)));  // -> (0, 0.6, 0.8)
    assertU(adoc("id", "a"));
    assertU(adoc("id", "1", "vector", base64StringOf(0, -5, 0)));  // -> (0, -1.0, 0)
    assertU(adoc("id", "b"));
    assertU(adoc("id", "c"));
    assertU(adoc("id", "2", "vector", base64StringOf(0, 0, 0)));
    assertU(commit());
  }

  @Test
  public void testWithPlugin() throws Exception {
    SolrQuery query = new SolrQuery();
    query.add(CommonParams.Q, "*:*");
    query.add(CommonParams.FL, "id, score");
    query.add(CommonParams.SORT, "score desc, id asc");
    query.add(Demo2Params.DEMO2_FIELD_NAME, "vector");
    query.add(Demo2Params.DEMO2_QUERY_VECTOR, base64StringOf(0, 3, 4));  // -> (0, 0.6, 0.8)

    assertJQ("/select" + query.toQueryString(),
             "/response/numFound==6",
             "/response/docs/[0]/id=='0'",
             "/response/docs/[1]/id=='2'",
             "/response/docs/[2]/id=='1'",
             "/response/docs/[3]/id=='a'",
             "/response/docs/[4]/id=='b'",
             "/response/docs/[5]/id=='c'",
             "/response/docs/[0]/score==1.0",
             "/response/docs/[1]/score==0.0",
             "/response/docs/[2]/score==-0.6",
             "/response/docs/[3]/score==-" + Float.MAX_VALUE,
             "/response/docs/[4]/score==-" + Float.MAX_VALUE,
             "/response/docs/[5]/score==-" + Float.MAX_VALUE);
  }
}
