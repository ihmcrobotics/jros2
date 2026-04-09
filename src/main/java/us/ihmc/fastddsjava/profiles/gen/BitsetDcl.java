
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;


import java.util.ArrayList;
import java.util.List;


/**
 * <p>Java class for bitsetDcl complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="bitsetDcl"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="bitfield" maxOccurs="unbounded"&gt;
 *           &lt;complexType&gt;
 *             &lt;complexContent&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                 &lt;attribute name="name" type="{http://www.eprosima.com}string" /&gt;
 *                 &lt;attribute name="type" type="{http://www.eprosima.com}IntPrimitiveTypes" /&gt;
 *                 &lt;attribute name="bit_bound" use="required"&gt;
 *                   &lt;simpleType&gt;
 *                     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}short"&gt;
 *                       &lt;minInclusive value="1"/&gt;
 *                       &lt;maxInclusive value="64"/&gt;
 *                     &lt;/restriction&gt;
 *                   &lt;/simpleType&gt;
 *                 &lt;/attribute&gt;
 *               &lt;/restriction&gt;
 *             &lt;/complexContent&gt;
 *           &lt;/complexType&gt;
 *         &lt;/element&gt;
 *       &lt;/sequence&gt;
 *       &lt;attribute name="name" use="required" type="{http://www.eprosima.com}string" /&gt;
 *       &lt;attribute name="baseType" type="{http://www.eprosima.com}string" /&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)

public class BitsetDcl {

    
    protected List<BitsetDcl.Bitfield> bitfield;
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    protected String name;
    @JacksonXmlProperty(isAttribute = true, localName = "baseType")
    protected String baseType;

    /**
     * Gets the value of the bitfield property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the bitfield property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getBitfield().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link BitsetDcl.Bitfield }
     * 
     * 
     */
    public List<BitsetDcl.Bitfield> getBitfield() {
        if (bitfield == null) {
            bitfield = new ArrayList<BitsetDcl.Bitfield>();
        }
        return this.bitfield;
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
     * Gets the value of the baseType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBaseType() {
        return baseType;
    }

    /**
     * Sets the value of the baseType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBaseType(String value) {
        this.baseType = value;
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
     *       &lt;attribute name="name" type="{http://www.eprosima.com}string" /&gt;
     *       &lt;attribute name="type" type="{http://www.eprosima.com}IntPrimitiveTypes" /&gt;
     *       &lt;attribute name="bit_bound" use="required"&gt;
     *         &lt;simpleType&gt;
     *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}short"&gt;
     *             &lt;minInclusive value="1"/&gt;
     *             &lt;maxInclusive value="64"/&gt;
     *           &lt;/restriction&gt;
     *         &lt;/simpleType&gt;
     *       &lt;/attribute&gt;
     *     &lt;/restriction&gt;
     *   &lt;/complexContent&gt;
     * &lt;/complexType&gt;
     * </pre>
     * 
     * 
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
    
    public static class Bitfield {

        @JacksonXmlProperty(isAttribute = true, localName = "name")
        protected String name;
        @JacksonXmlProperty(isAttribute = true, localName = "type")
        protected IntPrimitiveTypes type;
        @JacksonXmlProperty(isAttribute = true, localName = "bit_bound")
        protected short bitBound;

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
         * Gets the value of the type property.
         * 
         * @return
         *     possible object is
         *     {@link IntPrimitiveTypes }
         *     
         */
        public IntPrimitiveTypes getType() {
            return type;
        }

        /**
         * Sets the value of the type property.
         * 
         * @param value
         *     allowed object is
         *     {@link IntPrimitiveTypes }
         *     
         */
        public void setType(IntPrimitiveTypes value) {
            this.type = value;
        }

        /**
         * Gets the value of the bitBound property.
         * 
         */
        public short getBitBound() {
            return bitBound;
        }

        /**
         * Sets the value of the bitBound property.
         * 
         */
        public void setBitBound(short value) {
            this.bitBound = value;
        }

    }

}
