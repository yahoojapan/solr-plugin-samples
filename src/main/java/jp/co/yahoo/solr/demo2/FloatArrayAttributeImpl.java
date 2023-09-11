package jp.co.yahoo.solr.demo2;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public class FloatArrayAttributeImpl extends AttributeImpl implements FloatArrayAttribute {
  private float[] floatArray = null;

  @Override
  public float[] getFloatArray() {
    return floatArray;
  }

  @Override
  public void setFloatArray(float[] floatArray) {
    this.floatArray = floatArray;
  }

  @Override
  public void clear() {
    floatArray = null;
  }

  @Override
  public void reflectWith(AttributeReflector reflector) {
    reflector.reflect(FloatArrayAttribute.class, "floatArray", getFloatArray());
  }

  @Override
  public void copyTo(AttributeImpl target) {
    if (target instanceof FloatArrayAttributeImpl) {
      ((FloatArrayAttribute) target).setFloatArray(floatArray);
    }
  }
}
