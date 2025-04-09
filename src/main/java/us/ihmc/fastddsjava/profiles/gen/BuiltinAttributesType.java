
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for builtinAttributesType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="builtinAttributesType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element name="discovery_config" type="{http://www.eprosima.com}discoverySettingsType" minOccurs="0"/>
 *         &lt;element name="avoid_builtin_multicast" type="{http://www.eprosima.com}boolean" minOccurs="0"/>
 *         &lt;element name="use_WriterLivelinessProtocol" type="{http://www.eprosima.com}boolean" minOccurs="0"/>
 *         &lt;element name="metatraffic_external_unicast_locators" type="{http://www.eprosima.com}externalLocatorListType" minOccurs="0"/>
 *         &lt;element name="metatrafficUnicastLocatorList" type="{http://www.eprosima.com}locatorListType" minOccurs="0"/>
 *         &lt;element name="metatrafficMulticastLocatorList" type="{http://www.eprosima.com}locatorListType" minOccurs="0"/>
 *         &lt;element name="initialPeersList" type="{http://www.eprosima.com}locatorListType" minOccurs="0"/>
 *         &lt;element name="readerHistoryMemoryPolicy" type="{http://www.eprosima.com}historyMemoryPolicyType" minOccurs="0"/>
 *         &lt;element name="writerHistoryMemoryPolicy" type="{http://www.eprosima.com}historyMemoryPolicyType" minOccurs="0"/>
 *         &lt;element name="readerPayloadSize" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
 *         &lt;element name="writerPayloadSize" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
 *         &lt;element name="mutation_tries" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
 *         &lt;element name="flow_controller_name" type="{http://www.eprosima.com}string" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "builtinAttributesType", propOrder = {

})
public class BuiltinAttributesType {

    @XmlElement(name = "discovery_config")
    protected DiscoverySettingsType discoveryConfig;
    @XmlElement(name = "avoid_builtin_multicast")
    protected Boolean avoidBuiltinMulticast;
    @XmlElement(name = "use_WriterLivelinessProtocol")
    protected Boolean useWriterLivelinessProtocol;
    @XmlElement(name = "metatraffic_external_unicast_locators")
    protected ExternalLocatorListType metatrafficExternalUnicastLocators;
    protected LocatorListType metatrafficUnicastLocatorList;
    protected LocatorListType metatrafficMulticastLocatorList;
    protected LocatorListType initialPeersList;
    @XmlSchemaType(name = "string")
    protected HistoryMemoryPolicyType readerHistoryMemoryPolicy;
    @XmlSchemaType(name = "string")
    protected HistoryMemoryPolicyType writerHistoryMemoryPolicy;
    @XmlSchemaType(name = "unsignedInt")
    protected Long readerPayloadSize;
    @XmlSchemaType(name = "unsignedInt")
    protected Long writerPayloadSize;
    @XmlElement(name = "mutation_tries")
    @XmlSchemaType(name = "unsignedInt")
    protected Long mutationTries;
    @XmlElement(name = "flow_controller_name")
    protected String flowControllerName;

    /**
     * Gets the value of the discoveryConfig property.
     * 
     * @return
     *     possible object is
     *     {@link DiscoverySettingsType }
     *     
     */
    public DiscoverySettingsType getDiscoveryConfig() {
        return discoveryConfig;
    }

    /**
     * Sets the value of the discoveryConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link DiscoverySettingsType }
     *     
     */
    public void setDiscoveryConfig(DiscoverySettingsType value) {
        this.discoveryConfig = value;
    }

    /**
     * Gets the value of the avoidBuiltinMulticast property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isAvoidBuiltinMulticast() {
        return avoidBuiltinMulticast;
    }

    /**
     * Sets the value of the avoidBuiltinMulticast property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setAvoidBuiltinMulticast(Boolean value) {
        this.avoidBuiltinMulticast = value;
    }

    /**
     * Gets the value of the useWriterLivelinessProtocol property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isUseWriterLivelinessProtocol() {
        return useWriterLivelinessProtocol;
    }

    /**
     * Sets the value of the useWriterLivelinessProtocol property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setUseWriterLivelinessProtocol(Boolean value) {
        this.useWriterLivelinessProtocol = value;
    }

    /**
     * Gets the value of the metatrafficExternalUnicastLocators property.
     * 
     * @return
     *     possible object is
     *     {@link ExternalLocatorListType }
     *     
     */
    public ExternalLocatorListType getMetatrafficExternalUnicastLocators() {
        return metatrafficExternalUnicastLocators;
    }

