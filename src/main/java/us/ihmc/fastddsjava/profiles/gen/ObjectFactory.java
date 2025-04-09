
package us.ihmc.fastddsjava.profiles.gen;

import java.util.List;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlElementDecl;
import jakarta.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the us.ihmc.fastddsjava.profiles.gen package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _Profiles_QNAME = new QName("http://www.eprosima.com", "profiles");
    private final static QName _LibrarySettings_QNAME = new QName("http://www.eprosima.com", "library_settings");
    private final static QName _Types_QNAME = new QName("http://www.eprosima.com", "types");
    private final static QName _Log_QNAME = new QName("http://www.eprosima.com", "log");
    private final static QName _DurationTypeNanosec_QNAME = new QName("http://www.eprosima.com", "nanosec");
    private final static QName _DurationTypeSec_QNAME = new QName("http://www.eprosima.com", "sec");
    private final static QName _ProfilesTypeTransportDescriptors_QNAME = new QName("http://www.eprosima.com", "transport_descriptors");
    private final static QName _ProfilesTypeTopic_QNAME = new QName("http://www.eprosima.com", "topic");
    private final static QName _ProfilesTypeDataReader_QNAME = new QName("http://www.eprosima.com", "data_reader");
    private final static QName _ProfilesTypeReplier_QNAME = new QName("http://www.eprosima.com", "replier");
    private final static QName _ProfilesTypeDomainparticipantFactory_QNAME = new QName("http://www.eprosima.com", "domainparticipant_factory");
    private final static QName _ProfilesTypeParticipant_QNAME = new QName("http://www.eprosima.com", "participant");
    private final static QName _ProfilesTypeDataWriter_QNAME = new QName("http://www.eprosima.com", "data_writer");
    private final static QName _ProfilesTypeRequester_QNAME = new QName("http://www.eprosima.com", "requester");
    private final static QName _TransportDescriptorTypeInterfaceWhiteListInterface_QNAME = new QName("http://www.eprosima.com", "interface");
    private final static QName _TransportDescriptorTypeInterfaceWhiteListAddress_QNAME = new QName("http://www.eprosima.com", "address");
    private final static QName _DiscoverySettingsTypeDiscoveryProtocol_QNAME = new QName("http://www.eprosima.com", "discoveryProtocol");
    private final static QName _DiscoverySettingsTypeClientAnnouncementPeriod_QNAME = new QName("http://www.eprosima.com", "clientAnnouncementPeriod");
    private final static QName _DiscoverySettingsTypeDiscoveryServersList_QNAME = new QName("http://www.eprosima.com", "discoveryServersList");
    private final static QName _DiscoverySettingsTypeSimpleEDP_QNAME = new QName("http://www.eprosima.com", "simpleEDP");
    private final static QName _DiscoverySettingsTypeLeaseDuration_QNAME = new QName("http://www.eprosima.com", "leaseDuration");
    private final static QName _DiscoverySettingsTypeEDP_QNAME = new QName("http://www.eprosima.com", "EDP");
    private final static QName _DiscoverySettingsTypeLeaseAnnouncement_QNAME = new QName("http://www.eprosima.com", "leaseAnnouncement");
    private final static QName _DiscoverySettingsTypeStaticEdpXmlConfig_QNAME = new QName("http://www.eprosima.com", "static_edp_xml_config");
    private final static QName _DiscoverySettingsTypeInitialAnnouncements_QNAME = new QName("http://www.eprosima.com", "initialAnnouncements");
    private final static QName _DiscoverySettingsTypeIgnoreParticipantFlags_QNAME = new QName("http://www.eprosima.com", "ignoreParticipantFlags");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: us.ihmc.fastddsjava.profiles.gen
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link BlocklistType }
     * 
     */
    public BlocklistType createBlocklistType() {
        return new BlocklistType();
    }

    /**
     * Create an instance of {@link TopicElementType }
     * 
     */
    public TopicElementType createTopicElementType() {
        return new TopicElementType();
    }

    /**
     * Create an instance of {@link TransportDescriptorType }
     * 
     */
    public TransportDescriptorType createTransportDescriptorType() {
        return new TransportDescriptorType();
    }

    /**
     * Create an instance of {@link BitmaskDcl }
     * 
     */
    public BitmaskDcl createBitmaskDcl() {
        return new BitmaskDcl();
    }

    /**
     * Create an instance of {@link RtpsParticipantAllocationAttributesType }
     * 
     */
    public RtpsParticipantAllocationAttributesType createRtpsParticipantAllocationAttributesType() {
        return new RtpsParticipantAllocationAttributesType();
    }

    /**
     * Create an instance of {@link BitsetDcl }
     * 
     */
    public BitsetDcl createBitsetDcl() {
        return new BitsetDcl();
    }

    /**
     * Create an instance of {@link ParticipantProfileType }
     * 
     */
    public ParticipantProfileType createParticipantProfileType() {
        return new ParticipantProfileType();
    }

    /**
     * Create an instance of {@link ParticipantProfileType.Rtps }
     * 
     */
    public ParticipantProfileType.Rtps createParticipantProfileTypeRtps() {
        return new ParticipantProfileType.Rtps();
    }

    /**
     * Create an instance of {@link PropertyPolicyType }
     * 
     */
    public PropertyPolicyType createPropertyPolicyType() {
        return new PropertyPolicyType();
    }

    /**
     * Create an instance of {@link AllowlistType }
     * 
     */
    public AllowlistType createAllowlistType() {
        return new AllowlistType();
    }

    /**
     * Create an instance of {@link TlsConfigType }
     * 
     */
    public TlsConfigType createTlsConfigType() {
        return new TlsConfigType();
    }

    /**
     * Create an instance of {@link PartitionQosPolicyType }
     * 
     */
    public PartitionQosPolicyType createPartitionQosPolicyType() {
        return new PartitionQosPolicyType();
    }

    /**
     * Create an instance of {@link TopicProfileType }
     * 
     */
    public TopicProfileType createTopicProfileType() {
        return new TopicProfileType();
    }

    /**
     * Create an instance of {@link DiscoverySettingsType }
     * 
     */
    public DiscoverySettingsType createDiscoverySettingsType() {
        return new DiscoverySettingsType();
    }

    /**
     * Create an instance of {@link UnionDcl }
     * 
     */
    public UnionDcl createUnionDcl() {
        return new UnionDcl();
    }

    /**
     * Create an instance of {@link UnionDcl.Case }
     * 
     */
    public UnionDcl.Case createUnionDclCase() {
        return new UnionDcl.Case();
    }

    /**
     * Create an instance of {@link DataSharingQosPolicyType }
     * 
     */
    public DataSharingQosPolicyType createDataSharingQosPolicyType() {
        return new DataSharingQosPolicyType();
    }

    /**
     * Create an instance of {@link EnumDcl }
     * 
     */
    public EnumDcl createEnumDcl() {
        return new EnumDcl();
    }

    /**
     * Create an instance of {@link LocatorListType }
     * 
     */
    public LocatorListType createLocatorListType() {
        return new LocatorListType();
    }

    /**
     * Create an instance of {@link Dds }
     * 
     */
    public Dds createDds() {
        return new Dds();
    }

    /**
     * Create an instance of {@link ProfilesType }
     * 
     */
    public ProfilesType createProfilesType() {
        return new ProfilesType();
    }

    /**
     * Create an instance of {@link TypesType }
     * 
     */
    public TypesType createTypesType() {
        return new TypesType();
    }

    /**
     * Create an instance of {@link LogType }
     * 
     */
    public LogType createLogType() {
        return new LogType();
    }

    /**
     * Create an instance of {@link LibrarySettingsType }
     * 
     */
    public LibrarySettingsType createLibrarySettingsType() {
        return new LibrarySettingsType();
    }

    /**
     * Create an instance of {@link DestinationOrderQosPolicyType }
     * 
     */
    public DestinationOrderQosPolicyType createDestinationOrderQosPolicyType() {
        return new DestinationOrderQosPolicyType();
    }

    /**
     * Create an instance of {@link BinaryPropertyType }
     * 
     */
    public BinaryPropertyType createBinaryPropertyType() {
        return new BinaryPropertyType();
    }

    /**
     * Create an instance of {@link TypedefDcl }
     * 
     */
    public TypedefDcl createTypedefDcl() {
        return new TypedefDcl();
    }

    /**
     * Create an instance of {@link DisablePositiveAcksQosPolicyType }
     * 
     */
    public DisablePositiveAcksQosPolicyType createDisablePositiveAcksQosPolicyType() {
        return new DisablePositiveAcksQosPolicyType();
    }

    /**
     * Create an instance of {@link TypeType }
     * 
     */
    public TypeType createTypeType() {
        return new TypeType();
    }

    /**
     * Create an instance of {@link StructDcl }
     * 
     */
    public StructDcl createStructDcl() {
        return new StructDcl();
    }

    /**
     * Create an instance of {@link PortType }
     * 
     */
    public PortType createPortType() {
        return new PortType();
    }

    /**
     * Create an instance of {@link SubscriberProfileType }
     * 
     */
    public SubscriberProfileType createSubscriberProfileType() {
        return new SubscriberProfileType();
    }

    /**
     * Create an instance of {@link LifespanQosPolicyType }
     * 
     */
    public LifespanQosPolicyType createLifespanQosPolicyType() {
        return new LifespanQosPolicyType();
    }

    /**
     * Create an instance of {@link DurabilityQosPolicyType }
     * 
     */
    public DurabilityQosPolicyType createDurabilityQosPolicyType() {
        return new DurabilityQosPolicyType();
    }

    /**
     * Create an instance of {@link Udpv6ExternalLocatorType }
     * 
     */
    public Udpv6ExternalLocatorType createUdpv6ExternalLocatorType() {
        return new Udpv6ExternalLocatorType();
    }

    /**
     * Create an instance of {@link OwnershipQosPolicyType }
     * 
     */
    public OwnershipQosPolicyType createOwnershipQosPolicyType() {
        return new OwnershipQosPolicyType();
    }

    /**
     * Create an instance of {@link AllocationConfigType }
     * 
     */
    public AllocationConfigType createAllocationConfigType() {
        return new AllocationConfigType();
    }

    /**
     * Create an instance of {@link BuiltinAttributesType }
     * 
     */
    public BuiltinAttributesType createBuiltinAttributesType() {
        return new BuiltinAttributesType();
    }

    /**
     * Create an instance of {@link LatencyBudgetQosPolicyType }
     * 
     */
    public LatencyBudgetQosPolicyType createLatencyBudgetQosPolicyType() {
        return new LatencyBudgetQosPolicyType();
    }

    /**
     * Create an instance of {@link ExternalLocatorListType }
     * 
     */
    public ExternalLocatorListType createExternalLocatorListType() {
        return new ExternalLocatorListType();
    }

    /**
     * Create an instance of {@link DurationType }
     * 
     */
    public DurationType createDurationType() {
        return new DurationType();
    }

    /**
     * Create an instance of {@link ReplierRequesterProfileType }
     * 
     */
    public ReplierRequesterProfileType createReplierRequesterProfileType() {
        return new ReplierRequesterProfileType();
    }

    /**
     * Create an instance of {@link ThreadSettingsType }
     * 
     */
    public ThreadSettingsType createThreadSettingsType() {
        return new ThreadSettingsType();
    }

    /**
     * Create an instance of {@link FlowControllerDescriptorListType }
     * 
     */
    public FlowControllerDescriptorListType createFlowControllerDescriptorListType() {
        return new FlowControllerDescriptorListType();
    }

    /**
     * Create an instance of {@link EntityFactoryQosPolicyType }
     * 
     */
    public EntityFactoryQosPolicyType createEntityFactoryQosPolicyType() {
        return new EntityFactoryQosPolicyType();
    }

    /**
     * Create an instance of {@link ThreadSettingsWithPortType }
     * 
     */
    public ThreadSettingsWithPortType createThreadSettingsWithPortType() {
        return new ThreadSettingsWithPortType();
    }

    /**
     * Create an instance of {@link TransportDescriptorListType }
     * 
     */
    public TransportDescriptorListType createTransportDescriptorListType() {
        return new TransportDescriptorListType();
    }

    /**
     * Create an instance of {@link OctectVectorQosPolicyType }
     * 
     */
    public OctectVectorQosPolicyType createOctectVectorQosPolicyType() {
        return new OctectVectorQosPolicyType();
    }

    /**
     * Create an instance of {@link DomainParticipantFactoryQosPoliciesType }
     * 
     */
    public DomainParticipantFactoryQosPoliciesType createDomainParticipantFactoryQosPoliciesType() {
        return new DomainParticipantFactoryQosPoliciesType();
    }

    /**
     * Create an instance of {@link Tcpv4LocatorType }
     * 
     */
    public Tcpv4LocatorType createTcpv4LocatorType() {
        return new Tcpv4LocatorType();
    }

    /**
     * Create an instance of {@link DeadlineQosPolicyType }
     * 
     */
    public DeadlineQosPolicyType createDeadlineQosPolicyType() {
        return new DeadlineQosPolicyType();
    }

    /**
     * Create an instance of {@link Tcpv6LocatorType }
     * 
     */
    public Tcpv6LocatorType createTcpv6LocatorType() {
        return new Tcpv6LocatorType();
    }

    /**
     * Create an instance of {@link BuiltinTransportsType }
     * 
     */
    public BuiltinTransportsType createBuiltinTransportsType() {
        return new BuiltinTransportsType();
    }

    /**
     * Create an instance of {@link SubscriberProfileNoAttributesType }
     * 
     */
    public SubscriberProfileNoAttributesType createSubscriberProfileNoAttributesType() {
        return new SubscriberProfileNoAttributesType();
    }

    /**
     * Create an instance of {@link PresentationQosPolicyType }
     * 
     */
    public PresentationQosPolicyType createPresentationQosPolicyType() {
        return new PresentationQosPolicyType();
    }

    /**
     * Create an instance of {@link InterfacesType }
     * 
     */
    public InterfacesType createInterfacesType() {
        return new InterfacesType();
    }

    /**
     * Create an instance of {@link PublisherProfileType }
     * 
     */
    public PublisherProfileType createPublisherProfileType() {
        return new PublisherProfileType();
    }

    /**
     * Create an instance of {@link PublishModeQosPolicyType }
     * 
     */
    public PublishModeQosPolicyType createPublishModeQosPolicyType() {
        return new PublishModeQosPolicyType();
    }

    /**
     * Create an instance of {@link DurabilityServiceQosPolicyType }
     * 
     */
    public DurabilityServiceQosPolicyType createDurabilityServiceQosPolicyType() {
        return new DurabilityServiceQosPolicyType();
    }

    /**
     * Create an instance of {@link LogConsumerType }
     * 
     */
    public LogConsumerType createLogConsumerType() {
        return new LogConsumerType();
    }

    /**
     * Create an instance of {@link Udpv4ExternalLocatorType }
     * 
     */
    public Udpv4ExternalLocatorType createUdpv4ExternalLocatorType() {
        return new Udpv4ExternalLocatorType();
    }

    /**
     * Create an instance of {@link PublisherProfileNoAttributesType }
     * 
     */
    public PublisherProfileNoAttributesType createPublisherProfileNoAttributesType() {
        return new PublisherProfileNoAttributesType();
    }

    /**
     * Create an instance of {@link LivelinessQosPolicyType }
     * 
     */
    public LivelinessQosPolicyType createLivelinessQosPolicyType() {
        return new LivelinessQosPolicyType();
    }

    /**
     * Create an instance of {@link HistoryQosPolicyType }
     * 
     */
    public HistoryQosPolicyType createHistoryQosPolicyType() {
        return new HistoryQosPolicyType();
    }

    /**
     * Create an instance of {@link DataWriterQosPoliciesType }
     * 
     */
    public DataWriterQosPoliciesType createDataWriterQosPoliciesType() {
        return new DataWriterQosPoliciesType();
    }

    /**
     * Create an instance of {@link FlowControllerDescriptorType }
     * 
     */
    public FlowControllerDescriptorType createFlowControllerDescriptorType() {
        return new FlowControllerDescriptorType();
    }

    /**
     * Create an instance of {@link ReaderTimesType }
     * 
     */
    public ReaderTimesType createReaderTimesType() {
        return new ReaderTimesType();
    }

    /**
     * Create an instance of {@link OwnershipStrengthQosPolicyType }
     * 
     */
    public OwnershipStrengthQosPolicyType createOwnershipStrengthQosPolicyType() {
        return new OwnershipStrengthQosPolicyType();
    }

    /**
     * Create an instance of {@link LogConsumerPropertyType }
     * 
     */
    public LogConsumerPropertyType createLogConsumerPropertyType() {
        return new LogConsumerPropertyType();
    }

    /**
     * Create an instance of {@link DomainParticipantFactoryProfileType }
     * 
     */
    public DomainParticipantFactoryProfileType createDomainParticipantFactoryProfileType() {
        return new DomainParticipantFactoryProfileType();
    }

    /**
     * Create an instance of {@link TimeBasedFilterQosPolicyType }
     * 
     */
    public TimeBasedFilterQosPolicyType createTimeBasedFilterQosPolicyType() {
        return new TimeBasedFilterQosPolicyType();
    }

    /**
     * Create an instance of {@link MemberDcl }
     * 
     */
    public MemberDcl createMemberDcl() {
        return new MemberDcl();
    }

    /**
     * Create an instance of {@link PropertyType }
     * 
     */
    public PropertyType createPropertyType() {
        return new PropertyType();
    }

    /**
     * Create an instance of {@link Udpv6LocatorType }
     * 
     */
    public Udpv6LocatorType createUdpv6LocatorType() {
        return new Udpv6LocatorType();
    }

    /**
     * Create an instance of {@link ReceptionThreadsListType }
     * 
     */
    public ReceptionThreadsListType createReceptionThreadsListType() {
        return new ReceptionThreadsListType();
    }

    /**
     * Create an instance of {@link Udpv4LocatorType }
     * 
     */
    public Udpv4LocatorType createUdpv4LocatorType() {
        return new Udpv4LocatorType();
    }

    /**
     * Create an instance of {@link ReliabilityQosPolicyType }
     * 
     */
    public ReliabilityQosPolicyType createReliabilityQosPolicyType() {
        return new ReliabilityQosPolicyType();
    }

    /**
     * Create an instance of {@link DataReaderQosPoliciesType }
     * 
     */
    public DataReaderQosPoliciesType createDataReaderQosPoliciesType() {
        return new DataReaderQosPoliciesType();
    }

    /**
     * Create an instance of {@link WriterTimesType }
     * 
     */
    public WriterTimesType createWriterTimesType() {
        return new WriterTimesType();
    }

    /**
     * Create an instance of {@link BlocklistType.Interface }
     * 
     */
    public BlocklistType.Interface createBlocklistTypeInterface() {
        return new BlocklistType.Interface();
    }

    /**
     * Create an instance of {@link TopicElementType.ResourceLimitsQos }
     * 
     */
    public TopicElementType.ResourceLimitsQos createTopicElementTypeResourceLimitsQos() {
        return new TopicElementType.ResourceLimitsQos();
    }

    /**
     * Create an instance of {@link TransportDescriptorType.InterfaceWhiteList }
     * 
     */
    public TransportDescriptorType.InterfaceWhiteList createTransportDescriptorTypeInterfaceWhiteList() {
        return new TransportDescriptorType.InterfaceWhiteList();
    }

    /**
     * Create an instance of {@link TransportDescriptorType.ListeningPorts }
     * 
     */
    public TransportDescriptorType.ListeningPorts createTransportDescriptorTypeListeningPorts() {
        return new TransportDescriptorType.ListeningPorts();
    }

    /**
     * Create an instance of {@link BitmaskDcl.BitValue }
     * 
     */
    public BitmaskDcl.BitValue createBitmaskDclBitValue() {
        return new BitmaskDcl.BitValue();
    }

    /**
     * Create an instance of {@link RtpsParticipantAllocationAttributesType.RemoteLocators }
     * 
     */
    public RtpsParticipantAllocationAttributesType.RemoteLocators createRtpsParticipantAllocationAttributesTypeRemoteLocators() {
        return new RtpsParticipantAllocationAttributesType.RemoteLocators();
    }

    /**
     * Create an instance of {@link RtpsParticipantAllocationAttributesType.SendBuffers }
     * 
     */
    public RtpsParticipantAllocationAttributesType.SendBuffers createRtpsParticipantAllocationAttributesTypeSendBuffers() {
        return new RtpsParticipantAllocationAttributesType.SendBuffers();
    }

    /**
     * Create an instance of {@link BitsetDcl.Bitfield }
     * 
     */
    public BitsetDcl.Bitfield createBitsetDclBitfield() {
        return new BitsetDcl.Bitfield();
    }

    /**
     * Create an instance of {@link ParticipantProfileType.Rtps.UserTransports }
     * 
     */
    public ParticipantProfileType.Rtps.UserTransports createParticipantProfileTypeRtpsUserTransports() {
        return new ParticipantProfileType.Rtps.UserTransports();
    }

    /**
     * Create an instance of {@link PropertyPolicyType.Properties }
     * 
     */
    public PropertyPolicyType.Properties createPropertyPolicyTypeProperties() {
        return new PropertyPolicyType.Properties();
    }

    /**
     * Create an instance of {@link PropertyPolicyType.BinaryProperties }
     * 
     */
    public PropertyPolicyType.BinaryProperties createPropertyPolicyTypeBinaryProperties() {
        return new PropertyPolicyType.BinaryProperties();
    }

    /**
     * Create an instance of {@link AllowlistType.Interface }
     * 
     */
    public AllowlistType.Interface createAllowlistTypeInterface() {
        return new AllowlistType.Interface();
    }

    /**
     * Create an instance of {@link TlsConfigType.VerifyMode }
     * 
     */
    public TlsConfigType.VerifyMode createTlsConfigTypeVerifyMode() {
        return new TlsConfigType.VerifyMode();
    }

    /**
     * Create an instance of {@link TlsConfigType.VerifyPaths }
     * 
     */
    public TlsConfigType.VerifyPaths createTlsConfigTypeVerifyPaths() {
        return new TlsConfigType.VerifyPaths();
    }

    /**
     * Create an instance of {@link TlsConfigType.Options }
     * 
     */
    public TlsConfigType.Options createTlsConfigTypeOptions() {
        return new TlsConfigType.Options();
    }

    /**
     * Create an instance of {@link PartitionQosPolicyType.Names }
     * 
     */
    public PartitionQosPolicyType.Names createPartitionQosPolicyTypeNames() {
        return new PartitionQosPolicyType.Names();
    }

    /**
     * Create an instance of {@link TopicProfileType.ResourceLimitsQos }
     * 
     */
    public TopicProfileType.ResourceLimitsQos createTopicProfileTypeResourceLimitsQos() {
        return new TopicProfileType.ResourceLimitsQos();
    }

    /**
     * Create an instance of {@link DiscoverySettingsType.SimpleEDP }
     * 
     */
    public DiscoverySettingsType.SimpleEDP createDiscoverySettingsTypeSimpleEDP() {
        return new DiscoverySettingsType.SimpleEDP();
    }

    /**
     * Create an instance of {@link DiscoverySettingsType.InitialAnnouncements }
     * 
     */
    public DiscoverySettingsType.InitialAnnouncements createDiscoverySettingsTypeInitialAnnouncements() {
        return new DiscoverySettingsType.InitialAnnouncements();
    }

    /**
     * Create an instance of {@link UnionDcl.Discriminator }
     * 
     */
    public UnionDcl.Discriminator createUnionDclDiscriminator() {
        return new UnionDcl.Discriminator();
    }

    /**
     * Create an instance of {@link UnionDcl.Case.CaseDiscriminator }
     * 
     */
    public UnionDcl.Case.CaseDiscriminator createUnionDclCaseCaseDiscriminator() {
        return new UnionDcl.Case.CaseDiscriminator();
    }

    /**
     * Create an instance of {@link DataSharingQosPolicyType.DomainIds }
     * 
     */
    public DataSharingQosPolicyType.DomainIds createDataSharingQosPolicyTypeDomainIds() {
        return new DataSharingQosPolicyType.DomainIds();
    }

    /**
     * Create an instance of {@link EnumDcl.Enumerator }
     * 
     */
    public EnumDcl.Enumerator createEnumDclEnumerator() {
        return new EnumDcl.Enumerator();
    }

    /**
     * Create an instance of {@link LocatorListType.Locator }
     * 
     */
    public LocatorListType.Locator createLocatorListTypeLocator() {
        return new LocatorListType.Locator();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ProfilesType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "profiles")
    public JAXBElement<ProfilesType> createProfiles(ProfilesType value) {
        return new JAXBElement<ProfilesType>(_Profiles_QNAME, ProfilesType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LibrarySettingsType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "library_settings")
    public JAXBElement<LibrarySettingsType> createLibrarySettings(LibrarySettingsType value) {
        return new JAXBElement<LibrarySettingsType>(_LibrarySettings_QNAME, LibrarySettingsType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TypesType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "types")
    public JAXBElement<TypesType> createTypes(TypesType value) {
        return new JAXBElement<TypesType>(_Types_QNAME, TypesType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LogType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "log")
    public JAXBElement<LogType> createLog(LogType value) {
        return new JAXBElement<LogType>(_Log_QNAME, LogType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "nanosec", scope = DurationType.class)
    public JAXBElement<String> createDurationTypeNanosec(String value) {
        return new JAXBElement<String>(_DurationTypeNanosec_QNAME, String.class, DurationType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "sec", scope = DurationType.class)
    public JAXBElement<String> createDurationTypeSec(String value) {
        return new JAXBElement<String>(_DurationTypeSec_QNAME, String.class, DurationType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TransportDescriptorListType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "transport_descriptors", scope = ProfilesType.class)
    public JAXBElement<TransportDescriptorListType> createProfilesTypeTransportDescriptors(TransportDescriptorListType value) {
        return new JAXBElement<TransportDescriptorListType>(_ProfilesTypeTransportDescriptors_QNAME, TransportDescriptorListType.class, ProfilesType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TopicProfileType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "topic", scope = ProfilesType.class)
    public JAXBElement<TopicProfileType> createProfilesTypeTopic(TopicProfileType value) {
        return new JAXBElement<TopicProfileType>(_ProfilesTypeTopic_QNAME, TopicProfileType.class, ProfilesType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SubscriberProfileType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "data_reader", scope = ProfilesType.class)
    public JAXBElement<SubscriberProfileType> createProfilesTypeDataReader(SubscriberProfileType value) {
        return new JAXBElement<SubscriberProfileType>(_ProfilesTypeDataReader_QNAME, SubscriberProfileType.class, ProfilesType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReplierRequesterProfileType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "replier", scope = ProfilesType.class)
    public JAXBElement<ReplierRequesterProfileType> createProfilesTypeReplier(ReplierRequesterProfileType value) {
        return new JAXBElement<ReplierRequesterProfileType>(_ProfilesTypeReplier_QNAME, ReplierRequesterProfileType.class, ProfilesType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DomainParticipantFactoryProfileType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "domainparticipant_factory", scope = ProfilesType.class)
    public JAXBElement<DomainParticipantFactoryProfileType> createProfilesTypeDomainparticipantFactory(DomainParticipantFactoryProfileType value) {
        return new JAXBElement<DomainParticipantFactoryProfileType>(_ProfilesTypeDomainparticipantFactory_QNAME, DomainParticipantFactoryProfileType.class, ProfilesType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ParticipantProfileType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "participant", scope = ProfilesType.class)
    public JAXBElement<ParticipantProfileType> createProfilesTypeParticipant(ParticipantProfileType value) {
        return new JAXBElement<ParticipantProfileType>(_ProfilesTypeParticipant_QNAME, ParticipantProfileType.class, ProfilesType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PublisherProfileType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "data_writer", scope = ProfilesType.class)
    public JAXBElement<PublisherProfileType> createProfilesTypeDataWriter(PublisherProfileType value) {
        return new JAXBElement<PublisherProfileType>(_ProfilesTypeDataWriter_QNAME, PublisherProfileType.class, ProfilesType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReplierRequesterProfileType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "requester", scope = ProfilesType.class)
    public JAXBElement<ReplierRequesterProfileType> createProfilesTypeRequester(ReplierRequesterProfileType value) {
        return new JAXBElement<ReplierRequesterProfileType>(_ProfilesTypeRequester_QNAME, ReplierRequesterProfileType.class, ProfilesType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "interface", scope = TransportDescriptorType.InterfaceWhiteList.class)
    public JAXBElement<String> createTransportDescriptorTypeInterfaceWhiteListInterface(String value) {
        return new JAXBElement<String>(_TransportDescriptorTypeInterfaceWhiteListInterface_QNAME, String.class, TransportDescriptorType.InterfaceWhiteList.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link List }{@code <}{@link String }{@code >}{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "address", scope = TransportDescriptorType.InterfaceWhiteList.class)
    public JAXBElement<List<String>> createTransportDescriptorTypeInterfaceWhiteListAddress(List<String> value) {
        return new JAXBElement<List<String>>(_TransportDescriptorTypeInterfaceWhiteListAddress_QNAME, ((Class) List.class), TransportDescriptorType.InterfaceWhiteList.class, ((List<String> ) value));
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "discoveryProtocol", scope = DiscoverySettingsType.class)
    public JAXBElement<String> createDiscoverySettingsTypeDiscoveryProtocol(String value) {
        return new JAXBElement<String>(_DiscoverySettingsTypeDiscoveryProtocol_QNAME, String.class, DiscoverySettingsType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DurationType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "clientAnnouncementPeriod", scope = DiscoverySettingsType.class)
    public JAXBElement<DurationType> createDiscoverySettingsTypeClientAnnouncementPeriod(DurationType value) {
        return new JAXBElement<DurationType>(_DiscoverySettingsTypeClientAnnouncementPeriod_QNAME, DurationType.class, DiscoverySettingsType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LocatorListType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "discoveryServersList", scope = DiscoverySettingsType.class)
    public JAXBElement<LocatorListType> createDiscoverySettingsTypeDiscoveryServersList(LocatorListType value) {
        return new JAXBElement<LocatorListType>(_DiscoverySettingsTypeDiscoveryServersList_QNAME, LocatorListType.class, DiscoverySettingsType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DiscoverySettingsType.SimpleEDP }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "simpleEDP", scope = DiscoverySettingsType.class)
    public JAXBElement<DiscoverySettingsType.SimpleEDP> createDiscoverySettingsTypeSimpleEDP(DiscoverySettingsType.SimpleEDP value) {
        return new JAXBElement<DiscoverySettingsType.SimpleEDP>(_DiscoverySettingsTypeSimpleEDP_QNAME, DiscoverySettingsType.SimpleEDP.class, DiscoverySettingsType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DurationType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "leaseDuration", scope = DiscoverySettingsType.class)
    public JAXBElement<DurationType> createDiscoverySettingsTypeLeaseDuration(DurationType value) {
        return new JAXBElement<DurationType>(_DiscoverySettingsTypeLeaseDuration_QNAME, DurationType.class, DiscoverySettingsType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "EDP", scope = DiscoverySettingsType.class)
    public JAXBElement<String> createDiscoverySettingsTypeEDP(String value) {
        return new JAXBElement<String>(_DiscoverySettingsTypeEDP_QNAME, String.class, DiscoverySettingsType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DurationType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "leaseAnnouncement", scope = DiscoverySettingsType.class)
    public JAXBElement<DurationType> createDiscoverySettingsTypeLeaseAnnouncement(DurationType value) {
        return new JAXBElement<DurationType>(_DiscoverySettingsTypeLeaseAnnouncement_QNAME, DurationType.class, DiscoverySettingsType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "static_edp_xml_config", scope = DiscoverySettingsType.class)
    public JAXBElement<String> createDiscoverySettingsTypeStaticEdpXmlConfig(String value) {
        return new JAXBElement<String>(_DiscoverySettingsTypeStaticEdpXmlConfig_QNAME, String.class, DiscoverySettingsType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DiscoverySettingsType.InitialAnnouncements }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "initialAnnouncements", scope = DiscoverySettingsType.class)
    public JAXBElement<DiscoverySettingsType.InitialAnnouncements> createDiscoverySettingsTypeInitialAnnouncements(DiscoverySettingsType.InitialAnnouncements value) {
        return new JAXBElement<DiscoverySettingsType.InitialAnnouncements>(_DiscoverySettingsTypeInitialAnnouncements_QNAME, DiscoverySettingsType.InitialAnnouncements.class, DiscoverySettingsType.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.eprosima.com", name = "ignoreParticipantFlags", scope = DiscoverySettingsType.class)
    public JAXBElement<String> createDiscoverySettingsTypeIgnoreParticipantFlags(String value) {
        return new JAXBElement<String>(_DiscoverySettingsTypeIgnoreParticipantFlags_QNAME, String.class, DiscoverySettingsType.class, value);
    }

}
