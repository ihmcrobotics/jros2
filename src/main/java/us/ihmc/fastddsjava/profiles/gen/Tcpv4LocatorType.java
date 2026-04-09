
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;




/**
 * <p>Java class for tcpv4LocatorType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="tcpv4LocatorType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="port" type="{http://www.eprosima.com}uint16" minOccurs="0"/&gt;
 *         &lt;element name="physical_port" type="{http://www.eprosima.com}uint16" minOccurs="0"/&gt;
 *         &lt;element name="address" type="{http://www.eprosima.com}ipv4Address" minOccurs="0"/&gt;
 *         &lt;element name="unique_lan_id" type="{http://www.eprosima.com}string" minOccurs="0"/&gt;
 *         &lt;element name="wan_address" type="{http://www.eprosima.com}ipv4AddressFormat" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)

public class Tcpv4LocatorType {

        protected Integer port;
    @JacksonXmlProperty(localName = "physical_port")
        protected Integer physicalPort;
    protected String address;
    @JacksonXmlProperty(localName = "unique_lan_id")
    protected String uniqueLanId;
    @JacksonXmlProperty(localName = "wan_address")
    protected String wanAddress;

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

    /**
     * Gets the value of the uniqueLanId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUniqueLanId() {
        return uniqueLanId;
    }

    /**
     * Sets the value of the uniqueLanId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUniqueLanId(String value) {
        this.uniqueLanId = value;
    }

    /**
     * Gets the value of the wanAddress property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getWanAddress() {
        return wanAddress;
    }

    /**
     * Sets the value of the wanAddress property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setWanAddress(String value) {
        this.wanAddress = value;
    }

}
