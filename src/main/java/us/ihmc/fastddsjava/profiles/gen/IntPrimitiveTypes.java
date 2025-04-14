
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for IntPrimitiveTypes.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="IntPrimitiveTypes">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="bool"/>
 *     &lt;enumeration value="char"/>
 *     &lt;enumeration value="char8"/>
 *     &lt;enumeration value="wchar"/>
 *     &lt;enumeration value="char16"/>
 *     &lt;enumeration value="byte"/>
 *     &lt;enumeration value="octet"/>
 *     &lt;enumeration value="int8"/>
 *     &lt;enumeration value="uint8"/>
 *     &lt;enumeration value="short"/>
 *     &lt;enumeration value="uShort"/>
 *     &lt;enumeration value="int16"/>
 *     &lt;enumeration value="uint16"/>
 *     &lt;enumeration value="long"/>
 *     &lt;enumeration value="uLong"/>
 *     &lt;enumeration value="int32"/>
 *     &lt;enumeration value="uint32"/>
 *     &lt;enumeration value="longLong"/>
 *     &lt;enumeration value="uLongLong"/>
 *     &lt;enumeration value="int64"/>
 *     &lt;enumeration value="uint64"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "IntPrimitiveTypes")
@XmlEnum
public enum IntPrimitiveTypes {

    @XmlEnumValue("bool")
    BOOL("bool"),
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
    UINT_64("uint64");
    private final String value;

    IntPrimitiveTypes(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static IntPrimitiveTypes fromValue(String v) {
        for (IntPrimitiveTypes c: IntPrimitiveTypes.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
