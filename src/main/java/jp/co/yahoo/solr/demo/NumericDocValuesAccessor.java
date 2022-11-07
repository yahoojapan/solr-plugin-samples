package jp.co.yahoo.solr.demo;

import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;

import java.io.IOException;

public class NumericDocValuesAccessor {
  private final NumericDocValues docValues;

  /**
   * Constructor for numeric docValues accessor.
   *
   * @param fieldName target field name
   * @param context context object for target index segment
   * @throws IOException when failed to read docValues
   */
  public NumericDocValuesAccessor(String fieldName, LeafReaderContext context) throws IOException {
    final FieldInfo fieldInfo = context.reader().getFieldInfos().fieldInfo(fieldName);
    if (fieldInfo == null) {  // All documents in this index segment do not have docValues in this field
      docValues = null;
      return;
    }

    if (fieldInfo.getDocValuesType() != DocValuesType.NUMERIC) {
      throw new IllegalArgumentException("Unsupported docValues type: " + fieldInfo.getDocValuesType().name());
    }

    this.docValues = context.reader().getNumericDocValues(fieldName);
  }

  /**
   * Get a numeric value from docValues.
   *
   * @param targetDocId target document ID
   * @return an int value
   * @throws IOException when failed to read docValues
   */
  public Long getLongValue(int targetDocId) throws IOException {
    if (docValues == null) return null;

    int currentDocId = docValues.docID();
    if (currentDocId < targetDocId) {
      currentDocId = docValues.advance(targetDocId);  // while currentDocId < targetDocId
    }
    if (currentDocId == targetDocId) {  // There is a value
      return docValues.longValue();
    } else {  // No value
      return null;
    }
  }
}
