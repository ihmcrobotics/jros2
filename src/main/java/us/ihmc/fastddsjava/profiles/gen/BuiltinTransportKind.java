
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for builtinTransportKind.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="builtinTransportKind">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="NONE"/>
 *     &lt;enumeration value="DEFAULT"/>
 *     &lt;enumeration value="DEFAULTv6"/>
 *     &lt;enumeration value="SHM"/>
 *     &lt;enumeration value="UDPv4"/>
 *     &lt;enumeration value="UDPv6"/>
 *     &lt;enumeration value="LARGE_DATA"/>
 *     &lt;enumeration value="LARGE_DATAv6"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "builtinTransportKind")
@XmlEnum
public enum BuiltinTransportKind {

    NONE("NONE"),
    DEFAULT("DEFAULT"),
    @XmlEnumValue("DEFAULTv6")
    DEFAUL_TV_6("DEFAULTv6"),
    SHM("SHM"),
    @XmlEnumValue("UDPv4")
    UD_PV_4("UDPv4"),
    @XmlEnumValue("UDPv6")
    UD_PV_6("UDPv6"),
    LARGE_DATA("LARGE_DATA"),
    @XmlEnumValue("LARGE_DATAv6")
    LARGE_DAT_AV_6("LARGE_DATAv6");
    private final String value;

    BuiltinTransportKind(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static BuiltinTransportKind fromValue(String v) {
        for (BuiltinTransportKind c: BuiltinTransportKind.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
