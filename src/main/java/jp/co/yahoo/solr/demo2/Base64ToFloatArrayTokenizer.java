package jp.co.yahoo.solr.demo2;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.util.AttributeFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Base64;

public class Base64ToFloatArrayTokenizer extends Tokenizer {
  private final static Base64.Decoder base64Decoder = Base64.getDecoder();

  private boolean done = false;
  private final int length;
  private final FloatArrayAttribute floatArrayAttribute = addAttribute(FloatArrayAttribute.class);

  public Base64ToFloatArrayTokenizer(AttributeFactory factory, int length) {
    super(factory);
    this.length = length;
  }

  @Override
  public final boolean incrementToken() throws IOException {
    if (done) {
      return false;
    }
    done = true;

    String base64String = IOUtils.toString(input);
    byte[] bytes = base64Decoder.decode(base64String);
    FloatBuffer floatBuffer = ByteBuffer.wrap(bytes).asFloatBuffer();
    if (0 <= length && length != floatBuffer.limit()) {
      throw new IOException("Array length is not expected: " + base64String);
    }
    float[] floatArray = new float[floatBuffer.limit()];
    floatBuffer.get(floatArray);

    clearAttributes();
    floatArrayAttribute.setFloatArray(floatArray);
    return true;
  }

  @Override
  public void reset() throws IOException {
    super.reset();
    this.done = false;
  }
}
