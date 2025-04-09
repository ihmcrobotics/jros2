
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for defaultType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="defaultType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="default"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "defaultType")
@XmlEnum
public enum DefaultType {

    @XmlEnumValue("default")
    DEFAULT("default");
    private final String value;

    DefaultType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DefaultType fromValue(String v) {
        for (DefaultType c: DefaultType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
