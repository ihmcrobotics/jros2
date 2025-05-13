
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for domainParticipantFactoryQosPoliciesType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="domainParticipantFactoryQosPoliciesType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="entity_factory" type="{http://www.eprosima.com}entityFactoryQosPolicyType" minOccurs="0"/&gt;
 *         &lt;element name="shm_watchdog_thread" type="{http://www.eprosima.com}threadSettingsType" minOccurs="0"/&gt;
 *         &lt;element name="file_watch_threads" type="{http://www.eprosima.com}threadSettingsType" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "domainParticipantFactoryQosPoliciesType", propOrder = {

})
public class DomainParticipantFactoryQosPoliciesType {

    @XmlElement(name = "entity_factory")
    protected EntityFactoryQosPolicyType entityFactory;
    @XmlElement(name = "shm_watchdog_thread")
    protected ThreadSettingsType shmWatchdogThread;
    @XmlElement(name = "file_watch_threads")
    protected ThreadSettingsType fileWatchThreads;

    /**
     * Gets the value of the entityFactory property.
     * 
     * @return
     *     possible object is
     *     {@link EntityFactoryQosPolicyType }
     *     
     */
    public EntityFactoryQosPolicyType getEntityFactory() {
        return entityFactory;
    }

    /**
     * Sets the value of the entityFactory property.
     * 
     * @param value
     *     allowed object is
     *     {@link EntityFactoryQosPolicyType }
     *     
     */
    public void setEntityFactory(EntityFactoryQosPolicyType value) {
        this.entityFactory = value;
    }

    /**
     * Gets the value of the shmWatchdogThread property.
     * 
     * @return
     *     possible object is
     *     {@link ThreadSettingsType }
     *     
     */
    public ThreadSettingsType getShmWatchdogThread() {
        return shmWatchdogThread;
    }

    /**
     * Sets the value of the shmWatchdogThread property.
     * 
     * @param value
     *     allowed object is
     *     {@link ThreadSettingsType }
     *     
     */
    public void setShmWatchdogThread(ThreadSettingsType value) {
        this.shmWatchdogThread = value;
    }

    /**
     * Gets the value of the fileWatchThreads property.
     * 
     * @return
     *     possible object is
     *     {@link ThreadSettingsType }
     *     
     */
    public ThreadSettingsType getFileWatchThreads() {
        return fileWatchThreads;
    }

    /**
     * Sets the value of the fileWatchThreads property.
     * 
     * @param value
     *     allowed object is
     *     {@link ThreadSettingsType }
     *     
     */
    public void setFileWatchThreads(ThreadSettingsType value) {
        this.fileWatchThreads = value;
    }

}
