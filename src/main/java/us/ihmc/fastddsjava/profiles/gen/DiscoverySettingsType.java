
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;


import java.util.ArrayList;
import java.util.List;


/**
 * <p>Java class for discoverySettingsType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="discoverySettingsType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence maxOccurs="unbounded"&gt;
 *         &lt;choice&gt;
 *           &lt;element name="discoveryProtocol" minOccurs="0"&gt;
 *             &lt;simpleType&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *                 &lt;enumeration value="SIMPLE"/&gt;
 *                 &lt;enumeration value="CLIENT"/&gt;
 *                 &lt;enumeration value="SERVER"/&gt;
 *                 &lt;enumeration value="BACKUP"/&gt;
 *                 &lt;enumeration value="NONE"/&gt;
 *               &lt;/restriction&gt;
 *             &lt;/simpleType&gt;
 *           &lt;/element&gt;
 *           &lt;element name="discoveryServersList" type="{http://www.eprosima.com}locatorListType" minOccurs="0"/&gt;
 *           &lt;element name="ignoreParticipantFlags" minOccurs="0"&gt;
 *             &lt;simpleType&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *                 &lt;pattern value="((FILTER_DIFFERENT_HOST|FILTER_DIFFERENT_PROCESS|FILTER_SAME_PROCESS|NO_FILTER)(\||\s)*)*"/&gt;
 *               &lt;/restriction&gt;
 *             &lt;/simpleType&gt;
 *           &lt;/element&gt;
 *           &lt;element name="EDP" minOccurs="0"&gt;
 *             &lt;simpleType&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *                 &lt;enumeration value="SIMPLE"/&gt;
 *                 &lt;enumeration value="STATIC"/&gt;
 *               &lt;/restriction&gt;
 *             &lt;/simpleType&gt;
 *           &lt;/element&gt;
 *           &lt;element name="simpleEDP" minOccurs="0"&gt;
 *             &lt;complexType&gt;
 *               &lt;complexContent&gt;
 *                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                   &lt;all&gt;
 *                     &lt;element name="PUBWRITER_SUBREADER" type="{http://www.eprosima.com}boolean" minOccurs="0"/&gt;
 *                     &lt;element name="PUBREADER_SUBWRITER" type="{http://www.eprosima.com}boolean" minOccurs="0"/&gt;
 *                   &lt;/all&gt;
 *                 &lt;/restriction&gt;
 *               &lt;/complexContent&gt;
 *             &lt;/complexType&gt;
 *           &lt;/element&gt;
 *           &lt;element name="leaseDuration" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
 *           &lt;element name="leaseAnnouncement" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
 *           &lt;element name="initialAnnouncements" minOccurs="0"&gt;
 *             &lt;complexType&gt;
 *               &lt;complexContent&gt;
 *                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                   &lt;all&gt;
 *                     &lt;element name="count" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
 *                     &lt;element name="period" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
 *                   &lt;/all&gt;
 *                 &lt;/restriction&gt;
 *               &lt;/complexContent&gt;
 *             &lt;/complexType&gt;
 *           &lt;/element&gt;
 *           &lt;element name="clientAnnouncementPeriod" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
 *           &lt;element name="static_edp_xml_config" type="{http://www.eprosima.com}string" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;/choice&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)

public class DiscoverySettingsType {

    
    protected List<Object> discoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags;

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
     * {@link JAXBElement }{@code <}{@link String }{@code >}
     * {@link JAXBElement }{@code <}{@link LocatorListType }{@code >}
     * {@link JAXBElement }{@code <}{@link String }{@code >}
     * {@link JAXBElement }{@code <}{@link String }{@code >}
     * {@link JAXBElement }{@code <}{@link DiscoverySettingsType.SimpleEDP }{@code >}
     * {@link JAXBElement }{@code <}{@link DurationType }{@code >}
     * {@link JAXBElement }{@code <}{@link DurationType }{@code >}
     * {@link JAXBElement }{@code <}{@link DiscoverySettingsType.InitialAnnouncements }{@code >}
     * {@link JAXBElement }{@code <}{@link DurationType }{@code >}
     * {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * 
     */
    public List<Object> getDiscoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags() {
        if (discoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags == null) {
            discoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags = new ArrayList<Object>();
        }
        return this.discoveryProtocolOrDiscoveryServersListOrIgnoreParticipantFlags;
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
     *       &lt;all&gt;
     *         &lt;element name="count" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
     *         &lt;element name="period" type="{http://www.eprosima.com}durationType" minOccurs="0"/&gt;
     *       &lt;/all&gt;
     *     &lt;/restriction&gt;
     *   &lt;/complexContent&gt;
     * &lt;/complexType&gt;
     * </pre>
     * 
     * 
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
    
    public static class InitialAnnouncements {

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
     * &lt;complexType&gt;
     *   &lt;complexContent&gt;
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *       &lt;all&gt;
     *         &lt;element name="PUBWRITER_SUBREADER" type="{http://www.eprosima.com}boolean" minOccurs="0"/&gt;
     *         &lt;element name="PUBREADER_SUBWRITER" type="{http://www.eprosima.com}boolean" minOccurs="0"/&gt;
     *       &lt;/all&gt;
     *     &lt;/restriction&gt;
     *   &lt;/complexContent&gt;
     * &lt;/complexType&gt;
     * </pre>
     * 
     * 
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
    
    public static class SimpleEDP {

        @JacksonXmlProperty(localName = "PUBWRITER_SUBREADER")
        protected Boolean pubwritersubreader;
        @JacksonXmlProperty(localName = "PUBREADER_SUBWRITER")
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
