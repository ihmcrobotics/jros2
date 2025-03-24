
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for historyQosKindPolicyType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="historyQosKindPolicyType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="KEEP_LAST"/>
 *     &lt;enumeration value="KEEP_ALL"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
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
