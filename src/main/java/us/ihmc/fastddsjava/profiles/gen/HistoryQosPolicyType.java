
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;




/**
 * <p>Java class for historyQosPolicyType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="historyQosPolicyType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="kind" type="{http://www.eprosima.com}historyQosKindPolicyType" minOccurs="0"/&gt;
 *         &lt;element name="depth" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)

public class HistoryQosPolicyType {

        protected HistoryQosKindPolicyType kind;
        protected Long depth;

    /**
     * Gets the value of the kind property.
     * 
     * @return
     *     possible object is
     *     {@link HistoryQosKindPolicyType }
     *     
     */
    public HistoryQosKindPolicyType getKind() {
        return kind;
    }

    /**
     * Sets the value of the kind property.
     * 
     * @param value
     *     allowed object is
     *     {@link HistoryQosKindPolicyType }
     *     
     */
    public void setKind(HistoryQosKindPolicyType value) {
        this.kind = value;
    }

    /**
     * Gets the value of the depth property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getDepth() {
        return depth;
    }

    /**
     * Sets the value of the depth property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setDepth(Long value) {
        this.depth = value;
    }

}
