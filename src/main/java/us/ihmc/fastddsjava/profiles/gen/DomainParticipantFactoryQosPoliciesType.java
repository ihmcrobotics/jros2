
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;




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
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)

public class DomainParticipantFactoryQosPoliciesType {

    @JacksonXmlProperty(localName = "entity_factory")
    protected EntityFactoryQosPolicyType entityFactory;
    @JacksonXmlProperty(localName = "shm_watchdog_thread")
    protected ThreadSettingsType shmWatchdogThread;
    @JacksonXmlProperty(localName = "file_watch_threads")
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
