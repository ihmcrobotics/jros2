
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for dataWriterQosPoliciesType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="dataWriterQosPoliciesType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element name="data_sharing" type="{http://www.eprosima.com}dataSharingQosPolicyType" minOccurs="0"/>
 *         &lt;element name="deadline" type="{http://www.eprosima.com}deadlineQosPolicyType" minOccurs="0"/>
 *         &lt;element name="destination_order" type="{http://www.eprosima.com}destinationOrderQosPolicyType" minOccurs="0"/>
 *         &lt;element name="disable_heartbeat_piggyback" type="{http://www.eprosima.com}boolean" minOccurs="0"/>
 *         &lt;element name="disablePositiveAcks" type="{http://www.eprosima.com}disablePositiveAcksQosPolicyType" minOccurs="0"/>
 *         &lt;element name="durability" type="{http://www.eprosima.com}durabilityQosPolicyType" minOccurs="0"/>
 *         &lt;element name="durabilityService" type="{http://www.eprosima.com}durabilityServiceQosPolicyType" minOccurs="0"/>
 *         &lt;element name="groupData" type="{http://www.eprosima.com}octectVectorQosPolicyType" minOccurs="0"/>
 *         &lt;element name="latencyBudget" type="{http://www.eprosima.com}latencyBudgetQosPolicyType" minOccurs="0"/>
 *         &lt;element name="lifespan" type="{http://www.eprosima.com}lifespanQosPolicyType" minOccurs="0"/>
 *         &lt;element name="liveliness" type="{http://www.eprosima.com}livelinessQosPolicyType" minOccurs="0"/>
 *         &lt;element name="ownership" type="{http://www.eprosima.com}ownershipQosPolicyType" minOccurs="0"/>
 *         &lt;element name="ownershipStrength" type="{http://www.eprosima.com}ownershipStrengthQosPolicyType" minOccurs="0"/>
 *         &lt;element name="partition" type="{http://www.eprosima.com}partitionQosPolicyType" minOccurs="0"/>
 *         &lt;element name="presentation" type="{http://www.eprosima.com}presentationQosPolicyType" minOccurs="0"/>
 *         &lt;element name="publishMode" type="{http://www.eprosima.com}publishModeQosPolicyType" minOccurs="0"/>
 *         &lt;element name="reliability" type="{http://www.eprosima.com}reliabilityQosPolicyType" minOccurs="0"/>
 *         &lt;element name="timeBasedFilter" type="{http://www.eprosima.com}timeBasedFilterQosPolicyType" minOccurs="0"/>
 *         &lt;element name="topicData" type="{http://www.eprosima.com}octectVectorQosPolicyType" minOccurs="0"/>
 *         &lt;element name="userData" type="{http://www.eprosima.com}octectVectorQosPolicyType" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "dataWriterQosPoliciesType", propOrder = {

})
public class DataWriterQosPoliciesType {

    @XmlElement(name = "data_sharing")
    protected DataSharingQosPolicyType dataSharing;
    protected DeadlineQosPolicyType deadline;
    @XmlElement(name = "destination_order")
    protected DestinationOrderQosPolicyType destinationOrder;
    @XmlElement(name = "disable_heartbeat_piggyback")
    protected Boolean disableHeartbeatPiggyback;
    protected DisablePositiveAcksQosPolicyType disablePositiveAcks;
    protected DurabilityQosPolicyType durability;
    protected DurabilityServiceQosPolicyType durabilityService;
    protected OctectVectorQosPolicyType groupData;
    protected LatencyBudgetQosPolicyType latencyBudget;
    protected LifespanQosPolicyType lifespan;
    protected LivelinessQosPolicyType liveliness;
    protected OwnershipQosPolicyType ownership;
    protected OwnershipStrengthQosPolicyType ownershipStrength;
    protected PartitionQosPolicyType partition;
    protected PresentationQosPolicyType presentation;
    protected PublishModeQosPolicyType publishMode;
    protected ReliabilityQosPolicyType reliability;
    protected TimeBasedFilterQosPolicyType timeBasedFilter;
    protected OctectVectorQosPolicyType topicData;
    protected OctectVectorQosPolicyType userData;

    /**
     * Gets the value of the dataSharing property.
     * 
     * @return
     *     possible object is
     *     {@link DataSharingQosPolicyType }
     *     
     */
    public DataSharingQosPolicyType getDataSharing() {
        return dataSharing;
    }

    /**
     * Sets the value of the dataSharing property.
     * 
     * @param value
     *     allowed object is
     *     {@link DataSharingQosPolicyType }
     *     
     */
    public void setDataSharing(DataSharingQosPolicyType value) {
        this.dataSharing = value;
    }

    /**
     * Gets the value of the deadline property.
     * 
     * @return
     *     possible object is
     *     {@link DeadlineQosPolicyType }
     *     
     */
    public DeadlineQosPolicyType getDeadline() {
        return deadline;
    }

