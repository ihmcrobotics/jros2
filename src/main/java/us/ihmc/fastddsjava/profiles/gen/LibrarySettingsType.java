
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LibrarySettingsType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="LibrarySettingsType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all minOccurs="0"&gt;
 *         &lt;element name="intraprocess_delivery" minOccurs="0"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *               &lt;enumeration value="OFF"/&gt;
 *               &lt;enumeration value="USER_DATA_ONLY"/&gt;
 *               &lt;enumeration value="FULL"/&gt;
 *               &lt;enumeration value="off"/&gt;
 *               &lt;enumeration value="user_data_only"/&gt;
 *               &lt;enumeration value="full"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "LibrarySettingsType", propOrder = {

})
public class LibrarySettingsType {

    @XmlElement(name = "intraprocess_delivery")
    protected String intraprocessDelivery;

    /**
     * Gets the value of the intraprocessDelivery property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getIntraprocessDelivery() {
        return intraprocessDelivery;
    }

    /**
     * Sets the value of the intraprocessDelivery property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setIntraprocessDelivery(String value) {
        this.intraprocessDelivery = value;
    }

}
