package jp.co.yahoo.solr.demo;

import java.util.Objects;

public class DemoContext {
  public String fieldName;
  public long fieldValue;

  /**
   * Constructor for demo context object.
   *
   * @param fieldName target field name
   * @param fieldValue target field value in long bits
   */
  public DemoContext(String fieldName, long fieldValue) {
    this.fieldName = fieldName;
    this.fieldValue = fieldValue;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;

    DemoContext that = (DemoContext) obj;
    return that.fieldName.equals(fieldName) && that.fieldValue == fieldValue;
  }

  @Override
  public int hashCode() {
    return Objects.hash(fieldName, fieldValue);
  }
}
