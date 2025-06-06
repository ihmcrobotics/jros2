
package us.ihmc.fastddsjava.profiles.gen;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for logConsumerType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="logConsumerType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence maxOccurs="unbounded"&gt;
 *         &lt;choice&gt;
 *           &lt;element name="class" minOccurs="0"&gt;
 *             &lt;simpleType&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *                 &lt;enumeration value="StdoutConsumer"/&gt;
 *                 &lt;enumeration value="StdoutErrConsumer"/&gt;
 *                 &lt;enumeration value="FileConsumer"/&gt;
 *               &lt;/restriction&gt;
 *             &lt;/simpleType&gt;
 *           &lt;/element&gt;
 *           &lt;element name="property" type="{http://www.eprosima.com}logConsumerPropertyType" maxOccurs="unbounded" minOccurs="0"/&gt;
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
@XmlType(name = "logConsumerType", propOrder = {
    "clazzOrProperty"
})
public class LogConsumerType {

    @XmlElements({
        @XmlElement(name = "class", type = String.class),
        @XmlElement(name = "property", type = LogConsumerPropertyType.class)
    })
    protected List<Object> clazzOrProperty;

    /**
     * Gets the value of the clazzOrProperty property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the clazzOrProperty property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getClazzOrProperty().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * {@link LogConsumerPropertyType }
     * 
     * 
     */
    public List<Object> getClazzOrProperty() {
        if (clazzOrProperty == null) {
            clazzOrProperty = new ArrayList<Object>();
        }
        return this.clazzOrProperty;
    }

}
