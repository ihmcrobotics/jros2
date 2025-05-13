
package us.ihmc.fastddsjava.profiles.gen;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for receptionThreadsListType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="receptionThreadsListType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence maxOccurs="unbounded" minOccurs="0"&gt;
 *         &lt;element name="reception_thread" type="{http://www.eprosima.com}threadSettingsWithPortType" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "receptionThreadsListType", propOrder = {
    "receptionThread"
})
public class ReceptionThreadsListType {

    @XmlElement(name = "reception_thread")
    protected List<ThreadSettingsWithPortType> receptionThread;

    /**
     * Gets the value of the receptionThread property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the receptionThread property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getReceptionThread().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ThreadSettingsWithPortType }
     * 
     * 
     */
    public List<ThreadSettingsWithPortType> getReceptionThread() {
        if (receptionThread == null) {
            receptionThread = new ArrayList<ThreadSettingsWithPortType>();
        }
        return this.receptionThread;
    }

}
