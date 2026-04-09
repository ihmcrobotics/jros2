
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;




/**
 * <p>Java class for domainParticipantFactoryProfileType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="domainParticipantFactoryProfileType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="qos" type="{http://www.eprosima.com}domainParticipantFactoryQosPoliciesType" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *       &lt;attribute name="profile_name" use="required" type="{http://www.eprosima.com}string" /&gt;
 *       &lt;attribute name="is_default_profile" type="{http://www.eprosima.com}boolean" /&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)

public class DomainParticipantFactoryProfileType {

    protected DomainParticipantFactoryQosPoliciesType qos;
    @JacksonXmlProperty(isAttribute = true, localName = "profile_name")
    protected String profileName;
    @JacksonXmlProperty(isAttribute = true, localName = "is_default_profile")
    protected Boolean isDefaultProfile;

    /**
     * Gets the value of the qos property.
     * 
     * @return
     *     possible object is
     *     {@link DomainParticipantFactoryQosPoliciesType }
     *     
     */
    public DomainParticipantFactoryQosPoliciesType getQos() {
        return qos;
    }

    /**
     * Sets the value of the qos property.
     * 
     * @param value
     *     allowed object is
     *     {@link DomainParticipantFactoryQosPoliciesType }
     *     
     */
    public void setQos(DomainParticipantFactoryQosPoliciesType value) {
        this.qos = value;
    }

    /**
     * Gets the value of the profileName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * Sets the value of the profileName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProfileName(String value) {
        this.profileName = value;
    }

    /**
     * Gets the value of the isDefaultProfile property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isIsDefaultProfile() {
        return isDefaultProfile;
    }

    /**
     * Sets the value of the isDefaultProfile property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setIsDefaultProfile(Boolean value) {
        this.isDefaultProfile = value;
    }

}
