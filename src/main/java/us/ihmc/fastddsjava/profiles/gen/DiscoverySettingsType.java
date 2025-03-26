
package us.ihmc.fastddsjava.profiles.gen;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlElementRefs;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for discoverySettingsType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="discoverySettingsType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence maxOccurs="unbounded">
 *         &lt;choice>
 *           &lt;element name="discoveryProtocol" minOccurs="0">
 *             &lt;simpleType>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *                 &lt;enumeration value="SIMPLE"/>
 *                 &lt;enumeration value="CLIENT"/>
 *                 &lt;enumeration value="SERVER"/>
 *                 &lt;enumeration value="BACKUP"/>
 *                 &lt;enumeration value="NONE"/>
 *               &lt;/restriction>
 *             &lt;/simpleType>
 *           &lt;/element>
 *           &lt;element name="discoveryServersList" type="{http://www.eprosima.com}locatorListType" minOccurs="0"/>
 *           &lt;element name="ignoreParticipantFlags" minOccurs="0">
 *             &lt;simpleType>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *                 &lt;pattern value="((FILTER_DIFFERENT_HOST|FILTER_DIFFERENT_PROCESS|FILTER_SAME_PROCESS|NO_FILTER)(\||\s)*)*"/>
 *               &lt;/restriction>
 *             &lt;/simpleType>
 *           &lt;/element>
 *           &lt;element name="EDP" minOccurs="0">
 *             &lt;simpleType>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *                 &lt;enumeration value="SIMPLE"/>
 *                 &lt;enumeration value="STATIC"/>
 *               &lt;/restriction>
 *             &lt;/simpleType>
 *           &lt;/element>
 *           &lt;element name="simpleEDP" minOccurs="0">
 *             &lt;complexType>
 *               &lt;complexContent>
 *                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                   &lt;all>
 *                     &lt;element name="PUBWRITER_SUBREADER" type="{http://www.eprosima.com}boolean" minOccurs="0"/>
 *                     &lt;element name="PUBREADER_SUBWRITER" type="{http://www.eprosima.com}boolean" minOccurs="0"/>
 *                   &lt;/all>
 *                 &lt;/restriction>
 *               &lt;/complexContent>
 *             &lt;/complexType>
 *           &lt;/element>
 *           &lt;element name="leaseDuration" type="{http://www.eprosima.com}durationType" minOccurs="0"/>
 *           &lt;element name="leaseAnnouncement" type="{http://www.eprosima.com}durationType" minOccurs="0"/>
 *           &lt;element name="initialAnnouncements" minOccurs="0">
 *             &lt;complexType>
 *               &lt;complexContent>
 *                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                   &lt;all>
 *                     &lt;element name="count" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
 *                     &lt;element name="period" type="{http://www.eprosima.com}durationType" minOccurs="0"/>
 *                   &lt;/all>
 *                 &lt;/restriction>
 *               &lt;/complexContent>
 *             &lt;/complexType>
 *           &lt;/element>
 *           &lt;element name="clientAnnouncementPeriod" type="{http://www.eprosima.com}durationType" minOccurs="0"/>
 *           &lt;element name="static_edp_xml_config" type="{http://www.eprosima.com}string" maxOccurs="unbounded" minOccurs="0"/>
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
@XmlType(name = "discoverySettingsType", propOrder = {
    "discoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags"
})
public class DiscoverySettingsType {

    @XmlElementRefs({
        @XmlElementRef(name = "leaseDuration", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "discoveryServersList", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "discoveryProtocol", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "initialAnnouncements", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "simpleEDP", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "leaseAnnouncement", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "ignoreParticipantFlags", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "EDP", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "clientAnnouncementPeriod", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "static_edp_xml_config", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false)
    })
    protected List<JAXBElement<?>> discoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags;

    /**
     * Gets the value of the discoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the discoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDiscoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link JAXBElement }{@code <}{@link DurationType }{@code >}
     * {@link JAXBElement }{@code <}{@link LocatorListType }{@code >}
     * {@link JAXBElement }{@code <}{@link String }{@code >}
     * {@link JAXBElement }{@code <}{@link DiscoverySettingsType.InitialAnnouncements }{@code >}
     * {@link JAXBElement }{@code <}{@link DiscoverySettingsType.SimpleEDP }{@code >}
     * {@link JAXBElement }{@code <}{@link DurationType }{@code >}
     * {@link JAXBElement }{@code <}{@link String }{@code >}
     * {@link JAXBElement }{@code <}{@link String }{@code >}
     * {@link JAXBElement }{@code <}{@link DurationType }{@code >}
     * {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * 
     */
    public List<JAXBElement<?>> getDiscoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags() {
        if (discoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags == null) {
            discoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags = new ArrayList<JAXBElement<?>>();
        }
        return this.discoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags;
    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType>
     *   &lt;complexContent>
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *       &lt;all>
     *         &lt;element name="count" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
     *         &lt;element name="period" type="{http://www.eprosima.com}durationType" minOccurs="0"/>
     *       &lt;/all>
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {

    })
    public static class InitialAnnouncements {

        @XmlSchemaType(name = "unsignedInt")
        protected Long count;
        protected DurationType period;

        /**
         * Gets the value of the count property.
         * 
         * @return
         *     possible object is
         *     {@link Long }
         *     
         */
        public Long getCount() {
            return count;
        }

        /**
         * Sets the value of the count property.
         * 
         * @param value
         *     allowed object is
         *     {@link Long }
         *     
         */
        public void setCount(Long value) {
            this.count = value;
        }

        /**
         * Gets the value of the period property.
         * 
         * @return
         *     possible object is
         *     {@link DurationType }
         *     
         */
        public DurationType getPeriod() {
            return period;
        }

        /**
         * Sets the value of the period property.
         * 
         * @param value
         *     allowed object is
         *     {@link DurationType }
         *     
         */
        public void setPeriod(DurationType value) {
            this.period = value;
        }

    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType>
     *   &lt;complexContent>
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *       &lt;all>
     *         &lt;element name="PUBWRITER_SUBREADER" type="{http://www.eprosima.com}boolean" minOccurs="0"/>
     *         &lt;element name="PUBREADER_SUBWRITER" type="{http://www.eprosima.com}boolean" minOccurs="0"/>
     *       &lt;/all>
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {

    })
    public static class SimpleEDP {

        @XmlElement(name = "PUBWRITER_SUBREADER")
        protected Boolean pubwritersubreader;
        @XmlElement(name = "PUBREADER_SUBWRITER")
        protected Boolean pubreadersubwriter;

        /**
         * Gets the value of the pubwritersubreader property.
         * 
         * @return
         *     possible object is
         *     {@link Boolean }
         *     
         */
        public Boolean isPUBWRITERSUBREADER() {
            return pubwritersubreader;
        }

        /**
         * Sets the value of the pubwritersubreader property.
         * 
         * @param value
         *     allowed object is
         *     {@link Boolean }
         *     
         */
        public void setPUBWRITERSUBREADER(Boolean value) {
            this.pubwritersubreader = value;
        }

        /**
         * Gets the value of the pubreadersubwriter property.
         * 
         * @return
         *     possible object is
         *     {@link Boolean }
         *     
         */
        public Boolean isPUBREADERSUBWRITER() {
            return pubreadersubwriter;
        }

        /**
         * Sets the value of the pubreadersubwriter property.
         * 
         * @param value
         *     allowed object is
         *     {@link Boolean }
         *     
         */
        public void setPUBREADERSUBWRITER(Boolean value) {
            this.pubreadersubwriter = value;
        }

    }

}
