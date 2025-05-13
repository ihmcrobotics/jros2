
package us.ihmc.fastddsjava.profiles.gen;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for unionDcl complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="unionDcl"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence maxOccurs="unbounded"&gt;
 *         &lt;choice&gt;
 *           &lt;element name="discriminator" minOccurs="0"&gt;
 *             &lt;complexType&gt;
 *               &lt;complexContent&gt;
 *                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                   &lt;attribute name="type" use="required"&gt;
 *                     &lt;simpleType&gt;
 *                       &lt;union memberTypes=" {http://www.eprosima.com}primitiveTypes {http://www.eprosima.com}nonBasicType"&gt;
 *                       &lt;/union&gt;
 *                     &lt;/simpleType&gt;
 *                   &lt;/attribute&gt;
 *                 &lt;/restriction&gt;
 *               &lt;/complexContent&gt;
 *             &lt;/complexType&gt;
 *           &lt;/element&gt;
 *           &lt;element name="case" maxOccurs="unbounded" minOccurs="0"&gt;
 *             &lt;complexType&gt;
 *               &lt;complexContent&gt;
 *                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                   &lt;sequence maxOccurs="unbounded"&gt;
 *                     &lt;choice&gt;
 *                       &lt;element name="caseDiscriminator" maxOccurs="unbounded" minOccurs="0"&gt;
 *                         &lt;complexType&gt;
 *                           &lt;complexContent&gt;
 *                             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                               &lt;attribute name="value" use="required"&gt;
 *                                 &lt;simpleType&gt;
 *                                   &lt;union memberTypes=" {http://www.eprosima.com}string {http://www.eprosima.com}defaultType"&gt;
 *                                   &lt;/union&gt;
 *                                 &lt;/simpleType&gt;
 *                               &lt;/attribute&gt;
 *                             &lt;/restriction&gt;
 *                           &lt;/complexContent&gt;
 *                         &lt;/complexType&gt;
 *                       &lt;/element&gt;
 *                       &lt;element name="member" type="{http://www.eprosima.com}memberDcl" minOccurs="0"/&gt;
 *                     &lt;/choice&gt;
 *                   &lt;/sequence&gt;
 *                 &lt;/restriction&gt;
 *               &lt;/complexContent&gt;
 *             &lt;/complexType&gt;
 *           &lt;/element&gt;
 *         &lt;/choice&gt;
 *       &lt;/sequence&gt;
 *       &lt;attribute name="name" use="required" type="{http://www.eprosima.com}string" /&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "unionDcl", propOrder = {
    "discriminatorOrCase"
})
public class UnionDcl {

    @XmlElements({
        @XmlElement(name = "discriminator", type = UnionDcl.Discriminator.class),
        @XmlElement(name = "case", type = UnionDcl.Case.class)
    })
    protected List<Object> discriminatorOrCase;
    @XmlAttribute(name = "name", required = true)
    protected String name;

    /**
     * Gets the value of the discriminatorOrCase property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the discriminatorOrCase property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDiscriminatorOrCase().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link UnionDcl.Discriminator }
     * {@link UnionDcl.Case }
     * 
     * 
     */
    public List<Object> getDiscriminatorOrCase() {
        if (discriminatorOrCase == null) {
            discriminatorOrCase = new ArrayList<Object>();
        }
        return this.discriminatorOrCase;
    }

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType&gt;
     *   &lt;complexContent&gt;
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *       &lt;sequence maxOccurs="unbounded"&gt;
     *         &lt;choice&gt;
     *           &lt;element name="caseDiscriminator" maxOccurs="unbounded" minOccurs="0"&gt;
     *             &lt;complexType&gt;
     *               &lt;complexContent&gt;
     *                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                   &lt;attribute name="value" use="required"&gt;
     *                     &lt;simpleType&gt;
     *                       &lt;union memberTypes=" {http://www.eprosima.com}string {http://www.eprosima.com}defaultType"&gt;
     *                       &lt;/union&gt;
     *                     &lt;/simpleType&gt;
     *                   &lt;/attribute&gt;
     *                 &lt;/restriction&gt;
     *               &lt;/complexContent&gt;
     *             &lt;/complexType&gt;
     *           &lt;/element&gt;
     *           &lt;element name="member" type="{http://www.eprosima.com}memberDcl" minOccurs="0"/&gt;
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
    @XmlType(name = "", propOrder = {
        "caseDiscriminatorOrMember"
    })
    public static class Case {

        @XmlElements({
            @XmlElement(name = "caseDiscriminator", type = UnionDcl.Case.CaseDiscriminator.class),
            @XmlElement(name = "member", type = MemberDcl.class)
        })
        protected List<Object> caseDiscriminatorOrMember;

        /**
         * Gets the value of the caseDiscriminatorOrMember property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the caseDiscriminatorOrMember property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getCaseDiscriminatorOrMember().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link UnionDcl.Case.CaseDiscriminator }
         * {@link MemberDcl }
         * 
         * 
         */
        public List<Object> getCaseDiscriminatorOrMember() {
            if (caseDiscriminatorOrMember == null) {
                caseDiscriminatorOrMember = new ArrayList<Object>();
            }
            return this.caseDiscriminatorOrMember;
        }


        /**
         * <p>Java class for anonymous complex type.
         * 
         * <p>The following schema fragment specifies the expected content contained within this class.
         * 
         * <pre>
         * &lt;complexType&gt;
         *   &lt;complexContent&gt;
         *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *       &lt;attribute name="value" use="required"&gt;
         *         &lt;simpleType&gt;
         *           &lt;union memberTypes=" {http://www.eprosima.com}string {http://www.eprosima.com}defaultType"&gt;
         *           &lt;/union&gt;
         *         &lt;/simpleType&gt;
         *       &lt;/attribute&gt;
         *     &lt;/restriction&gt;
         *   &lt;/complexContent&gt;
         * &lt;/complexType&gt;
         * </pre>
         * 
         * 
         */
        @XmlAccessorType(XmlAccessType.FIELD)
        @XmlType(name = "")
        public static class CaseDiscriminator {

            @XmlAttribute(name = "value", required = true)
            protected String value;

            /**
             * Gets the value of the value property.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getValue() {
                return value;
            }

            /**
             * Sets the value of the value property.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setValue(String value) {
                this.value = value;
            }

        }

    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType&gt;
     *   &lt;complexContent&gt;
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *       &lt;attribute name="type" use="required"&gt;
     *         &lt;simpleType&gt;
     *           &lt;union memberTypes=" {http://www.eprosima.com}primitiveTypes {http://www.eprosima.com}nonBasicType"&gt;
     *           &lt;/union&gt;
     *         &lt;/simpleType&gt;
     *       &lt;/attribute&gt;
     *     &lt;/restriction&gt;
     *   &lt;/complexContent&gt;
     * &lt;/complexType&gt;
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "")
    public static class Discriminator {

        @XmlAttribute(name = "type", required = true)
        protected String type;

        /**
         * Gets the value of the type property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getType() {
            return type;
        }

        /**
         * Sets the value of the type property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setType(String value) {
            this.type = value;
        }

    }

}
