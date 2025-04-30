
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for replierRequesterProfileType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="replierRequesterProfileType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all minOccurs="0"&gt;
 *         &lt;element name="request_topic_name" type="{http://www.eprosima.com}string" minOccurs="0"/&gt;
 *         &lt;element name="reply_topic_name" type="{http://www.eprosima.com}string" minOccurs="0"/&gt;
 *         &lt;element name="data_writer" type="{http://www.eprosima.com}publisherProfileNoAttributesType" minOccurs="0"/&gt;
 *         &lt;element name="data_reader" type="{http://www.eprosima.com}subscriberProfileNoAttributesType" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *       &lt;attribute name="profile_name" use="required" type="{http://www.eprosima.com}string" /&gt;
 *       &lt;attribute name="service_name" use="required" type="{http://www.eprosima.com}string" /&gt;
 *       &lt;attribute name="request_type" use="required" type="{http://www.eprosima.com}string" /&gt;
 *       &lt;attribute name="reply_type" use="required" type="{http://www.eprosima.com}string" /&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "replierRequesterProfileType", propOrder = {

})
public class ReplierRequesterProfileType {

    @XmlElement(name = "request_topic_name")
    protected String requestTopicName;
    @XmlElement(name = "reply_topic_name")
    protected String replyTopicName;
    @XmlElement(name = "data_writer")
    protected PublisherProfileNoAttributesType dataWriter;
    @XmlElement(name = "data_reader")
    protected SubscriberProfileNoAttributesType dataReader;
    @XmlAttribute(name = "profile_name", required = true)
    protected String profileName;
    @XmlAttribute(name = "service_name", required = true)
    protected String serviceName;
    @XmlAttribute(name = "request_type", required = true)
    protected String requestType;
    @XmlAttribute(name = "reply_type", required = true)
    protected String replyType;

    /**
     * Gets the value of the requestTopicName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRequestTopicName() {
        return requestTopicName;
    }

    /**
     * Sets the value of the requestTopicName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRequestTopicName(String value) {
        this.requestTopicName = value;
    }

    /**
     * Gets the value of the replyTopicName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getReplyTopicName() {
        return replyTopicName;
    }

    /**
     * Sets the value of the replyTopicName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setReplyTopicName(String value) {
        this.replyTopicName = value;
    }

    /**
     * Gets the value of the dataWriter property.
     * 
     * @return
     *     possible object is
     *     {@link PublisherProfileNoAttributesType }
     *     
     */
    public PublisherProfileNoAttributesType getDataWriter() {
        return dataWriter;
    }

    /**
     * Sets the value of the dataWriter property.
     * 
     * @param value
     *     allowed object is
     *     {@link PublisherProfileNoAttributesType }
     *     
     */
    public void setDataWriter(PublisherProfileNoAttributesType value) {
        this.dataWriter = value;
    }

    /**
     * Gets the value of the dataReader property.
     * 
     * @return
     *     possible object is
     *     {@link SubscriberProfileNoAttributesType }
     *     
     */
    public SubscriberProfileNoAttributesType getDataReader() {
        return dataReader;
    }

    /**
     * Sets the value of the dataReader property.
     * 
     * @param value
     *     allowed object is
     *     {@link SubscriberProfileNoAttributesType }
     *     
     */
    public void setDataReader(SubscriberProfileNoAttributesType value) {
        this.dataReader = value;
    }

    /**
     * Gets the value of the profileName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * Sets the value of the profileName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProfileName(String value) {
        this.profileName = value;
    }

    /**
     * Gets the value of the serviceName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Sets the value of the serviceName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setServiceName(String value) {
        this.serviceName = value;
    }

    /**
     * Gets the value of the requestType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRequestType() {
        return requestType;
    }

    /**
     * Sets the value of the requestType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRequestType(String value) {
        this.requestType = value;
    }

    /**
     * Gets the value of the replyType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getReplyType() {
        return replyType;
    }

    /**
     * Sets the value of the replyType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setReplyType(String value) {
        this.replyType = value;
    }

}
