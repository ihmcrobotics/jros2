
package us.ihmc.fastddsjava.profiles.gen;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlElementRefs;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for durationType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="durationType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence maxOccurs="unbounded">
 *         &lt;choice>
 *           &lt;element name="sec" minOccurs="0">
 *             &lt;simpleType>
 *               &lt;union>
 *                 &lt;simpleType>
 *                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *                     &lt;minLength value="1"/>
 *                     &lt;enumeration value="DURATION_INFINITY"/>
 *                     &lt;enumeration value="DURATION_INFINITE_SEC"/>
 *                   &lt;/restriction>
 *                 &lt;/simpleType>
 *                 &lt;simpleType>
 *                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}unsignedInt">
 *                   &lt;/restriction>
 *                 &lt;/simpleType>
 *               &lt;/union>
 *             &lt;/simpleType>
 *           &lt;/element>
 *           &lt;element name="nanosec" minOccurs="0">
 *             &lt;simpleType>
 *               &lt;union>
 *                 &lt;simpleType>
 *                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *                     &lt;enumeration value="DURATION_INFINITY"/>
 *                     &lt;enumeration value="DURATION_INFINITE_NSEC"/>
 *                   &lt;/restriction>
 *                 &lt;/simpleType>
 *                 &lt;simpleType>
 *                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}unsignedInt">
 *                   &lt;/restriction>
 *                 &lt;/simpleType>
 *               &lt;/union>
 *             &lt;/simpleType>
 *           &lt;/element>
 *         &lt;/choice>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "durationType", propOrder = {
    "secOrNanosec"
})
public class DurationType {

    @XmlElementRefs({
        @XmlElementRef(name = "nanosec", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "sec", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false)
    })
    protected List<JAXBElement<String>> secOrNanosec;

    /**
     * Gets the value of the secOrNanosec property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the secOrNanosec property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSecOrNanosec().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link JAXBElement }{@code <}{@link String }{@code >}
     * {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * 
     */
    public List<JAXBElement<String>> getSecOrNanosec() {
        if (secOrNanosec == null) {
            secOrNanosec = new ArrayList<JAXBElement<String>>();
        }
        return this.secOrNanosec;
    }

}
