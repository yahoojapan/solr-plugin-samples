package jp.co.yahoo.solr.demo2;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.util.TokenizerFactory;
import org.apache.lucene.util.AttributeFactory;

import java.util.Map;

public class Base64ToFloatArrayTokenizerFactory extends TokenizerFactory {
  private final int length;

  public Base64ToFloatArrayTokenizerFactory(Map<String, String> args) {
    super(args);
    String length = args.remove("length");
    if (null == length) {
      this.length = -1;
    } else {
      this.length = Integer.parseInt(length);
    }
  }

  @Override
  public Tokenizer create(AttributeFactory factory) {
    return new Base64ToFloatArrayTokenizer(factory, length);
  }
}
