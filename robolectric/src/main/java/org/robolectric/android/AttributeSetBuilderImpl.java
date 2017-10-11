package org.robolectric.android;

import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.res.android.ResourceTypes.ANDROID_NS;
import static org.robolectric.res.android.ResourceTypes.AUTO_NS;
import static org.robolectric.res.android.ResourceTypes.RES_XML_END_ELEMENT_TYPE;
import static org.robolectric.res.android.ResourceTypes.RES_XML_RESOURCE_MAP_TYPE;
import static org.robolectric.res.android.ResourceTypes.RES_XML_START_ELEMENT_TYPE;
import static org.robolectric.res.android.ResourceTypes.ResTable_map.ATTR_TYPE;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.res.AttrData;
import org.robolectric.res.AttributeResource;
import org.robolectric.res.ResName;
import org.robolectric.res.ResourceTable;
import org.robolectric.res.android.DataType;
import org.robolectric.res.android.ResourceTable.flag_entry;
import org.robolectric.res.android.ResourceTypes.ResChunk_header;
import org.robolectric.res.android.ResourceTypes.ResStringPool_header.Writer;
import org.robolectric.res.android.ResourceTypes.ResXMLTree_attrExt;
import org.robolectric.res.android.ResourceTypes.ResXMLTree_endElementExt;
import org.robolectric.res.android.ResourceTypes.ResXMLTree_header;
import org.robolectric.res.android.ResourceTypes.ResXMLTree_node;
import org.robolectric.res.android.ResourceTypes.Res_value;
import org.robolectric.shadows.Converter;
import org.robolectric.shadows.ShadowArscAssetManager;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

public class AttributeSetBuilderImpl implements AttributeSetBuilder {
  private static final int STYLE_RES_ID = Integer.MAX_VALUE - 2;
  private static final int CLASS_RES_ID = Integer.MAX_VALUE - 1;
  private static final int ID_RES_ID = Integer.MAX_VALUE;

  private ResourceTable appResourceTable;
  private Map<Integer, AttrInfo> attrToValue = new TreeMap<>();

  static class AttrInfo {
    private final String value;

    public AttrInfo(String value) {
      this.value = value;
    }

    public AttrInfo(int value) {
      this.value = "@" + value;
    }
  }

  protected AttributeSetBuilderImpl(ResourceTable resourceTable) {
    this.appResourceTable = resourceTable;
  }

  @Override
  public AttributeSetBuilder addAttribute(int resId, String value) {
    attrToValue.put(resId, new AttrInfo(value));
    return this;
  }

  @Override
  public AttributeSetBuilder setStyleAttribute(String value) {
    attrToValue.put(STYLE_RES_ID, new AttrInfo(value));
    return this;
  }

  @Override
  public AttributeSetBuilder setClassAttribute(String value) {
    attrToValue.put(CLASS_RES_ID, new AttrInfo(value));
    return this;
  }

  @Override
  public AttributeSetBuilder setIdAttribute(String value) {
    attrToValue.put(ID_RES_ID, new AttrInfo(value));
    return this;
  }

