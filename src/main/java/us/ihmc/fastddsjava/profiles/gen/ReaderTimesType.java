
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for readerTimesType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="readerTimesType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="initial_acknack_delay" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
 *         &lt;element name="heartbeat_response_delay" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "readerTimesType", propOrder = {

})
public class ReaderTimesType {

    @XmlElement(name = "initial_acknack_delay")
    protected DurationType initialAcknackDelay;
    @XmlElement(name = "heartbeat_response_delay")
    protected DurationType heartbeatResponseDelay;

    /**
     * Gets the value of the initialAcknackDelay property.
     * 
     * @return
     *     possible object is
     *     {@link DurationType }
     *     
     */
    public DurationType getInitialAcknackDelay() {
        return initialAcknackDelay;
    }

    /**
     * Sets the value of the initialAcknackDelay property.
     * 
     * @param value
     *     allowed object is
     *     {@link DurationType }
     *     
     */
    public void setInitialAcknackDelay(DurationType value) {
        this.initialAcknackDelay = value;
    }

    /**
     * Gets the value of the heartbeatResponseDelay property.
     * 
     * @return
     *     possible object is
     *     {@link DurationType }
     *     
     */
    public DurationType getHeartbeatResponseDelay() {
        return heartbeatResponseDelay;
    }

    /**
     * Sets the value of the heartbeatResponseDelay property.
     * 
     * @param value
     *     allowed object is
     *     {@link DurationType }
     *     
     */
    public void setHeartbeatResponseDelay(DurationType value) {
        this.heartbeatResponseDelay = value;
    }

}
