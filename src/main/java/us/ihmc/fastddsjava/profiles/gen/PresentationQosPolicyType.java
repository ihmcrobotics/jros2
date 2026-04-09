
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;




/**
 * <p>Java class for presentationQosPolicyType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="presentationQosPolicyType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="access_scope" minOccurs="0"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *               &lt;enumeration value="INSTANCE"/&gt;
 *               &lt;enumeration value="TOPIC"/&gt;
 *               &lt;enumeration value="GROUP"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="coherent_access" type="{http://www.eprosima.com}boolean" minOccurs="0"/&gt;
 *         &lt;element name="ordered_access" type="{http://www.eprosima.com}boolean" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)

public class PresentationQosPolicyType {

    @JacksonXmlProperty(localName = "access_scope")
    protected String accessScope;
    @JacksonXmlProperty(localName = "coherent_access")
    protected Boolean coherentAccess;
    @JacksonXmlProperty(localName = "ordered_access")
    protected Boolean orderedAccess;

    /**
     * Gets the value of the accessScope property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAccessScope() {
        return accessScope;
    }

    /**
     * Sets the value of the accessScope property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAccessScope(String value) {
        this.accessScope = value;
    }

    /**
     * Gets the value of the coherentAccess property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isCoherentAccess() {
        return coherentAccess;
    }

    /**
     * Sets the value of the coherentAccess property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setCoherentAccess(Boolean value) {
        this.coherentAccess = value;
    }

    /**
     * Gets the value of the orderedAccess property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isOrderedAccess() {
        return orderedAccess;
    }

    /**
     * Sets the value of the orderedAccess property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setOrderedAccess(Boolean value) {
        this.orderedAccess = value;
    }

}
