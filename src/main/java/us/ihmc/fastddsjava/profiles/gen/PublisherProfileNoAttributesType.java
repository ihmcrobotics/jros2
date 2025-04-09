
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for publisherProfileNoAttributesType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="publisherProfileNoAttributesType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="topic" type="{http://www.eprosima.com}topicElementType" minOccurs="0"/&gt;
 *         &lt;element name="qos" type="{http://www.eprosima.com}dataWriterQosPoliciesType" minOccurs="0"/&gt;
 *         &lt;element name="times" type="{http://www.eprosima.com}writerTimesType" minOccurs="0"/&gt;
 *         &lt;element name="unicastLocatorList" type="{http://www.eprosima.com}locatorListType" minOccurs="0"/&gt;
 *         &lt;element name="multicastLocatorList" type="{http://www.eprosima.com}locatorListType" minOccurs="0"/&gt;
 *         &lt;element name="external_unicast_locators" type="{http://www.eprosima.com}externalLocatorListType" minOccurs="0"/&gt;
 *         &lt;element name="ignore_non_matching_locators" type="{http://www.eprosima.com}boolean" minOccurs="0"/&gt;
 *         &lt;element name="historyMemoryPolicy" type="{http://www.eprosima.com}historyMemoryPolicyType" minOccurs="0"/&gt;
 *         &lt;element name="propertiesPolicy" type="{http://www.eprosima.com}propertyPolicyType" minOccurs="0"/&gt;
 *         &lt;element name="userDefinedID" type="{http://www.eprosima.com}int16" minOccurs="0"/&gt;
 *         &lt;element name="entityID" type="{http://www.eprosima.com}int16" minOccurs="0"/&gt;
 *         &lt;element name="matchedSubscribersAllocation" type="{http://www.eprosima.com}allocationConfigType" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "publisherProfileNoAttributesType", propOrder = {

})
@XmlSeeAlso({
    PublisherProfileType.class
})
public class PublisherProfileNoAttributesType {

    protected TopicElementType topic;
    protected DataWriterQosPoliciesType qos;
    protected WriterTimesType times;
    protected LocatorListType unicastLocatorList;
    protected LocatorListType multicastLocatorList;
    @XmlElement(name = "external_unicast_locators")
    protected ExternalLocatorListType externalUnicastLocators;
    @XmlElement(name = "ignore_non_matching_locators")
    protected Boolean ignoreNonMatchingLocators;
    @XmlSchemaType(name = "string")
    protected HistoryMemoryPolicyType historyMemoryPolicy;
    protected PropertyPolicyType propertiesPolicy;
    protected Short userDefinedID;
    protected Short entityID;
    protected AllocationConfigType matchedSubscribersAllocation;

    /**
     * Gets the value of the topic property.
     * 
     * @return
     *     possible object is
     *     {@link TopicElementType }
     *     
     */
    public TopicElementType getTopic() {
        return topic;
    }

    /**
     * Sets the value of the topic property.
     * 
     * @param value
     *     allowed object is
     *     {@link TopicElementType }
     *     
     */
    public void setTopic(TopicElementType value) {
        this.topic = value;
    }

    /**
     * Gets the value of the qos property.
     * 
     * @return
     *     possible object is
     *     {@link DataWriterQosPoliciesType }
     *     
     */
    public DataWriterQosPoliciesType getQos() {
        return qos;
    }

    /**
     * Sets the value of the qos property.
     * 
     * @param value
     *     allowed object is
     *     {@link DataWriterQosPoliciesType }
     *     
     */
    public void setQos(DataWriterQosPoliciesType value) {
        this.qos = value;
    }

    /**
     * Gets the value of the times property.
     * 
     * @return
     *     possible object is
     *     {@link WriterTimesType }
     *     
     */
    public WriterTimesType getTimes() {
        return times;
    }

    /**
     * Sets the value of the times property.
     * 
     * @param value
     *     allowed object is
     *     {@link WriterTimesType }
     *     
     */
    public void setTimes(WriterTimesType value) {
        this.times = value;
    }

    /**
     * Gets the value of the unicastLocatorList property.
     * 
     * @return
     *     possible object is
     *     {@link LocatorListType }
     *     
     */
    public LocatorListType getUnicastLocatorList() {
        return unicastLocatorList;
    }

