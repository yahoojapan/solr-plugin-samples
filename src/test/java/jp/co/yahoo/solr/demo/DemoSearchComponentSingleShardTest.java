package jp.co.yahoo.solr.demo;

import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.util.RestTestBase;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

public class DemoSearchComponentSingleShardTest extends RestTestBase {
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    File testHome = createTempDir().toFile();
    FileUtils.copyDirectory(getFile("solr"), testHome);

    createJettyAndHarness(testHome.getAbsolutePath(), "solrconfig.xml", "schema.xml", "/solr", true, null);
  }

  @Before
  public void setUpBefore() {
    assertU(adoc("id", "0", "price", "2600"));
    assertU(adoc("id", "1", "price", "2600"));
    assertU(adoc("id", "2", "price", "980"));
    assertU(adoc("id", "3", "price", "3500"));
    assertU(adoc("id", "4", "price", "680"));
    assertU(adoc("id", "5", "price", "1880"));
    assertU(adoc("id", "6", "price", "2880"));
    assertU(adoc("id", "7", "price", "2600"));
    assertU(adoc("id", "8", "price", "1380"));
    assertU(adoc("id", "9", "price", "780"));
    assertU(commit());
  }

  @Test
  public void testWithoutPlugin() throws Exception {
    SolrQuery query = new SolrQuery();
    query.add(CommonParams.Q, "{!func}field(price_float)");
    query.add(CommonParams.FL, "id");
    query.add(CommonParams.SORT, "score desc, id asc");

    assertJQ("/select" + query.toQueryString(),
             "/response/numFound==10",
             "/response/docs/[0]/id=='3'",
             "/response/docs/[1]/id=='6'",
             "/response/docs/[2]/id=='0'",
             "/response/docs/[3]/id=='1'",
             "/response/docs/[4]/id=='7'",
             "/response/docs/[5]/id=='5'",
             "/response/docs/[6]/id=='8'",
             "/response/docs/[7]/id=='2'",
             "/response/docs/[8]/id=='9'",
             "/response/docs/[9]/id=='4'");
  }

  @Test
  public void testWithPlugin() throws Exception {
    SolrQuery query = new SolrQuery();
    query.add(CommonParams.Q, "{!func}field(price_float)");
    query.add(CommonParams.FL, "id");
    query.add(CommonParams.SORT, "score desc, id asc");
    query.add(DemoParams.DEMO_FIELD_NAME, "price_int");
    query.add(DemoParams.DEMO_FIELD_VALUE, "2600");

    assertJQ("/select" + query.toQueryString(),
             "/response/numFound==10",

             // Documents which have the specified field value
             "/response/docs/[0]/id=='0'",
             "/response/docs/[1]/id=='1'",
             "/response/docs/[2]/id=='7'",

             // Documents which does not
             "/response/docs/[3]/id=='3'",
             "/response/docs/[4]/id=='6'",
             "/response/docs/[5]/id=='5'",
             "/response/docs/[6]/id=='8'",
             "/response/docs/[7]/id=='2'",
             "/response/docs/[8]/id=='9'",
             "/response/docs/[9]/id=='4'");
  }

  @Test
  public void testWithPlugin_long() throws Exception {
    SolrQuery query = new SolrQuery();
    query.add(CommonParams.Q, "{!func}field(price_float)");
    query.add(CommonParams.FL, "id");
    query.add(CommonParams.SORT, "score desc, id asc");
    query.add(DemoParams.DEMO_FIELD_NAME, "price_long");
    query.add(DemoParams.DEMO_FIELD_VALUE, "2600");

    assertJQ("/select" + query.toQueryString(),
             "/response/numFound==10",

             // Documents which have the specified field value
             "/response/docs/[0]/id=='0'",
             "/response/docs/[1]/id=='1'",
             "/response/docs/[2]/id=='7'",

             // Documents which does not
             "/response/docs/[3]/id=='3'",
             "/response/docs/[4]/id=='6'",
             "/response/docs/[5]/id=='5'",
             "/response/docs/[6]/id=='8'",
             "/response/docs/[7]/id=='2'",
             "/response/docs/[8]/id=='9'",
             "/response/docs/[9]/id=='4'");
  }

  @Test
  public void testWithPlugin_float() throws Exception {
    SolrQuery query = new SolrQuery();
    query.add(CommonParams.Q, "{!func}field(price_float)");
    query.add(CommonParams.FL, "id");
    query.add(CommonParams.SORT, "score desc, id asc");
    query.add(DemoParams.DEMO_FIELD_NAME, "price_float");
    query.add(DemoParams.DEMO_FIELD_VALUE, "2600.0");

    assertJQ("/select" + query.toQueryString(),
             "/response/numFound==10",

             // Documents which have the specified field value
             "/response/docs/[0]/id=='0'",
             "/response/docs/[1]/id=='1'",
             "/response/docs/[2]/id=='7'",

             // Documents which does not
             "/response/docs/[3]/id=='3'",
             "/response/docs/[4]/id=='6'",
             "/response/docs/[5]/id=='5'",
             "/response/docs/[6]/id=='8'",
             "/response/docs/[7]/id=='2'",
             "/response/docs/[8]/id=='9'",
             "/response/docs/[9]/id=='4'");
  }

  @Test
  public void testWithPlugin_double() throws Exception {
    SolrQuery query = new SolrQuery();
    query.add(CommonParams.Q, "{!func}field(price_float)");
    query.add(CommonParams.FL, "id");
    query.add(CommonParams.SORT, "score desc, id asc");
    query.add(DemoParams.DEMO_FIELD_NAME, "price_double");
    query.add(DemoParams.DEMO_FIELD_VALUE, "2600.0");

    assertJQ("/select" + query.toQueryString(),
             "/response/numFound==10",

             // Documents which have the specified field value
             "/response/docs/[0]/id=='0'",
             "/response/docs/[1]/id=='1'",
             "/response/docs/[2]/id=='7'",

             // Documents which does not
             "/response/docs/[3]/id=='3'",
             "/response/docs/[4]/id=='6'",
             "/response/docs/[5]/id=='5'",
             "/response/docs/[6]/id=='8'",
             "/response/docs/[7]/id=='2'",
             "/response/docs/[8]/id=='9'",
             "/response/docs/[9]/id=='4'");
  }

  @Test
  public void testNoDocument() throws Exception {
    SolrQuery query = new SolrQuery();
    query.add(CommonParams.Q, "{!func}field(price_float)");
    query.add(CommonParams.FL, "id");
    query.add(CommonParams.SORT, "score desc, id asc");
    query.add(DemoParams.DEMO_FIELD_NAME, "no_document");
    query.add(DemoParams.DEMO_FIELD_VALUE, "2600");

    assertJQ("/select" + query.toQueryString(),
             "/response/numFound==10",
             "/response/docs/[0]/id=='3'",
             "/response/docs/[1]/id=='6'",
             "/response/docs/[2]/id=='0'",
             "/response/docs/[3]/id=='1'",
             "/response/docs/[4]/id=='7'",
             "/response/docs/[5]/id=='5'",
             "/response/docs/[6]/id=='8'",
             "/response/docs/[7]/id=='2'",
             "/response/docs/[8]/id=='9'",
             "/response/docs/[9]/id=='4'");
  }

  @Test
  public void testNotNumberType() throws Exception {
    SolrQuery query = new SolrQuery();
    query.add(CommonParams.Q, "*:*");
    query.add(DemoParams.DEMO_FIELD_NAME, "price");
    query.add(DemoParams.DEMO_FIELD_VALUE, "2600");

    assertJQ("/select" + query.toQueryString(),
             "/error/msg=='Target field is not numeric field: price'");
  }
}
