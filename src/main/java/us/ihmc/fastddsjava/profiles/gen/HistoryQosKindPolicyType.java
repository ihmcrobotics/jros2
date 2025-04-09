
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for historyQosKindPolicyType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="historyQosKindPolicyType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="KEEP_LAST"/&gt;
 *     &lt;enumeration value="KEEP_ALL"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "historyQosKindPolicyType")
@XmlEnum
public enum HistoryQosKindPolicyType {

    KEEP_LAST,
    KEEP_ALL;

    public String value() {
        return name();
    }

    public static HistoryQosKindPolicyType fromValue(String v) {
        return valueOf(v);
    }

}
