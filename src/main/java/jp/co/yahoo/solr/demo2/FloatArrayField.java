package jp.co.yahoo.solr.demo2;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.SolrException;
import org.apache.solr.schema.BinaryField;
import org.apache.solr.schema.SchemaField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;

/**
 * A FieldType for float arrays.
 *
 * <p>Basically it is almost the same as solr.BinaryField.
 *
 * <p>The only difference is that it supports docValues="true" instead of stored="true".
 */
public class FloatArrayField extends BinaryField {
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  protected void checkSupportsDocValues() {}  // Indicates docValues support by not throwing an Exception

  @Override
  public IndexableField createField(SchemaField field, Object val) {
    if (val == null) return null;
    if (!field.hasDocValues()) {
      logger.trace("Ignoring float array field without docValues: " + field);
      return null;
    }
    byte[] buf;
    int offset = 0, len;
    if (val instanceof byte[]) {
      buf = (byte[]) val;
      len = buf.length;
    } else if (val instanceof ByteBuffer && ((ByteBuffer) val).hasArray()) {
      ByteBuffer byteBuf = (ByteBuffer) val;
      buf = byteBuf.array();
      offset = byteBuf.position();
      len = byteBuf.limit() - byteBuf.position();
    } else {
      String strVal = val.toString();
      Analyzer analyzer = field.getType().getIndexAnalyzer();
      float[] documentVector;
      try (TokenStream tokenStream = analyzer.tokenStream(field.getName(), strVal)) {
        tokenStream.reset();
        if (!tokenStream.incrementToken()) {
          throw new IOException("In demo2, just one token is expected");
        }
        documentVector = tokenStream.getAttribute(FloatArrayAttribute.class).getFloatArray();
        if (tokenStream.incrementToken()) {
          throw new IOException("In demo2, just one token is expected");
        }
        tokenStream.end();
      } catch (IOException e) {
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                                "Error while creating field '" + field + "' from value '" + strVal + "'", e);
      }
      buf = new byte[Float.BYTES * documentVector.length];
      ByteBuffer.wrap(buf).asFloatBuffer().put(documentVector);
      len = buf.length;
    }
    return new BinaryDocValuesField(field.getName(), new BytesRef(buf, offset, len));
  }

  @Override
  protected boolean supportsAnalyzers() {
    return true;
  }
}
