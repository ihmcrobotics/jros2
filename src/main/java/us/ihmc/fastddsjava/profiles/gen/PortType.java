
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for portType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="portType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element name="portBase" type="{http://www.eprosima.com}uint16" minOccurs="0"/>
 *         &lt;element name="domainIDGain" type="{http://www.eprosima.com}uint16" minOccurs="0"/>
 *         &lt;element name="participantIDGain" type="{http://www.eprosima.com}uint16" minOccurs="0"/>
 *         &lt;element name="offsetd0" type="{http://www.eprosima.com}uint16" minOccurs="0"/>
 *         &lt;element name="offsetd1" type="{http://www.eprosima.com}uint16" minOccurs="0"/>
 *         &lt;element name="offsetd2" type="{http://www.eprosima.com}uint16" minOccurs="0"/>
 *         &lt;element name="offsetd3" type="{http://www.eprosima.com}uint16" minOccurs="0"/>
 *         &lt;element name="offsetd4" type="{http://www.eprosima.com}uint16" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "portType", propOrder = {

})
public class PortType {

    @XmlSchemaType(name = "unsignedShort")
    protected Integer portBase;
    @XmlSchemaType(name = "unsignedShort")
    protected Integer domainIDGain;
    @XmlSchemaType(name = "unsignedShort")
    protected Integer participantIDGain;
    @XmlSchemaType(name = "unsignedShort")
    protected Integer offsetd0;
    @XmlSchemaType(name = "unsignedShort")
    protected Integer offsetd1;
    @XmlSchemaType(name = "unsignedShort")
    protected Integer offsetd2;
    @XmlSchemaType(name = "unsignedShort")
    protected Integer offsetd3;
    @XmlSchemaType(name = "unsignedShort")
    protected Integer offsetd4;

    /**
     * Gets the value of the portBase property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getPortBase() {
        return portBase;
    }

    /**
     * Sets the value of the portBase property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setPortBase(Integer value) {
        this.portBase = value;
    }

    /**
     * Gets the value of the domainIDGain property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getDomainIDGain() {
        return domainIDGain;
    }

    /**
     * Sets the value of the domainIDGain property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setDomainIDGain(Integer value) {
        this.domainIDGain = value;
    }

    /**
     * Gets the value of the participantIDGain property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getParticipantIDGain() {
        return participantIDGain;
    }

    /**
     * Sets the value of the participantIDGain property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setParticipantIDGain(Integer value) {
        this.participantIDGain = value;
    }

    /**
     * Gets the value of the offsetd0 property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getOffsetd0() {
        return offsetd0;
    }

    /**
     * Sets the value of the offsetd0 property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setOffsetd0(Integer value) {
        this.offsetd0 = value;
    }

    /**
     * Gets the value of the offsetd1 property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getOffsetd1() {
        return offsetd1;
    }

    /**
     * Sets the value of the offsetd1 property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setOffsetd1(Integer value) {
        this.offsetd1 = value;
    }

    /**
     * Gets the value of the offsetd2 property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getOffsetd2() {
        return offsetd2;
    }

    /**
     * Sets the value of the offsetd2 property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setOffsetd2(Integer value) {
        this.offsetd2 = value;
    }

    /**
     * Gets the value of the offsetd3 property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getOffsetd3() {
        return offsetd3;
    }

    /**
     * Sets the value of the offsetd3 property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setOffsetd3(Integer value) {
        this.offsetd3 = value;
    }

    /**
     * Gets the value of the offsetd4 property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getOffsetd4() {
        return offsetd4;
    }

    /**
     * Sets the value of the offsetd4 property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setOffsetd4(Integer value) {
        this.offsetd4 = value;
    }

}