    /**
     * Sets the value of the deadline property.
     * 
     * @param value
     *     allowed object is
     *     {@link DeadlineQosPolicyType }
     *     
     */
    public void setDeadline(DeadlineQosPolicyType value) {
        this.deadline = value;
    }

    /**
     * Gets the value of the destinationOrder property.
     * 
     * @return
     *     possible object is
     *     {@link DestinationOrderQosPolicyType }
     *     
     */
    public DestinationOrderQosPolicyType getDestinationOrder() {
        return destinationOrder;
    }

    /**
     * Sets the value of the destinationOrder property.
     * 
     * @param value
     *     allowed object is
     *     {@link DestinationOrderQosPolicyType }
     *     
     */
    public void setDestinationOrder(DestinationOrderQosPolicyType value) {
        this.destinationOrder = value;
    }

    /**
     * Gets the value of the disableHeartbeatPiggyback property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isDisableHeartbeatPiggyback() {
        return disableHeartbeatPiggyback;
    }

    /**
     * Sets the value of the disableHeartbeatPiggyback property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setDisableHeartbeatPiggyback(Boolean value) {
        this.disableHeartbeatPiggyback = value;
    }

    /**
     * Gets the value of the disablePositiveAcks property.
     * 
     * @return
     *     possible object is
     *     {@link DisablePositiveAcksQosPolicyType }
     *     
     */
    public DisablePositiveAcksQosPolicyType getDisablePositiveAcks() {
        return disablePositiveAcks;
    }

    /**
     * Sets the value of the disablePositiveAcks property.
     * 
     * @param value
     *     allowed object is
     *     {@link DisablePositiveAcksQosPolicyType }
     *     
     */
    public void setDisablePositiveAcks(DisablePositiveAcksQosPolicyType value) {
        this.disablePositiveAcks = value;
    }

    /**
     * Gets the value of the durability property.
     * 
     * @return
     *     possible object is
     *     {@link DurabilityQosPolicyType }
     *     
     */
    public DurabilityQosPolicyType getDurability() {
        return durability;
    }

    /**
     * Sets the value of the durability property.
     * 
     * @param value
     *     allowed object is
     *     {@link DurabilityQosPolicyType }
     *     
     */
    public void setDurability(DurabilityQosPolicyType value) {
        this.durability = value;
    }

    /**
     * Gets the value of the durabilityService property.
     * 
     * @return
     *     possible object is
     *     {@link DurabilityServiceQosPolicyType }
     *     
     */
    public DurabilityServiceQosPolicyType getDurabilityService() {
        return durabilityService;
    }

    /**
     * Sets the value of the durabilityService property.
     * 
     * @param value
     *     allowed object is
     *     {@link DurabilityServiceQosPolicyType }
     *     
     */
    public void setDurabilityService(DurabilityServiceQosPolicyType value) {
        this.durabilityService = value;
    }

    /**
     * Gets the value of the groupData property.
     * 
     * @return
     *     possible object is
     *     {@link OctectVectorQosPolicyType }
     *     
     */
    public OctectVectorQosPolicyType getGroupData() {
        return groupData;
    }

    /**
     * Sets the value of the groupData property.
     * 
     * @param value
     *     allowed object is
     *     {@link OctectVectorQosPolicyType }
     *     
     */
    public void setGroupData(OctectVectorQosPolicyType value) {
        this.groupData = value;
    }

    /**
     * Gets the value of the latencyBudget property.
     * 
     * @return
     *     possible object is
     *     {@link LatencyBudgetQosPolicyType }
     *     
     */
    public LatencyBudgetQosPolicyType getLatencyBudget() {
        return latencyBudget;
    }

    /**
     * Sets the value of the latencyBudget property.
     * 
     * @param value
     *     allowed object is
     *     {@link LatencyBudgetQosPolicyType }
     *     
     */
    public void setLatencyBudget(LatencyBudgetQosPolicyType value) {
        this.latencyBudget = value;
    }

    /**
     * Gets the value of the lifespan property.
     * 
     * @return
     *     possible object is
     *     {@link LifespanQosPolicyType }
     *     
     */
    public LifespanQosPolicyType getLifespan() {
        return lifespan;
    }

    /**
     * Sets the value of the lifespan property.
     * 
     * @param value
     *     allowed object is
     *     {@link LifespanQosPolicyType }
     *     
     */
    public void setLifespan(LifespanQosPolicyType value) {
        this.lifespan = value;
    }

    /**
     * Gets the value of the liveliness property.
     * 
     * @return
     *     possible object is
     *     {@link LivelinessQosPolicyType }
     *     
     */
    public LivelinessQosPolicyType getLiveliness() {
        return liveliness;
    }

