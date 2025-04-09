
package us.ihmc.fastddsjava.profiles.gen;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for flowControllerDescriptorListType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="flowControllerDescriptorListType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence maxOccurs="unbounded">
 *         &lt;element name="flow_controller_descriptor" type="{http://www.eprosima.com}flowControllerDescriptorType"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "flowControllerDescriptorListType", propOrder = {
    "flowControllerDescriptor"
})
public class FlowControllerDescriptorListType {

    @XmlElement(name = "flow_controller_descriptor", required = true)
    protected List<FlowControllerDescriptorType> flowControllerDescriptor;

    /**
     * Gets the value of the flowControllerDescriptor property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the flowControllerDescriptor property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getFlowControllerDescriptor().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link FlowControllerDescriptorType }
     * 
     * 
     */
    public List<FlowControllerDescriptorType> getFlowControllerDescriptor() {
        if (flowControllerDescriptor == null) {
            flowControllerDescriptor = new ArrayList<FlowControllerDescriptorType>();
        }
        return this.flowControllerDescriptor;
    }

}
