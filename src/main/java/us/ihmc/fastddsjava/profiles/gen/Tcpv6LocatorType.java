
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for tcpv6LocatorType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="tcpv6LocatorType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="port" type="{http://www.eprosima.com}uint16" minOccurs="0"/&gt;
 *         &lt;element name="physical_port" type="{http://www.eprosima.com}uint16" minOccurs="0"/&gt;
 *         &lt;element name="address" type="{http://www.eprosima.com}ipv6Address" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "tcpv6LocatorType", propOrder = {

})
public class Tcpv6LocatorType {

    @XmlSchemaType(name = "unsignedShort")
    protected Integer port;
    @XmlElement(name = "physical_port")
    @XmlSchemaType(name = "unsignedShort")
    protected Integer physicalPort;
    protected String address;

    /**
     * Gets the value of the port property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getPort() {
        return port;
    }

    /**
     * Sets the value of the port property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setPort(Integer value) {
        this.port = value;
    }

    /**
     * Gets the value of the physicalPort property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getPhysicalPort() {
        return physicalPort;
    }

    /**
     * Sets the value of the physicalPort property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setPhysicalPort(Integer value) {
        this.physicalPort = value;
    }

    /**
     * Gets the value of the address property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAddress() {
        return address;
    }

    /**
     * Sets the value of the address property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAddress(String value) {
        this.address = value;
    }

}
