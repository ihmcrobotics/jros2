
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;




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
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)

public class ReplierRequesterProfileType {

    @JacksonXmlProperty(localName = "request_topic_name")
    protected String requestTopicName;
    @JacksonXmlProperty(localName = "reply_topic_name")
    protected String replyTopicName;
    @JacksonXmlProperty(localName = "data_writer")
    protected PublisherProfileNoAttributesType dataWriter;
    @JacksonXmlProperty(localName = "data_reader")
    protected SubscriberProfileNoAttributesType dataReader;
    @JacksonXmlProperty(isAttribute = true, localName = "profile_name")
    protected String profileName;
    @JacksonXmlProperty(isAttribute = true, localName = "service_name")
    protected String serviceName;
    @JacksonXmlProperty(isAttribute = true, localName = "request_type")
    protected String requestType;
    @JacksonXmlProperty(isAttribute = true, localName = "reply_type")
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