  @Override
  public AttributeSet build() {
    Class<?> xmlBlockClass = ReflectionHelpers
        .loadClass(this.getClass().getClassLoader(), "android.content.res.XmlBlock");

    ByteBuffer buf = ByteBuffer.allocate(16 * 1024).order(ByteOrder.LITTLE_ENDIAN);
    Writer resStringPoolWriter = new Writer();

    final SparseArray<Integer> resIds = new SparseArray<>();
    final int[] maxAttrNameIndex = new int[] { 0 };

    ResXMLTree_attrExt.Writer dummyStart = new ResXMLTree_attrExt.Writer(buf, resStringPoolWriter,
        null, "dummy") {
      {
        for (Entry<Integer, AttrInfo> entry : attrToValue.entrySet()) {
          Integer attrId = entry.getKey();
          String attrNs = "";
          String attrName;
          ResName attrResName = null;
          switch (attrId) {
            case STYLE_RES_ID:
              attrId = null;
              attrName = "style";
              break;

            case CLASS_RES_ID:
              attrId = null;
              attrName = "class";
              break;

            case ID_RES_ID:
              attrId = null;
              attrName = "id";
              break;

            default:
              attrResName = appResourceTable.getResName(attrId);
              attrNs = (attrResName.packageName.equals("android")) ? ANDROID_NS : AUTO_NS;
              attrName = attrResName.name;
          }

          String value = entry.getValue().value;
          DataType type;
          int valueInt = 0;

          Context context = RuntimeEnvironment.application;
          Resources resources = context.getResources();
          AssetManager assets = resources.getAssets();
          ShadowArscAssetManager shadowArscAssetManager = shadowOf(assets);
          String packageName = context.getPackageName();
          if (attrResName != null) {
            TypedValue outValue = parse(attrId, attrResName, value, resources,
                shadowArscAssetManager, packageName);

            type = DataType.fromCode(outValue.type);
            value = (String) outValue.string;
            if (type == DataType.STRING) {
              valueInt = resStringPoolWriter.string(value);
            } else {
              valueInt = outValue.data;
            }
          } else {
            // it's a style, class, or id attribute, so no attr resource id
            if (value == null || AttributeResource.isNull(value)) {
              type = DataType.NULL;
            } else if (AttributeResource.isResourceReference(value)) {
              ResName resourceReference = AttributeResource
                  .getResourceReference(value, packageName, null);
              Integer valueResId = appResourceTable.getResourceId(resourceReference);
              type = DataType.REFERENCE;
              valueInt = valueResId;
            } else {
              type = DataType.STRING;
              valueInt = resStringPoolWriter.string(value);
            }
          }

          Res_value resValue = new Res_value(type.code(), valueInt);

          int attrNameIndex = resStringPoolWriter.uniqueString(attrName);
          attr(resStringPoolWriter.string(attrNs), attrNameIndex,
              resStringPoolWriter.string(value), resValue, attrNs + ":" + attrName);
          if (attrId != null) {
            resIds.put(attrNameIndex, attrId);
          }
          maxAttrNameIndex[0] = Math.max(maxAttrNameIndex[0], attrNameIndex);
        }
      }
    };

    ResXMLTree_endElementExt.Writer dummyEnd =
        new ResXMLTree_endElementExt.Writer(buf, resStringPoolWriter, null, "dummy");

    int finalMaxAttrNameIndex = maxAttrNameIndex[0];
    ResXMLTree_header.write(buf, resStringPoolWriter, () -> {
      if (finalMaxAttrNameIndex > 0) {
        ResChunk_header.write(buf, (short) RES_XML_RESOURCE_MAP_TYPE, () -> {}, () -> {
          // not particularly compact, but no big deal for our purposes...
          for (int i = 0; i <= finalMaxAttrNameIndex; i++) {
            Integer value = resIds.get(i);
            buf.putInt(value == null ? 0 : value);
          }
        });
      }

      ResXMLTree_node.write(buf, RES_XML_START_ELEMENT_TYPE, dummyStart::write);
      ResXMLTree_node.write(buf, RES_XML_END_ELEMENT_TYPE, dummyEnd::write);
    });

    int size = buf.position();
    byte[] bytes = new byte[size];
    buf.position(0);
    buf.get(bytes, 0, size);

    Object xmlBlockInstance = ReflectionHelpers
        .callConstructor(xmlBlockClass, ClassParameter.from(byte[].class, bytes));

    AttributeSet parser = ReflectionHelpers.callInstanceMethod(xmlBlockClass, xmlBlockInstance,
        "newParser");
    ReflectionHelpers.callInstanceMethod(parser, "next");
    ReflectionHelpers.callInstanceMethod(parser, "next");

    return parser;
  }

  private TypedValue parse(Integer attrId, ResName attrResName, String value, Resources resources,
      ShadowArscAssetManager shadowArscAssetManager, String packageName) {
    AttributeResource attribute =
        new AttributeResource(attrResName, value, packageName);
    TypedValue outValue = new TypedValue();

    if (attribute.isResourceReference()) {
      ResName resourceReference = attribute.getResourceReference();
      int id = resources.getIdentifier(resourceReference.name, resourceReference.type,
          resourceReference.packageName);
      if (id == 0) {
        throw new IllegalArgumentException("couldn't resolve " + attribute);
      }

      outValue.type = Res_value.TYPE_REFERENCE;
      outValue.data = id;
      outValue.resourceId = id;
      outValue.string = "@" + id;
    } else {
      String format = shadowArscAssetManager.getResourceBagText(attrId, ATTR_TYPE)
          .toString();
      Map<String, Integer> map = shadowArscAssetManager.getResourceBagValues(attrId);
      ArrayList<AttrData.Pair> pairs = new ArrayList<>();
      for (Entry<String, Integer> e : map.entrySet()) {
        pairs.add(new AttrData.Pair(e.getKey(), Integer.toString(e.getValue())));
      }
      int formatFlags = Integer.parseInt(format);
      flag_entry[] flags = org.robolectric.res.android.ResourceTable.gFormatFlags;
      for (flag_entry flag : flags) {
        if ((formatFlags & flag.value) != 0) {
          if ("reference".equals(flag.name)) {
            continue;
          }

          AttrData attrData = new AttrData(attrResName.getFullyQualifiedName(), flag.name, pairs);
          Converter converter = Converter.getConverterFor(attrData, flag.name);
          if (converter.fillTypedValue(attribute.value, outValue, true)) {
            break;
          }
        }
      }
    }
    return outValue;
  }
}