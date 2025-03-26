
package us.ihmc.fastddsjava.profiles.gen;

import java.math.BigInteger;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for threadSettingsType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="threadSettingsType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element name="scheduling_policy" type="{http://www.eprosima.com}uint32_with_negative_default" minOccurs="0"/>
 *         &lt;element name="priority" type="{http://www.eprosima.com}int32" minOccurs="0"/>
 *         &lt;element name="affinity" type="{http://www.eprosima.com}uint64" minOccurs="0"/>
 *         &lt;element name="stack_size" type="{http://www.eprosima.com}uint32_with_negative_default" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "threadSettingsType", propOrder = {

})
@XmlSeeAlso({
    ThreadSettingsWithPortType.class
})
public class ThreadSettingsType {

    @XmlElement(name = "scheduling_policy")
    protected Integer schedulingPolicy;
    protected Integer priority;
    @XmlSchemaType(name = "unsignedLong")
    protected BigInteger affinity;
    @XmlElement(name = "stack_size")
    protected Integer stackSize;

    /**
     * Gets the value of the schedulingPolicy property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getSchedulingPolicy() {
        return schedulingPolicy;
    }

    /**
     * Sets the value of the schedulingPolicy property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setSchedulingPolicy(Integer value) {
        this.schedulingPolicy = value;
    }

    /**
     * Gets the value of the priority property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getPriority() {
        return priority;
    }

    /**
     * Sets the value of the priority property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setPriority(Integer value) {
        this.priority = value;
    }

    /**
     * Gets the value of the affinity property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getAffinity() {
        return affinity;
    }

    /**
     * Sets the value of the affinity property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setAffinity(BigInteger value) {
        this.affinity = value;
    }

    /**
     * Gets the value of the stackSize property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getStackSize() {
        return stackSize;
    }

    /**
     * Sets the value of the stackSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setStackSize(Integer value) {
        this.stackSize = value;
    }

}
