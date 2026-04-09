
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;




/**
 * <p>Java class for durabilityServiceQosPolicyType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="durabilityServiceQosPolicyType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="service_cleanup_delay" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
 *         &lt;element name="history_kind" type="{http://www.eprosima.com}historyQosKindPolicyType" minOccurs="0"/&gt;
 *         &lt;element name="history_depth" type="{http://www.eprosima.com}int32" minOccurs="0"/&gt;
 *         &lt;element name="max_samples" type="{http://www.eprosima.com}int32" minOccurs="0"/&gt;
 *         &lt;element name="max_instances" type="{http://www.eprosima.com}int32" minOccurs="0"/&gt;
 *         &lt;element name="max_samples_per_instance" type="{http://www.eprosima.com}int32" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)

public class DurabilityServiceQosPolicyType {

    @JacksonXmlProperty(localName = "service_cleanup_delay")
    protected DurationType serviceCleanupDelay;
    @JacksonXmlProperty(localName = "history_kind")
        protected HistoryQosKindPolicyType historyKind;
    @JacksonXmlProperty(localName = "history_depth")
    protected Integer historyDepth;
    @JacksonXmlProperty(localName = "max_samples")
    protected Integer maxSamples;
    @JacksonXmlProperty(localName = "max_instances")
    protected Integer maxInstances;
    @JacksonXmlProperty(localName = "max_samples_per_instance")
    protected Integer maxSamplesPerInstance;

    /**
     * Gets the value of the serviceCleanupDelay property.
     * 
     * @return
     *     possible object is
     *     {@link DurationType }
     *     
     */
    public DurationType getServiceCleanupDelay() {
        return serviceCleanupDelay;
    }

    /**
     * Sets the value of the serviceCleanupDelay property.
     * 
     * @param value
     *     allowed object is
     *     {@link DurationType }
     *     
     */
    public void setServiceCleanupDelay(DurationType value) {
        this.serviceCleanupDelay = value;
    }

    /**
     * Gets the value of the historyKind property.
     * 
     * @return
     *     possible object is
     *     {@link HistoryQosKindPolicyType }
     *     
     */
    public HistoryQosKindPolicyType getHistoryKind() {
        return historyKind;
    }

    /**
     * Sets the value of the historyKind property.
     * 
     * @param value
     *     allowed object is
     *     {@link HistoryQosKindPolicyType }
     *     
     */
    public void setHistoryKind(HistoryQosKindPolicyType value) {
        this.historyKind = value;
    }

    /**
     * Gets the value of the historyDepth property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getHistoryDepth() {
        return historyDepth;
    }

    /**
     * Sets the value of the historyDepth property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setHistoryDepth(Integer value) {
        this.historyDepth = value;
    }

    /**
     * Gets the value of the maxSamples property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getMaxSamples() {
        return maxSamples;
    }

    /**
     * Sets the value of the maxSamples property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setMaxSamples(Integer value) {
        this.maxSamples = value;
    }

    /**
     * Gets the value of the maxInstances property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getMaxInstances() {
        return maxInstances;
    }

    /**
     * Sets the value of the maxInstances property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setMaxInstances(Integer value) {
        this.maxInstances = value;
    }

    /**
     * Gets the value of the maxSamplesPerInstance property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getMaxSamplesPerInstance() {
        return maxSamplesPerInstance;
    }

    /**
     * Sets the value of the maxSamplesPerInstance property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setMaxSamplesPerInstance(Integer value) {
        this.maxSamplesPerInstance = value;
    }

}
