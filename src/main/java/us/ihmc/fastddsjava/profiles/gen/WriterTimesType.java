
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for writerTimesType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="writerTimesType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="initial_heartbeat_delay" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
 *         &lt;element name="heartbeat_period" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
 *         &lt;element name="nack_response_delay" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
 *         &lt;element name="nack_supression_duration" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "writerTimesType", propOrder = {

})
public class WriterTimesType {

    @XmlElement(name = "initial_heartbeat_delay")
    protected DurationType initialHeartbeatDelay;
    @XmlElement(name = "heartbeat_period")
    protected DurationType heartbeatPeriod;
    @XmlElement(name = "nack_response_delay")
    protected DurationType nackResponseDelay;
    @XmlElement(name = "nack_supression_duration")
    protected DurationType nackSupressionDuration;

    /**
     * Gets the value of the initialHeartbeatDelay property.
     * 
     * @return
     *     possible object is
     *     {@link DurationType }
     *     
     */
    public DurationType getInitialHeartbeatDelay() {
        return initialHeartbeatDelay;
    }

    /**
     * Sets the value of the initialHeartbeatDelay property.
     * 
     * @param value
     *     allowed object is
     *     {@link DurationType }
     *     
     */
    public void setInitialHeartbeatDelay(DurationType value) {
        this.initialHeartbeatDelay = value;
    }

    /**
     * Gets the value of the heartbeatPeriod property.
     * 
     * @return
     *     possible object is
     *     {@link DurationType }
     *     
     */
    public DurationType getHeartbeatPeriod() {
        return heartbeatPeriod;
    }

    /**
     * Sets the value of the heartbeatPeriod property.
     * 
     * @param value
     *     allowed object is
     *     {@link DurationType }
     *     
     */
    public void setHeartbeatPeriod(DurationType value) {
        this.heartbeatPeriod = value;
    }

    /**
     * Gets the value of the nackResponseDelay property.
     * 
     * @return
     *     possible object is
     *     {@link DurationType }
     *     
     */
    public DurationType getNackResponseDelay() {
        return nackResponseDelay;
    }

    /**
     * Sets the value of the nackResponseDelay property.
     * 
     * @param value
     *     allowed object is
     *     {@link DurationType }
     *     
     */
    public void setNackResponseDelay(DurationType value) {
        this.nackResponseDelay = value;
    }

    /**
     * Gets the value of the nackSupressionDuration property.
     * 
     * @return
     *     possible object is
     *     {@link DurationType }
     *     
     */
    public DurationType getNackSupressionDuration() {
        return nackSupressionDuration;
    }

    /**
     * Sets the value of the nackSupressionDuration property.
     * 
     * @param value
     *     allowed object is
     *     {@link DurationType }
     *     
     */
    public void setNackSupressionDuration(DurationType value) {
        this.nackSupressionDuration = value;
    }

}