    /**
     * Sets the value of the liveliness property.
     * 
     * @param value
     *     allowed object is
     *     {@link LivelinessQosPolicyType }
     *     
     */
    public void setLiveliness(LivelinessQosPolicyType value) {
        this.liveliness = value;
    }

    /**
     * Gets the value of the ownership property.
     * 
     * @return
     *     possible object is
     *     {@link OwnershipQosPolicyType }
     *     
     */
    public OwnershipQosPolicyType getOwnership() {
        return ownership;
    }

    /**
     * Sets the value of the ownership property.
     * 
     * @param value
     *     allowed object is
     *     {@link OwnershipQosPolicyType }
     *     
     */
    public void setOwnership(OwnershipQosPolicyType value) {
        this.ownership = value;
    }

    /**
     * Gets the value of the ownershipStrength property.
     * 
     * @return
     *     possible object is
     *     {@link OwnershipStrengthQosPolicyType }
     *     
     */
    public OwnershipStrengthQosPolicyType getOwnershipStrength() {
        return ownershipStrength;
    }

    /**
     * Sets the value of the ownershipStrength property.
     * 
     * @param value
     *     allowed object is
     *     {@link OwnershipStrengthQosPolicyType }
     *     
     */
    public void setOwnershipStrength(OwnershipStrengthQosPolicyType value) {
        this.ownershipStrength = value;
    }

    /**
     * Gets the value of the partition property.
     * 
     * @return
     *     possible object is
     *     {@link PartitionQosPolicyType }
     *     
     */
    public PartitionQosPolicyType getPartition() {
        return partition;
    }

    /**
     * Sets the value of the partition property.
     * 
     * @param value
     *     allowed object is
     *     {@link PartitionQosPolicyType }
     *     
     */
    public void setPartition(PartitionQosPolicyType value) {
        this.partition = value;
    }

    /**
     * Gets the value of the presentation property.
     * 
     * @return
     *     possible object is
     *     {@link PresentationQosPolicyType }
     *     
     */
    public PresentationQosPolicyType getPresentation() {
        return presentation;
    }

    /**
     * Sets the value of the presentation property.
     * 
     * @param value
     *     allowed object is
     *     {@link PresentationQosPolicyType }
     *     
     */
    public void setPresentation(PresentationQosPolicyType value) {
        this.presentation = value;
    }

    /**
     * Gets the value of the publishMode property.
     * 
     * @return
     *     possible object is
     *     {@link PublishModeQosPolicyType }
     *     
     */
    public PublishModeQosPolicyType getPublishMode() {
        return publishMode;
    }

    /**
     * Sets the value of the publishMode property.
     * 
     * @param value
     *     allowed object is
     *     {@link PublishModeQosPolicyType }
     *     
     */
    public void setPublishMode(PublishModeQosPolicyType value) {
        this.publishMode = value;
    }

    /**
     * Gets the value of the reliability property.
     * 
     * @return
     *     possible object is
     *     {@link ReliabilityQosPolicyType }
     *     
     */
    public ReliabilityQosPolicyType getReliability() {
        return reliability;
    }

    /**
     * Sets the value of the reliability property.
     * 
     * @param value
     *     allowed object is
     *     {@link ReliabilityQosPolicyType }
     *     
     */
    public void setReliability(ReliabilityQosPolicyType value) {
        this.reliability = value;
    }

    /**
     * Gets the value of the timeBasedFilter property.
     * 
     * @return
     *     possible object is
     *     {@link TimeBasedFilterQosPolicyType }
     *     
     */
    public TimeBasedFilterQosPolicyType getTimeBasedFilter() {
        return timeBasedFilter;
    }

    /**
     * Sets the value of the timeBasedFilter property.
     * 
     * @param value
     *     allowed object is
     *     {@link TimeBasedFilterQosPolicyType }
     *     
     */
    public void setTimeBasedFilter(TimeBasedFilterQosPolicyType value) {
        this.timeBasedFilter = value;
    }

    /**
     * Gets the value of the topicData property.
     * 
     * @return
     *     possible object is
     *     {@link OctectVectorQosPolicyType }
     *     
     */
    public OctectVectorQosPolicyType getTopicData() {
        return topicData;
    }

    /**
     * Sets the value of the topicData property.
     * 
     * @param value
     *     allowed object is
     *     {@link OctectVectorQosPolicyType }
     *     
     */
    public void setTopicData(OctectVectorQosPolicyType value) {
        this.topicData = value;
    }

    /**
     * Gets the value of the userData property.
     * 
     * @return
     *     possible object is
     *     {@link OctectVectorQosPolicyType }
     *     
     */
    public OctectVectorQosPolicyType getUserData() {
        return userData;
    }

    /**
     * Sets the value of the userData property.
     * 
     * @param value
     *     allowed object is
     *     {@link OctectVectorQosPolicyType }
     *     
     */
    public void setUserData(OctectVectorQosPolicyType value) {
        this.userData = value;
    }

}
