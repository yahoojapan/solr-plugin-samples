package jp.co.yahoo.solr.demo2;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;

import java.util.Map;

public class VectorNormalizerTokenFilterFactory extends TokenFilterFactory {
  public VectorNormalizerTokenFilterFactory(Map<String, String> args) {
    super(args);
  }

  @Override
  public VectorNormalizerTokenFilter create(TokenStream input) {
    return new VectorNormalizerTokenFilter(input);
  }
}
