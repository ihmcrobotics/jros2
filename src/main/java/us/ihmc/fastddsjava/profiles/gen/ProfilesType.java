
package us.ihmc.fastddsjava.profiles.gen;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlElementRefs;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for profilesType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="profilesType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence maxOccurs="unbounded">
 *         &lt;choice>
 *           &lt;element name="domainparticipant_factory" type="{http://www.eprosima.com}domainParticipantFactoryProfileType" maxOccurs="unbounded"/>
 *           &lt;element name="participant" type="{http://www.eprosima.com}participantProfileType" maxOccurs="unbounded"/>
 *           &lt;element name="data_writer" type="{http://www.eprosima.com}publisherProfileType" maxOccurs="unbounded"/>
 *           &lt;element name="data_reader" type="{http://www.eprosima.com}subscriberProfileType" maxOccurs="unbounded"/>
 *           &lt;element name="transport_descriptors" type="{http://www.eprosima.com}TransportDescriptorListType"/>
 *           &lt;element name="topic" type="{http://www.eprosima.com}topicProfileType" maxOccurs="unbounded"/>
 *           &lt;element name="replier" type="{http://www.eprosima.com}replierRequesterProfileType" maxOccurs="unbounded"/>
 *           &lt;element name="requester" type="{http://www.eprosima.com}replierRequesterProfileType" maxOccurs="unbounded"/>
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
@XmlType(name = "profilesType", propOrder = {
    "domainparticipantFactoryOrParticipantOrDataWriter"
})
public class ProfilesType {

    @XmlElementRefs({
        @XmlElementRef(name = "data_reader", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "topic", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "data_writer", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "participant", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "requester", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "replier", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "transport_descriptors", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "domainparticipant_factory", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false)
    })
    protected List<JAXBElement<?>> domainparticipantFactoryOrParticipantOrDataWriter;

    /**
     * Gets the value of the domainparticipantFactoryOrParticipantOrDataWriter property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the domainparticipantFactoryOrParticipantOrDataWriter property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDomainparticipantFactoryOrParticipantOrDataWriter().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link JAXBElement }{@code <}{@link SubscriberProfileType }{@code >}
     * {@link JAXBElement }{@code <}{@link TopicProfileType }{@code >}
     * {@link JAXBElement }{@code <}{@link PublisherProfileType }{@code >}
     * {@link JAXBElement }{@code <}{@link ParticipantProfileType }{@code >}
     * {@link JAXBElement }{@code <}{@link ReplierRequesterProfileType }{@code >}
     * {@link JAXBElement }{@code <}{@link ReplierRequesterProfileType }{@code >}
     * {@link JAXBElement }{@code <}{@link TransportDescriptorListType }{@code >}
     * {@link JAXBElement }{@code <}{@link DomainParticipantFactoryProfileType }{@code >}
     * 
     * 
     */
    public List<JAXBElement<?>> getDomainparticipantFactoryOrParticipantOrDataWriter() {
        if (domainparticipantFactoryOrParticipantOrDataWriter == null) {
            domainparticipantFactoryOrParticipantOrDataWriter = new ArrayList<JAXBElement<?>>();
        }
        return this.domainparticipantFactoryOrParticipantOrDataWriter;
    }

}
