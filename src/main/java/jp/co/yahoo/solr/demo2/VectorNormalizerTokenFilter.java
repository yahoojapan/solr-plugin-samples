package jp.co.yahoo.solr.demo2;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;

import java.io.IOException;

public class VectorNormalizerTokenFilter extends TokenFilter {
  private final FloatArrayAttribute floatArrayAttribute = addAttribute(FloatArrayAttribute.class);

  public VectorNormalizerTokenFilter(TokenStream input) {
    super(input);
  }

  @Override
  public final boolean incrementToken() throws IOException {
    if (input.incrementToken()) {
      float[] floatArray = floatArrayAttribute.getFloatArray();
      if (null == floatArray) {
        return true;
      }
      float norm = 0.0f;
      for (float f : floatArray) {
        norm += f * f;
      }
      if (0 < norm) {
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < floatArray.length; ++i) {
          floatArray[i] /= norm;
        }
      }
      return true;
    } else {
      return false;
    }
  }
}
