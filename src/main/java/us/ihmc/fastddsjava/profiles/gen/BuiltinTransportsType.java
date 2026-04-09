
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;




/**
 * <p>Java class for builtinTransportsType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="builtinTransportsType"&gt;
 *   &lt;simpleContent&gt;
 *     &lt;extension base="&lt;http://www.eprosima.com&gt;builtinTransportKind"&gt;
 *       &lt;attribute name="max_msg_size" type="{http://www.eprosima.com}string" /&gt;
 *       &lt;attribute name="sockets_size" type="{http://www.eprosima.com}string" /&gt;
 *       &lt;attribute name="non_blocking" type="{http://www.eprosima.com}boolean" /&gt;
 *       &lt;attribute name="tcp_negotiation_timeout" type="{http://www.eprosima.com}uint32" /&gt;
 *     &lt;/extension&gt;
 *   &lt;/simpleContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)

public class BuiltinTransportsType {

    @JacksonXmlText
    protected BuiltinTransportKind value;
    @JacksonXmlProperty(isAttribute = true, localName = "max_msg_size")
    protected String maxMsgSize;
    @JacksonXmlProperty(isAttribute = true, localName = "sockets_size")
    protected String socketsSize;
    @JacksonXmlProperty(isAttribute = true, localName = "non_blocking")
    protected Boolean nonBlocking;
    @JacksonXmlProperty(isAttribute = true, localName = "tcp_negotiation_timeout")
    protected Long tcpNegotiationTimeout;

    /**
     * Gets the value of the value property.
     * 
     * @return
     *     possible object is
     *     {@link BuiltinTransportKind }
     *     
     */
    public BuiltinTransportKind getValue() {
        return value;
    }

    /**
     * Sets the value of the value property.
     * 
     * @param value
     *     allowed object is
     *     {@link BuiltinTransportKind }
     *     
     */
    public void setValue(BuiltinTransportKind value) {
        this.value = value;
    }

    /**
     * Gets the value of the maxMsgSize property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMaxMsgSize() {
        return maxMsgSize;
    }

    /**
     * Sets the value of the maxMsgSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMaxMsgSize(String value) {
        this.maxMsgSize = value;
    }

    /**
     * Gets the value of the socketsSize property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSocketsSize() {
        return socketsSize;
    }

    /**
     * Sets the value of the socketsSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSocketsSize(String value) {
        this.socketsSize = value;
    }

    /**
     * Gets the value of the nonBlocking property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isNonBlocking() {
        return nonBlocking;
    }

    /**
     * Sets the value of the nonBlocking property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setNonBlocking(Boolean value) {
        this.nonBlocking = value;
    }

    /**
     * Gets the value of the tcpNegotiationTimeout property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getTcpNegotiationTimeout() {
        return tcpNegotiationTimeout;
    }

    /**
     * Sets the value of the tcpNegotiationTimeout property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setTcpNegotiationTimeout(Long value) {
        this.tcpNegotiationTimeout = value;
    }

}
