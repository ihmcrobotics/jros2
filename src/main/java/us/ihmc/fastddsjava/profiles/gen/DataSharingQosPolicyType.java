
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;


import java.util.ArrayList;
import java.util.List;


/**
 * <p>Java class for dataSharingQosPolicyType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="dataSharingQosPolicyType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="kind"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *               &lt;enumeration value="AUTOMATIC"/&gt;
 *               &lt;enumeration value="ON"/&gt;
 *               &lt;enumeration value="OFF"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="shared_dir" type="{http://www.eprosima.com}string" minOccurs="0"/&gt;
 *         &lt;element name="domain_ids" minOccurs="0"&gt;
 *           &lt;complexType&gt;
 *             &lt;complexContent&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                 &lt;sequence&gt;
 *                   &lt;element name="domainId" type="{http://www.eprosima.com}uint32" maxOccurs="unbounded" minOccurs="0"/&gt;
 *                 &lt;/sequence&gt;
 *               &lt;/restriction&gt;
 *             &lt;/complexContent&gt;
 *           &lt;/complexType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="max_domains" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
 *         &lt;element name="data_sharing_listener_thread" type="{http://www.eprosima.com}threadSettingsType" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)

public class DataSharingQosPolicyType {

    
    protected String kind;
    @JacksonXmlProperty(localName = "shared_dir")
    protected String sharedDir;
    @JacksonXmlProperty(localName = "domain_ids")
    protected DataSharingQosPolicyType.DomainIds domainIds;
    @JacksonXmlProperty(localName = "max_domains")
        protected Long maxDomains;
    @JacksonXmlProperty(localName = "data_sharing_listener_thread")
    protected ThreadSettingsType dataSharingListenerThread;

    /**
     * Gets the value of the kind property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getKind() {
        return kind;
    }

    /**
     * Sets the value of the kind property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setKind(String value) {
        this.kind = value;
    }

    /**
     * Gets the value of the sharedDir property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSharedDir() {
        return sharedDir;
    }

    /**
     * Sets the value of the sharedDir property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSharedDir(String value) {
        this.sharedDir = value;
    }

    /**
     * Gets the value of the domainIds property.
     * 
     * @return
     *     possible object is
     *     {@link DataSharingQosPolicyType.DomainIds }
     *     
     */
    public DataSharingQosPolicyType.DomainIds getDomainIds() {
        return domainIds;
    }

    /**
     * Sets the value of the domainIds property.
     * 
     * @param value
     *     allowed object is
     *     {@link DataSharingQosPolicyType.DomainIds }
     *     
     */
    public void setDomainIds(DataSharingQosPolicyType.DomainIds value) {
        this.domainIds = value;
    }

    /**
     * Gets the value of the maxDomains property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getMaxDomains() {
        return maxDomains;
    }

    /**
     * Sets the value of the maxDomains property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setMaxDomains(Long value) {
        this.maxDomains = value;
    }

    /**
     * Gets the value of the dataSharingListenerThread property.
     * 
     * @return
     *     possible object is
     *     {@link ThreadSettingsType }
     *     
     */
    public ThreadSettingsType getDataSharingListenerThread() {
        return dataSharingListenerThread;
    }

    /**
     * Sets the value of the dataSharingListenerThread property.
     * 
     * @param value
     *     allowed object is
     *     {@link ThreadSettingsType }
     *     
     */
    public void setDataSharingListenerThread(ThreadSettingsType value) {
        this.dataSharingListenerThread = value;
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
     *       &lt;sequence&gt;
     *         &lt;element name="domainId" type="{http://www.eprosima.com}uint32" maxOccurs="unbounded" minOccurs="0"/&gt;
     *       &lt;/sequence&gt;
     *     &lt;/restriction&gt;
     *   &lt;/complexContent&gt;
     * &lt;/complexType&gt;
     * </pre>
     * 
     * 
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
    
    public static class DomainIds {

        
                protected List<Long> domainId;

        /**
         * Gets the value of the domainId property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the domainId property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getDomainId().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link Long }
         * 
         * 
         */
        public List<Long> getDomainId() {
            if (domainId == null) {
                domainId = new ArrayList<Long>();
            }
            return this.domainId;
        }

    }

}
