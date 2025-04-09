
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;


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
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "historyQosPolicyType", propOrder = {

})
public class HistoryQosPolicyType {

    @XmlSchemaType(name = "string")
    protected HistoryQosKindPolicyType kind;
    @XmlSchemaType(name = "unsignedInt")
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
