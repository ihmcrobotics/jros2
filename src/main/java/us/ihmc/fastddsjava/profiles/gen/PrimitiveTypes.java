
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for primitiveTypes.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="primitiveTypes"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="bool"/&gt;
 *     &lt;enumeration value="boolean"/&gt;
 *     &lt;enumeration value="char"/&gt;
 *     &lt;enumeration value="char8"/&gt;
 *     &lt;enumeration value="wchar"/&gt;
 *     &lt;enumeration value="char16"/&gt;
 *     &lt;enumeration value="byte"/&gt;
 *     &lt;enumeration value="octet"/&gt;
 *     &lt;enumeration value="int8"/&gt;
 *     &lt;enumeration value="uint8"/&gt;
 *     &lt;enumeration value="short"/&gt;
 *     &lt;enumeration value="uShort"/&gt;
 *     &lt;enumeration value="int16"/&gt;
 *     &lt;enumeration value="uint16"/&gt;
 *     &lt;enumeration value="long"/&gt;
 *     &lt;enumeration value="uLong"/&gt;
 *     &lt;enumeration value="int32"/&gt;
 *     &lt;enumeration value="uint32"/&gt;
 *     &lt;enumeration value="longLong"/&gt;
 *     &lt;enumeration value="uLongLong"/&gt;
 *     &lt;enumeration value="int64"/&gt;
 *     &lt;enumeration value="uint64"/&gt;
 *     &lt;enumeration value="float32"/&gt;
 *     &lt;enumeration value="float64"/&gt;
 *     &lt;enumeration value="float128"/&gt;
 *     &lt;enumeration value="string"/&gt;
 *     &lt;enumeration value="wstring"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "primitiveTypes")
@XmlEnum
public enum PrimitiveTypes {

    @XmlEnumValue("bool")
    BOOL("bool"),
    @XmlEnumValue("boolean")
    BOOLEAN("boolean"),
    @XmlEnumValue("char")
    CHAR("char"),
    @XmlEnumValue("char8")
    CHAR_8("char8"),
    @XmlEnumValue("wchar")
    WCHAR("wchar"),
    @XmlEnumValue("char16")
    CHAR_16("char16"),
    @XmlEnumValue("byte")
    BYTE("byte"),
    @XmlEnumValue("octet")
    OCTET("octet"),
    @XmlEnumValue("int8")
    INT_8("int8"),
    @XmlEnumValue("uint8")
    UINT_8("uint8"),
    @XmlEnumValue("short")
    SHORT("short"),
    @XmlEnumValue("uShort")
    U_SHORT("uShort"),
    @XmlEnumValue("int16")
    INT_16("int16"),
    @XmlEnumValue("uint16")
    UINT_16("uint16"),
    @XmlEnumValue("long")
    LONG("long"),
    @XmlEnumValue("uLong")
    U_LONG("uLong"),
    @XmlEnumValue("int32")
    INT_32("int32"),
    @XmlEnumValue("uint32")
    UINT_32("uint32"),
    @XmlEnumValue("longLong")
    LONG_LONG("longLong"),
    @XmlEnumValue("uLongLong")
    U_LONG_LONG("uLongLong"),
    @XmlEnumValue("int64")
    INT_64("int64"),
    @XmlEnumValue("uint64")
    UINT_64("uint64"),
    @XmlEnumValue("float32")
    FLOAT_32("float32"),
    @XmlEnumValue("float64")
    FLOAT_64("float64"),
    @XmlEnumValue("float128")
    FLOAT_128("float128"),
    @XmlEnumValue("string")
    STRING("string"),
    @XmlEnumValue("wstring")
    WSTRING("wstring");
    private final String value;

    PrimitiveTypes(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PrimitiveTypes fromValue(String v) {
        for (PrimitiveTypes c: PrimitiveTypes.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