    /**
     * Sets the value of the unicastLocatorList property.
     * 
     * @param value
     *     allowed object is
     *     {@link LocatorListType }
     *     
     */
    public void setUnicastLocatorList(LocatorListType value) {
        this.unicastLocatorList = value;
    }

    /**
     * Gets the value of the multicastLocatorList property.
     * 
     * @return
     *     possible object is
     *     {@link LocatorListType }
     *     
     */
    public LocatorListType getMulticastLocatorList() {
        return multicastLocatorList;
    }

    /**
     * Sets the value of the multicastLocatorList property.
     * 
     * @param value
     *     allowed object is
     *     {@link LocatorListType }
     *     
     */
    public void setMulticastLocatorList(LocatorListType value) {
        this.multicastLocatorList = value;
    }

    /**
     * Gets the value of the externalUnicastLocators property.
     * 
     * @return
     *     possible object is
     *     {@link ExternalLocatorListType }
     *     
     */
    public ExternalLocatorListType getExternalUnicastLocators() {
        return externalUnicastLocators;
    }

    /**
     * Sets the value of the externalUnicastLocators property.
     * 
     * @param value
     *     allowed object is
     *     {@link ExternalLocatorListType }
     *     
     */
    public void setExternalUnicastLocators(ExternalLocatorListType value) {
        this.externalUnicastLocators = value;
    }

    /**
     * Gets the value of the ignoreNonMatchingLocators property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isIgnoreNonMatchingLocators() {
        return ignoreNonMatchingLocators;
    }

    /**
     * Sets the value of the ignoreNonMatchingLocators property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setIgnoreNonMatchingLocators(Boolean value) {
        this.ignoreNonMatchingLocators = value;
    }

    /**
     * Gets the value of the historyMemoryPolicy property.
     * 
     * @return
     *     possible object is
     *     {@link HistoryMemoryPolicyType }
     *     
     */
    public HistoryMemoryPolicyType getHistoryMemoryPolicy() {
        return historyMemoryPolicy;
    }

    /**
     * Sets the value of the historyMemoryPolicy property.
     * 
     * @param value
     *     allowed object is
     *     {@link HistoryMemoryPolicyType }
     *     
     */
    public void setHistoryMemoryPolicy(HistoryMemoryPolicyType value) {
        this.historyMemoryPolicy = value;
    }

    /**
     * Gets the value of the propertiesPolicy property.
     * 
     * @return
     *     possible object is
     *     {@link PropertyPolicyType }
     *     
     */
    public PropertyPolicyType getPropertiesPolicy() {
        return propertiesPolicy;
    }

    /**
     * Sets the value of the propertiesPolicy property.
     * 
     * @param value
     *     allowed object is
     *     {@link PropertyPolicyType }
     *     
     */
    public void setPropertiesPolicy(PropertyPolicyType value) {
        this.propertiesPolicy = value;
    }

    /**
     * Gets the value of the userDefinedID property.
     * 
     * @return
     *     possible object is
     *     {@link Short }
     *     
     */
    public Short getUserDefinedID() {
        return userDefinedID;
    }

    /**
     * Sets the value of the userDefinedID property.
     * 
     * @param value
     *     allowed object is
     *     {@link Short }
     *     
     */
    public void setUserDefinedID(Short value) {
        this.userDefinedID = value;
    }

    /**
     * Gets the value of the entityID property.
     * 
     * @return
     *     possible object is
     *     {@link Short }
     *     
     */
    public Short getEntityID() {
        return entityID;
    }

    /**
     * Sets the value of the entityID property.
     * 
     * @param value
     *     allowed object is
     *     {@link Short }
     *     
     */
    public void setEntityID(Short value) {
        this.entityID = value;
    }

    /**
     * Gets the value of the matchedSubscribersAllocation property.
     * 
     * @return
     *     possible object is
     *     {@link AllocationConfigType }
     *     
     */
    public AllocationConfigType getMatchedSubscribersAllocation() {
        return matchedSubscribersAllocation;
    }

    /**
     * Sets the value of the matchedSubscribersAllocation property.
     * 
     * @param value
     *     allowed object is
     *     {@link AllocationConfigType }
     *     
     */
    public void setMatchedSubscribersAllocation(AllocationConfigType value) {
        this.matchedSubscribersAllocation = value;
    }

}
