
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;




/**
 * <p>Java class for livelinessQosPolicyType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="livelinessQosPolicyType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="kind" minOccurs="0"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *               &lt;enumeration value="AUTOMATIC"/&gt;
 *               &lt;enumeration value="MANUAL_BY_PARTICIPANT"/&gt;
 *               &lt;enumeration value="MANUAL_BY_TOPIC"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="lease_duration" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
 *         &lt;element name="announcement_period" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)

public class LivelinessQosPolicyType {

    protected String kind;
    @JacksonXmlProperty(localName = "lease_duration")
    protected DurationType leaseDuration;
    @JacksonXmlProperty(localName = "announcement_period")
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