    /**
     * Sets the value of the metatrafficExternalUnicastLocators property.
     * 
     * @param value
     *     allowed object is
     *     {@link ExternalLocatorListType }
     *     
     */
    public void setMetatrafficExternalUnicastLocators(ExternalLocatorListType value) {
        this.metatrafficExternalUnicastLocators = value;
    }

    /**
     * Gets the value of the metatrafficUnicastLocatorList property.
     * 
     * @return
     *     possible object is
     *     {@link LocatorListType }
     *     
     */
    public LocatorListType getMetatrafficUnicastLocatorList() {
        return metatrafficUnicastLocatorList;
    }

    /**
     * Sets the value of the metatrafficUnicastLocatorList property.
     * 
     * @param value
     *     allowed object is
     *     {@link LocatorListType }
     *     
     */
    public void setMetatrafficUnicastLocatorList(LocatorListType value) {
        this.metatrafficUnicastLocatorList = value;
    }

    /**
     * Gets the value of the metatrafficMulticastLocatorList property.
     * 
     * @return
     *     possible object is
     *     {@link LocatorListType }
     *     
     */
    public LocatorListType getMetatrafficMulticastLocatorList() {
        return metatrafficMulticastLocatorList;
    }

    /**
     * Sets the value of the metatrafficMulticastLocatorList property.
     * 
     * @param value
     *     allowed object is
     *     {@link LocatorListType }
     *     
     */
    public void setMetatrafficMulticastLocatorList(LocatorListType value) {
        this.metatrafficMulticastLocatorList = value;
    }

    /**
     * Gets the value of the initialPeersList property.
     * 
     * @return
     *     possible object is
     *     {@link LocatorListType }
     *     
     */
    public LocatorListType getInitialPeersList() {
        return initialPeersList;
    }

    /**
     * Sets the value of the initialPeersList property.
     * 
     * @param value
     *     allowed object is
     *     {@link LocatorListType }
     *     
     */
    public void setInitialPeersList(LocatorListType value) {
        this.initialPeersList = value;
    }

    /**
     * Gets the value of the readerHistoryMemoryPolicy property.
     * 
     * @return
     *     possible object is
     *     {@link HistoryMemoryPolicyType }
     *     
     */
    public HistoryMemoryPolicyType getReaderHistoryMemoryPolicy() {
        return readerHistoryMemoryPolicy;
    }

    /**
     * Sets the value of the readerHistoryMemoryPolicy property.
     * 
     * @param value
     *     allowed object is
     *     {@link HistoryMemoryPolicyType }
     *     
     */
    public void setReaderHistoryMemoryPolicy(HistoryMemoryPolicyType value) {
        this.readerHistoryMemoryPolicy = value;
    }

    /**
     * Gets the value of the writerHistoryMemoryPolicy property.
     * 
     * @return
     *     possible object is
     *     {@link HistoryMemoryPolicyType }
     *     
     */
    public HistoryMemoryPolicyType getWriterHistoryMemoryPolicy() {
        return writerHistoryMemoryPolicy;
    }

    /**
     * Sets the value of the writerHistoryMemoryPolicy property.
     * 
     * @param value
     *     allowed object is
     *     {@link HistoryMemoryPolicyType }
     *     
     */
    public void setWriterHistoryMemoryPolicy(HistoryMemoryPolicyType value) {
        this.writerHistoryMemoryPolicy = value;
    }

    /**
     * Gets the value of the readerPayloadSize property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getReaderPayloadSize() {
        return readerPayloadSize;
    }

    /**
     * Sets the value of the readerPayloadSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setReaderPayloadSize(Long value) {
        this.readerPayloadSize = value;
    }

    /**
     * Gets the value of the writerPayloadSize property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getWriterPayloadSize() {
        return writerPayloadSize;
    }

    /**
     * Sets the value of the writerPayloadSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setWriterPayloadSize(Long value) {
        this.writerPayloadSize = value;
    }

    /**
     * Gets the value of the mutationTries property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getMutationTries() {
        return mutationTries;
    }

    /**
     * Sets the value of the mutationTries property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setMutationTries(Long value) {
        this.mutationTries = value;
    }

    /**
     * Gets the value of the flowControllerName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFlowControllerName() {
        return flowControllerName;
    }

    /**
     * Sets the value of the flowControllerName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFlowControllerName(String value) {
        this.flowControllerName = value;
    }

}
