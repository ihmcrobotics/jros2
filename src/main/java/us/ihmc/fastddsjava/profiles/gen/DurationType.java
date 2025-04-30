
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
 * &lt;complexType name="durationType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence maxOccurs="unbounded"&gt;
 *         &lt;choice&gt;
 *           &lt;element name="sec" minOccurs="0"&gt;
 *             &lt;simpleType&gt;
 *               &lt;union&gt;
 *                 &lt;simpleType&gt;
 *                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *                     &lt;minLength value="1"/&gt;
 *                     &lt;enumeration value="DURATION_INFINITY"/&gt;
 *                     &lt;enumeration value="DURATION_INFINITE_SEC"/&gt;
 *                   &lt;/restriction&gt;
 *                 &lt;/simpleType&gt;
 *                 &lt;simpleType&gt;
 *                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}unsignedInt"&gt;
 *                   &lt;/restriction&gt;
 *                 &lt;/simpleType&gt;
 *               &lt;/union&gt;
 *             &lt;/simpleType&gt;
 *           &lt;/element&gt;
 *           &lt;element name="nanosec" minOccurs="0"&gt;
 *             &lt;simpleType&gt;
 *               &lt;union&gt;
 *                 &lt;simpleType&gt;
 *                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *                     &lt;enumeration value="DURATION_INFINITY"/&gt;
 *                     &lt;enumeration value="DURATION_INFINITE_NSEC"/&gt;
 *                   &lt;/restriction&gt;
 *                 &lt;/simpleType&gt;
 *                 &lt;simpleType&gt;
 *                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}unsignedInt"&gt;
 *                   &lt;/restriction&gt;
 *                 &lt;/simpleType&gt;
 *               &lt;/union&gt;
 *             &lt;/simpleType&gt;
 *           &lt;/element&gt;
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
@XmlType(name = "durationType", propOrder = {
    "secOrNanosec"
})
public class DurationType {

    @XmlElementRefs({
        @XmlElementRef(name = "sec", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "nanosec", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false)
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
