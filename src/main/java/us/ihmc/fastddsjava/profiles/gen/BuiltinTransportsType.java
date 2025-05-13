
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlValue;


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
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "builtinTransportsType", propOrder = {
    "value"
})
public class BuiltinTransportsType {

    @XmlValue
    protected BuiltinTransportKind value;
    @XmlAttribute(name = "max_msg_size")
    protected String maxMsgSize;
    @XmlAttribute(name = "sockets_size")
    protected String socketsSize;
    @XmlAttribute(name = "non_blocking")
    protected Boolean nonBlocking;
    @XmlAttribute(name = "tcp_negotiation_timeout")
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
