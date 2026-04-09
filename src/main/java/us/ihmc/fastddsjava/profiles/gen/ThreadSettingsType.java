
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;


import java.math.BigInteger;


/**
 * <p>Java class for threadSettingsType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="threadSettingsType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="scheduling_policy" type="{http://www.eprosima.com}uint32_with_negative_default" minOccurs="0"/&gt;
 *         &lt;element name="priority" type="{http://www.eprosima.com}int32" minOccurs="0"/&gt;
 *         &lt;element name="affinity" type="{http://www.eprosima.com}uint64" minOccurs="0"/&gt;
 *         &lt;element name="stack_size" type="{http://www.eprosima.com}uint32_with_negative_default" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)

public class ThreadSettingsType {

    @JacksonXmlProperty(localName = "scheduling_policy")
    protected Integer schedulingPolicy;
    protected Integer priority;
        protected BigInteger affinity;
    @JacksonXmlProperty(localName = "stack_size")
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
