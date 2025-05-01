
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for rtpsParticipantAllocationAttributesType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="rtpsParticipantAllocationAttributesType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element name="remote_locators" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;all>
 *                   &lt;element name="max_unicast_locators" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
 *                   &lt;element name="max_multicast_locators" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
 *                 &lt;/all>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;element name="total_participants" type="{http://www.eprosima.com}allocationConfigType" minOccurs="0"/>
 *         &lt;element name="total_readers" type="{http://www.eprosima.com}allocationConfigType" minOccurs="0"/>
 *         &lt;element name="total_writers" type="{http://www.eprosima.com}allocationConfigType" minOccurs="0"/>
 *         &lt;element name="send_buffers" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;all>
 *                   &lt;element name="preallocated_number" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
 *                   &lt;element name="dynamic" type="{http://www.eprosima.com}boolean" minOccurs="0"/>
 *                   &lt;element name="network_buffers_config" type="{http://www.eprosima.com}allocationConfigType" minOccurs="0"/>
 *                 &lt;/all>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;element name="max_properties" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
 *         &lt;element name="max_user_data" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
 *         &lt;element name="max_partitions" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "rtpsParticipantAllocationAttributesType", propOrder = {

})
public class RtpsParticipantAllocationAttributesType {

    @XmlElement(name = "remote_locators")
    protected RtpsParticipantAllocationAttributesType.RemoteLocators remoteLocators;
    @XmlElement(name = "total_participants")
    protected AllocationConfigType totalParticipants;
    @XmlElement(name = "total_readers")
    protected AllocationConfigType totalReaders;
    @XmlElement(name = "total_writers")
    protected AllocationConfigType totalWriters;
    @XmlElement(name = "send_buffers")
    protected RtpsParticipantAllocationAttributesType.SendBuffers sendBuffers;
    @XmlElement(name = "max_properties")
    @XmlSchemaType(name = "unsignedInt")
    protected Long maxProperties;
    @XmlElement(name = "max_user_data")
    @XmlSchemaType(name = "unsignedInt")
    protected Long maxUserData;
    @XmlElement(name = "max_partitions")
    @XmlSchemaType(name = "unsignedInt")
    protected Long maxPartitions;

    /**
     * Gets the value of the remoteLocators property.
     * 
     * @return
     *     possible object is
     *     {@link RtpsParticipantAllocationAttributesType.RemoteLocators }
     *     
     */
    public RtpsParticipantAllocationAttributesType.RemoteLocators getRemoteLocators() {
        return remoteLocators;
    }

    /**
     * Sets the value of the remoteLocators property.
     * 
     * @param value
     *     allowed object is
     *     {@link RtpsParticipantAllocationAttributesType.RemoteLocators }
     *     
     */
    public void setRemoteLocators(RtpsParticipantAllocationAttributesType.RemoteLocators value) {
        this.remoteLocators = value;
    }

    /**
     * Gets the value of the totalParticipants property.
     * 
     * @return
     *     possible object is
     *     {@link AllocationConfigType }
     *     
     */
    public AllocationConfigType getTotalParticipants() {
        return totalParticipants;
    }

    /**
     * Sets the value of the totalParticipants property.
     * 
     * @param value
     *     allowed object is
     *     {@link AllocationConfigType }
     *     
     */
    public void setTotalParticipants(AllocationConfigType value) {
        this.totalParticipants = value;
    }

    /**
     * Gets the value of the totalReaders property.
     * 
     * @return
     *     possible object is
     *     {@link AllocationConfigType }
     *     
     */
    public AllocationConfigType getTotalReaders() {
        return totalReaders;
    }

    /**
     * Sets the value of the totalReaders property.
     * 
     * @param value
     *     allowed object is
     *     {@link AllocationConfigType }
     *     
     */
    public void setTotalReaders(AllocationConfigType value) {
        this.totalReaders = value;
    }

    /**
     * Gets the value of the totalWriters property.
     * 
     * @return
     *     possible object is
     *     {@link AllocationConfigType }
     *     
     */
    public AllocationConfigType getTotalWriters() {
        return totalWriters;
    }

    /**
     * Sets the value of the totalWriters property.
     * 
     * @param value
     *     allowed object is
     *     {@link AllocationConfigType }
     *     
     */
    public void setTotalWriters(AllocationConfigType value) {
        this.totalWriters = value;
    }

    /**
     * Gets the value of the sendBuffers property.
     * 
     * @return
     *     possible object is
     *     {@link RtpsParticipantAllocationAttributesType.SendBuffers }
     *     
     */
    public RtpsParticipantAllocationAttributesType.SendBuffers getSendBuffers() {
        return sendBuffers;
    }

