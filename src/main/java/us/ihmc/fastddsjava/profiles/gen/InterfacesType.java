
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for interfacesType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="interfacesType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element name="allowlist" type="{http://www.eprosima.com}allowlistType" minOccurs="0"/>
 *         &lt;element name="blocklist" type="{http://www.eprosima.com}blocklistType" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "interfacesType", propOrder = {

})
public class InterfacesType {

    protected AllowlistType allowlist;
    protected BlocklistType blocklist;

    /**
     * Gets the value of the allowlist property.
     * 
     * @return
     *     possible object is
     *     {@link AllowlistType }
     *     
     */
    public AllowlistType getAllowlist() {
        return allowlist;
    }

    /**
     * Sets the value of the allowlist property.
     * 
     * @param value
     *     allowed object is
     *     {@link AllowlistType }
     *     
     */
    public void setAllowlist(AllowlistType value) {
        this.allowlist = value;
    }

    /**
     * Gets the value of the blocklist property.
     * 
     * @return
     *     possible object is
     *     {@link BlocklistType }
     *     
     */
    public BlocklistType getBlocklist() {
        return blocklist;
    }

    /**
     * Sets the value of the blocklist property.
     * 
     * @param value
     *     allowed object is
     *     {@link BlocklistType }
     *     
     */
    public void setBlocklist(BlocklistType value) {
        this.blocklist = value;
    }

}
