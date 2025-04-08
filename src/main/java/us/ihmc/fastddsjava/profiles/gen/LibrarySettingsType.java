
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
 * &lt;complexType name="LibrarySettingsType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all minOccurs="0">
 *         &lt;element name="intraprocess_delivery" minOccurs="0">
 *           &lt;simpleType>
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *               &lt;enumeration value="OFF"/>
 *               &lt;enumeration value="USER_DATA_ONLY"/>
 *               &lt;enumeration value="FULL"/>
 *               &lt;enumeration value="off"/>
 *               &lt;enumeration value="user_data_only"/>
 *               &lt;enumeration value="full"/>
 *             &lt;/restriction>
 *           &lt;/simpleType>
 *         &lt;/element>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
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
