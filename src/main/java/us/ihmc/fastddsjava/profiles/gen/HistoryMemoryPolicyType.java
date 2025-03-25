
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for historyMemoryPolicyType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="historyMemoryPolicyType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="PREALLOCATED"/&gt;
 *     &lt;enumeration value="PREALLOCATED_WITH_REALLOC"/&gt;
 *     &lt;enumeration value="DYNAMIC"/&gt;
 *     &lt;enumeration value="DYNAMIC_REUSABLE"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "historyMemoryPolicyType")
@XmlEnum
public enum HistoryMemoryPolicyType {

    PREALLOCATED,
    PREALLOCATED_WITH_REALLOC,
    DYNAMIC,
    DYNAMIC_REUSABLE;

    public String value() {
        return name();
    }

    public static HistoryMemoryPolicyType fromValue(String v) {
        return valueOf(v);
    }

}