    /**
     * Sets the value of the sendBuffers property.
     * 
     * @param value
     *     allowed object is
     *     {@link RtpsParticipantAllocationAttributesType.SendBuffers }
     *     
     */
    public void setSendBuffers(RtpsParticipantAllocationAttributesType.SendBuffers value) {
        this.sendBuffers = value;
    }

    /**
     * Gets the value of the maxProperties property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getMaxProperties() {
        return maxProperties;
    }

    /**
     * Sets the value of the maxProperties property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setMaxProperties(Long value) {
        this.maxProperties = value;
    }

    /**
     * Gets the value of the maxUserData property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getMaxUserData() {
        return maxUserData;
    }

    /**
     * Sets the value of the maxUserData property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setMaxUserData(Long value) {
        this.maxUserData = value;
    }

    /**
     * Gets the value of the maxPartitions property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getMaxPartitions() {
        return maxPartitions;
    }

    /**
     * Sets the value of the maxPartitions property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setMaxPartitions(Long value) {
        this.maxPartitions = value;
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
     *         &lt;element name="max_unicast_locators" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
     *         &lt;element name="max_multicast_locators" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
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
    public static class RemoteLocators {

        @XmlElement(name = "max_unicast_locators")
        @XmlSchemaType(name = "unsignedInt")
        protected Long maxUnicastLocators;
        @XmlElement(name = "max_multicast_locators")
        @XmlSchemaType(name = "unsignedInt")
        protected Long maxMulticastLocators;

        /**
         * Gets the value of the maxUnicastLocators property.
         * 
         * @return
         *     possible object is
         *     {@link Long }
         *     
         */
        public Long getMaxUnicastLocators() {
            return maxUnicastLocators;
        }

        /**
         * Sets the value of the maxUnicastLocators property.
         * 
         * @param value
         *     allowed object is
         *     {@link Long }
         *     
         */
        public void setMaxUnicastLocators(Long value) {
            this.maxUnicastLocators = value;
        }

        /**
         * Gets the value of the maxMulticastLocators property.
         * 
         * @return
         *     possible object is
         *     {@link Long }
         *     
         */
        public Long getMaxMulticastLocators() {
            return maxMulticastLocators;
        }

        /**
         * Sets the value of the maxMulticastLocators property.
         * 
         * @param value
         *     allowed object is
         *     {@link Long }
         *     
         */
        public void setMaxMulticastLocators(Long value) {
            this.maxMulticastLocators = value;
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
     *         &lt;element name="preallocated_number" type="{http://www.eprosima.com}uint32" minOccurs="0"/>
     *         &lt;element name="dynamic" type="{http://www.eprosima.com}boolean" minOccurs="0"/>
     *         &lt;element name="network_buffers_config" type="{http://www.eprosima.com}allocationConfigType" minOccurs="0"/>
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
    public static class SendBuffers {

        @XmlElement(name = "preallocated_number")
        @XmlSchemaType(name = "unsignedInt")
        protected Long preallocatedNumber;
        protected Boolean dynamic;
        @XmlElement(name = "network_buffers_config")
        protected AllocationConfigType networkBuffersConfig;

        /**
         * Gets the value of the preallocatedNumber property.
         * 
         * @return
         *     possible object is
         *     {@link Long }
         *     
         */
        public Long getPreallocatedNumber() {
            return preallocatedNumber;
        }

        /**
         * Sets the value of the preallocatedNumber property.
         * 
         * @param value
         *     allowed object is
         *     {@link Long }
         *     
         */
        public void setPreallocatedNumber(Long value) {
            this.preallocatedNumber = value;
        }

        /**
         * Gets the value of the dynamic property.
         * 
         * @return
         *     possible object is
         *     {@link Boolean }
         *     
         */
        public Boolean isDynamic() {
            return dynamic;
        }

        /**
         * Sets the value of the dynamic property.
         * 
         * @param value
         *     allowed object is
         *     {@link Boolean }
         *     
         */
        public void setDynamic(Boolean value) {
            this.dynamic = value;
        }

        /**
         * Gets the value of the networkBuffersConfig property.
         * 
         * @return
         *     possible object is
         *     {@link AllocationConfigType }
         *     
         */
        public AllocationConfigType getNetworkBuffersConfig() {
            return networkBuffersConfig;
        }

        /**
         * Sets the value of the networkBuffersConfig property.
         * 
         * @param value
         *     allowed object is
         *     {@link AllocationConfigType }
         *     
         */
        public void setNetworkBuffersConfig(AllocationConfigType value) {
            this.networkBuffersConfig = value;
        }

    }

}
