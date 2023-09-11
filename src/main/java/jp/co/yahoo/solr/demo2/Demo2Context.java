package jp.co.yahoo.solr.demo2;

import java.util.Arrays;
import java.util.Objects;

public class Demo2Context {
  public String fieldName;
  public float[] queryVector;

  /**
   * Constructor for demo2 context object.
   *
   * @param fieldName document vector field name
   * @param queryVector query vector
   */
  public Demo2Context(String fieldName, float[] queryVector) {
    this.fieldName = fieldName;
    this.queryVector = queryVector;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;

    Demo2Context that = (Demo2Context) obj;
    return that.fieldName.equals(fieldName) && Arrays.equals(queryVector, that.queryVector);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(fieldName);
    result = 31 * result + Arrays.hashCode(queryVector);
    return result;
  }
}
