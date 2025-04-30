
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for flowControllerSchedulerPolicy.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="flowControllerSchedulerPolicy"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="FIFO"/&gt;
 *     &lt;enumeration value="ROUND_ROBIN"/&gt;
 *     &lt;enumeration value="HIGH_PRIORITY"/&gt;
 *     &lt;enumeration value="PRIORITY_WITH_RESERVATION"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "flowControllerSchedulerPolicy")
@XmlEnum
public enum FlowControllerSchedulerPolicy {

    FIFO,
    ROUND_ROBIN,
    HIGH_PRIORITY,
    PRIORITY_WITH_RESERVATION;

    public String value() {
        return name();
    }

    public static FlowControllerSchedulerPolicy fromValue(String v) {
        return valueOf(v);
    }

}
