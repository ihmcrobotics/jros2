
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for entityFactoryQosPolicyType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="entityFactoryQosPolicyType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element name="autoenable_created_entities" type="{http://www.eprosima.com}boolean" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "entityFactoryQosPolicyType", propOrder = {

})
public class EntityFactoryQosPolicyType {

    @XmlElement(name = "autoenable_created_entities")
    protected Boolean autoenableCreatedEntities;

    /**
     * Gets the value of the autoenableCreatedEntities property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isAutoenableCreatedEntities() {
        return autoenableCreatedEntities;
    }

    /**
     * Sets the value of the autoenableCreatedEntities property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setAutoenableCreatedEntities(Boolean value) {
        this.autoenableCreatedEntities = value;
    }

}
