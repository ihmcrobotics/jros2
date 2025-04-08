
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="profiles" type="{http://www.eprosima.com}profilesType" minOccurs="0"/&gt;
 *         &lt;element name="types" type="{http://www.eprosima.com}typesType" minOccurs="0"/&gt;
 *         &lt;element name="log" type="{http://www.eprosima.com}logType" minOccurs="0"/&gt;
 *         &lt;element name="library_settings" type="{http://www.eprosima.com}LibrarySettingsType" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {

})
@XmlRootElement(name = "dds")
public class Dds {

    protected ProfilesType profiles;
    protected TypesType types;
    protected LogType log;
    @XmlElement(name = "library_settings")
    protected LibrarySettingsType librarySettings;

    /**
     * Gets the value of the profiles property.
     * 
     * @return
     *     possible object is
     *     {@link ProfilesType }
     *     
     */
    public ProfilesType getProfiles() {
        return profiles;
    }

    /**
     * Sets the value of the profiles property.
     * 
     * @param value
     *     allowed object is
     *     {@link ProfilesType }
     *     
     */
    public void setProfiles(ProfilesType value) {
        this.profiles = value;
    }

    /**
     * Gets the value of the types property.
     * 
     * @return
     *     possible object is
     *     {@link TypesType }
     *     
     */
    public TypesType getTypes() {
        return types;
    }

    /**
     * Sets the value of the types property.
     * 
     * @param value
     *     allowed object is
     *     {@link TypesType }
     *     
     */
    public void setTypes(TypesType value) {
        this.types = value;
    }

    /**
     * Gets the value of the log property.
     * 
     * @return
     *     possible object is
     *     {@link LogType }
     *     
     */
    public LogType getLog() {
        return log;
    }

    /**
     * Sets the value of the log property.
     * 
     * @param value
     *     allowed object is
     *     {@link LogType }
     *     
     */
    public void setLog(LogType value) {
        this.log = value;
    }

    /**
     * Gets the value of the librarySettings property.
     * 
     * @return
     *     possible object is
     *     {@link LibrarySettingsType }
     *     
     */
    public LibrarySettingsType getLibrarySettings() {
        return librarySettings;
    }

    /**
     * Sets the value of the librarySettings property.
     * 
     * @param value
     *     allowed object is
     *     {@link LibrarySettingsType }
     *     
     */
    public void setLibrarySettings(LibrarySettingsType value) {
        this.librarySettings = value;
    }

}
