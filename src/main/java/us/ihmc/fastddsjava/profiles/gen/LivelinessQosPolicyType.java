
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for livelinessQosPolicyType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="livelinessQosPolicyType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element name="kind" minOccurs="0">
 *           &lt;simpleType>
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *               &lt;enumeration value="AUTOMATIC"/>
 *               &lt;enumeration value="MANUAL_BY_PARTICIPANT"/>
 *               &lt;enumeration value="MANUAL_BY_TOPIC"/>
 *             &lt;/restriction>
 *           &lt;/simpleType>
 *         &lt;/element>
 *         &lt;element name="lease_duration" type="{http://www.eprosima.com}durationType" minOccurs="0"/>
 *         &lt;element name="announcement_period" type="{http://www.eprosima.com}durationType" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "livelinessQosPolicyType", propOrder = {

})
public class LivelinessQosPolicyType {

    protected String kind;
    @XmlElement(name = "lease_duration")
    protected DurationType leaseDuration;
    @XmlElement(name = "announcement_period")
    protected DurationType announcementPeriod;

    /**
     * Gets the value of the kind property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getKind() {
        return kind;
    }

    /**
     * Sets the value of the kind property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setKind(String value) {
        this.kind = value;
    }

    /**
     * Gets the value of the leaseDuration property.
     * 
     * @return
     *     possible object is
     *     {@link DurationType }
     *     
     */
    public DurationType getLeaseDuration() {
        return leaseDuration;
    }

    /**
     * Sets the value of the leaseDuration property.
     * 
     * @param value
     *     allowed object is
     *     {@link DurationType }
     *     
     */
    public void setLeaseDuration(DurationType value) {
        this.leaseDuration = value;
    }

    /**
     * Gets the value of the announcementPeriod property.
     * 
     * @return
     *     possible object is
     *     {@link DurationType }
     *     
     */
    public DurationType getAnnouncementPeriod() {
        return announcementPeriod;
    }

    /**
     * Sets the value of the announcementPeriod property.
     * 
     * @param value
     *     allowed object is
     *     {@link DurationType }
     *     
     */
    public void setAnnouncementPeriod(DurationType value) {
        this.announcementPeriod = value;
    }

}
