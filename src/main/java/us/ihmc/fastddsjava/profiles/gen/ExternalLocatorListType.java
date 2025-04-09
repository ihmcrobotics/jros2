
package us.ihmc.fastddsjava.profiles.gen;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for externalLocatorListType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="externalLocatorListType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence maxOccurs="unbounded"&gt;
 *         &lt;choice&gt;
 *           &lt;element name="udpv4" type="{http://www.eprosima.com}udpv4ExternalLocatorType" maxOccurs="unbounded"/&gt;
 *           &lt;element name="udpv6" type="{http://www.eprosima.com}udpv6ExternalLocatorType" maxOccurs="unbounded"/&gt;
 *         &lt;/choice&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "externalLocatorListType", propOrder = {
    "udpv4OrUdpv6"
})
public class ExternalLocatorListType {

    @XmlElements({
        @XmlElement(name = "udpv4", type = Udpv4ExternalLocatorType.class),
        @XmlElement(name = "udpv6", type = Udpv6ExternalLocatorType.class)
    })
    protected List<Object> udpv4OrUdpv6;

    /**
     * Gets the value of the udpv4OrUdpv6 property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the udpv4OrUdpv6 property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getUdpv4OrUdpv6().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Udpv4ExternalLocatorType }
     * {@link Udpv6ExternalLocatorType }
     * 
     * 
     */
    public List<Object> getUdpv4OrUdpv6() {
        if (udpv4OrUdpv6 == null) {
            udpv4OrUdpv6 = new ArrayList<Object>();
        }
        return this.udpv4OrUdpv6;
    }

}
