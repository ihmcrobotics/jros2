
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
 * <p>Java class for transportDescriptorType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="transportDescriptorType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all minOccurs="0"&gt;
 *         &lt;element name="transport_id" type="{http://www.eprosima.com}string"/&gt;
 *         &lt;element name="type" minOccurs="0"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *               &lt;enumeration value="UDPv4"/&gt;
 *               &lt;enumeration value="UDPv6"/&gt;
 *               &lt;enumeration value="TCPv4"/&gt;
 *               &lt;enumeration value="TCPv6"/&gt;
 *               &lt;enumeration value="SHM"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="sendBufferSize" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
 *         &lt;element name="receiveBufferSize" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
 *         &lt;element name="maxMessageSize" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
 *         &lt;element name="maxInitialPeersRange" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
 *         &lt;element name="interfaceWhiteList" minOccurs="0"&gt;
 *           &lt;complexType&gt;
 *             &lt;complexContent&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                 &lt;sequence maxOccurs="unbounded" minOccurs="0"&gt;
 *                   &lt;choice&gt;
 *                     &lt;element name="address" maxOccurs="unbounded"&gt;
 *                       &lt;simpleType&gt;
 *                         &lt;union memberTypes=" {http://www.eprosima.com}ipv4Address {http://www.eprosima.com}ipv6Address"&gt;
 *                         &lt;/union&gt;
 *                       &lt;/simpleType&gt;
 *                     &lt;/element&gt;
 *                     &lt;element name="interface" type="{http://www.eprosima.com}string" maxOccurs="unbounded"/&gt;
 *                   &lt;/choice&gt;
 *                 &lt;/sequence&gt;
 *               &lt;/restriction&gt;
 *             &lt;/complexContent&gt;
 *           &lt;/complexType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="netmask_filter" minOccurs="0"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *               &lt;enumeration value="OFF"/&gt;
 *               &lt;enumeration value="AUTO"/&gt;
 *               &lt;enumeration value="ON"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="interfaces" type="{http://www.eprosima.com}interfacesType" minOccurs="0"/&gt;
 *         &lt;element name="TTL" type="{http://www.eprosima.com}uint8" minOccurs="0"/&gt;
 *         &lt;element name="non_blocking_send" type="{http://www.eprosima.com}boolean" minOccurs="0"/&gt;
 *         &lt;element name="output_port" type="{http://www.eprosima.com}uint16" minOccurs="0"/&gt;
 *         &lt;element name="wan_addr" type="{http://www.eprosima.com}ipv4AddressFormat" minOccurs="0"/&gt;
 *         &lt;element name="keep_alive_frequency_ms" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
 *         &lt;element name="keep_alive_timeout_ms" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
 *         &lt;element name="max_logical_port" type="{http://www.eprosima.com}uint16" minOccurs="0"/&gt;
 *         &lt;element name="logical_port_range" type="{http://www.eprosima.com}uint16" minOccurs="0"/&gt;
 *         &lt;element name="logical_port_increment" type="{http://www.eprosima.com}uint16" minOccurs="0"/&gt;
 *         &lt;element name="listening_ports" minOccurs="0"&gt;
 *           &lt;complexType&gt;
 *             &lt;complexContent&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                 &lt;sequence maxOccurs="unbounded" minOccurs="0"&gt;
 *                   &lt;element name="port" type="{http://www.eprosima.com}uint16" maxOccurs="unbounded" minOccurs="0"/&gt;
 *                 &lt;/sequence&gt;
 *               &lt;/restriction&gt;
 *             &lt;/complexContent&gt;
 *           &lt;/complexType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="calculate_crc" type="{http://www.eprosima.com}boolean" minOccurs="0"/&gt;
 *         &lt;element name="check_crc" type="{http://www.eprosima.com}boolean" minOccurs="0"/&gt;
 *         &lt;element name="enable_tcp_nodelay" type="{http://www.eprosima.com}boolean" minOccurs="0"/&gt;
 *         &lt;element name="tls" type="{http://www.eprosima.com}tlsConfigType" minOccurs="0"/&gt;
 *         &lt;element name="keep_alive_thread" type="{http://www.eprosima.com}threadSettingsType" minOccurs="0"/&gt;
 *         &lt;element name="accept_thread" type="{http://www.eprosima.com}threadSettingsType" minOccurs="0"/&gt;
 *         &lt;element name="tcp_negotiation_timeout" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
 *         &lt;element name="segment_size" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
 *         &lt;element name="port_queue_capacity" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
 *         &lt;element name="healthy_check_timeout_ms" type="{http://www.eprosima.com}uint32" minOccurs="0"/&gt;
 *         &lt;element name="rtps_dump_file" type="{http://www.eprosima.com}string" minOccurs="0"/&gt;
 *         &lt;element name="default_reception_threads" type="{http://www.eprosima.com}threadSettingsType" minOccurs="0"/&gt;
 *         &lt;element name="reception_threads" type="{http://www.eprosima.com}receptionThreadsListType" minOccurs="0"/&gt;
 *         &lt;element name="dump_thread" type="{http://www.eprosima.com}threadSettingsType" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "transportDescriptorType", propOrder = {

})
public class TransportDescriptorType {

    @XmlElement(name = "transport_id")
    protected String transportId;
    protected String type;
    @XmlSchemaType(name = "unsignedInt")
    protected Long sendBufferSize;
    @XmlSchemaType(name = "unsignedInt")
    protected Long receiveBufferSize;
    @XmlSchemaType(name = "unsignedInt")
    protected Long maxMessageSize;
    @XmlSchemaType(name = "unsignedInt")
    protected Long maxInitialPeersRange;
    protected TransportDescriptorType.InterfaceWhiteList interfaceWhiteList;
    @XmlElement(name = "netmask_filter")
    protected String netmaskFilter;
    protected InterfacesType interfaces;
    @XmlElement(name = "TTL")
    @XmlSchemaType(name = "unsignedByte")
    protected Short ttl;
    @XmlElement(name = "non_blocking_send")
    protected Boolean nonBlockingSend;
    @XmlElement(name = "output_port")
    @XmlSchemaType(name = "unsignedShort")
    protected Integer outputPort;
    @XmlElement(name = "wan_addr")
    protected String wanAddr;
    @XmlElement(name = "keep_alive_frequency_ms")
    @XmlSchemaType(name = "unsignedInt")
    protected Long keepAliveFrequencyMs;
    @XmlElement(name = "keep_alive_timeout_ms")
    @XmlSchemaType(name = "unsignedInt")
    protected Long keepAliveTimeoutMs;
    @XmlElement(name = "max_logical_port")
    @XmlSchemaType(name = "unsignedShort")
    protected Integer maxLogicalPort;
    @XmlElement(name = "logical_port_range")
    @XmlSchemaType(name = "unsignedShort")
    protected Integer logicalPortRange;
    @XmlElement(name = "logical_port_increment")
    @XmlSchemaType(name = "unsignedShort")
    protected Integer logicalPortIncrement;
    @XmlElement(name = "listening_ports")
    protected TransportDescriptorType.ListeningPorts listeningPorts;
    @XmlElement(name = "calculate_crc")
    protected Boolean calculateCrc;
    @XmlElement(name = "check_crc")
    protected Boolean checkCrc;
    @XmlElement(name = "enable_tcp_nodelay")
    protected Boolean enableTcpNodelay;
    protected TlsConfigType tls;
    @XmlElement(name = "keep_alive_thread")
    protected ThreadSettingsType keepAliveThread;
    @XmlElement(name = "accept_thread")
    protected ThreadSettingsType acceptThread;
    @XmlElement(name = "tcp_negotiation_timeout")
    @XmlSchemaType(name = "unsignedInt")
    protected Long tcpNegotiationTimeout;
    @XmlElement(name = "segment_size")
    @XmlSchemaType(name = "unsignedInt")
    protected Long segmentSize;
    @XmlElement(name = "port_queue_capacity")
    @XmlSchemaType(name = "unsignedInt")
    protected Long portQueueCapacity;
    @XmlElement(name = "healthy_check_timeout_ms")
    @XmlSchemaType(name = "unsignedInt")
    protected Long healthyCheckTimeoutMs;
    @XmlElement(name = "rtps_dump_file")
    protected String rtpsDumpFile;
    @XmlElement(name = "default_reception_threads")
    protected ThreadSettingsType defaultReceptionThreads;
    @XmlElement(name = "reception_threads")
    protected ReceptionThreadsListType receptionThreads;
    @XmlElement(name = "dump_thread")
    protected ThreadSettingsType dumpThread;

    /**
     * Gets the value of the transportId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTransportId() {
        return transportId;
    }

    /**
     * Sets the value of the transportId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTransportId(String value) {
        this.transportId = value;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setType(String value) {
        this.type = value;
    }

    /**
     * Gets the value of the sendBufferSize property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getSendBufferSize() {
        return sendBufferSize;
    }

    /**
     * Sets the value of the sendBufferSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setSendBufferSize(Long value) {
        this.sendBufferSize = value;
    }

    /**
     * Gets the value of the receiveBufferSize property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getReceiveBufferSize() {
        return receiveBufferSize;
    }

    /**
     * Sets the value of the receiveBufferSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setReceiveBufferSize(Long value) {
        this.receiveBufferSize = value;
    }

    /**
     * Gets the value of the maxMessageSize property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getMaxMessageSize() {
        return maxMessageSize;
    }

    /**
     * Sets the value of the maxMessageSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setMaxMessageSize(Long value) {
        this.maxMessageSize = value;
    }

    /**
     * Gets the value of the maxInitialPeersRange property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getMaxInitialPeersRange() {
        return maxInitialPeersRange;
    }

    /**
     * Sets the value of the maxInitialPeersRange property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setMaxInitialPeersRange(Long value) {
        this.maxInitialPeersRange = value;
    }

    /**
     * Gets the value of the interfaceWhiteList property.
     * 
     * @return
     *     possible object is
     *     {@link TransportDescriptorType.InterfaceWhiteList }
     *     
     */
    public TransportDescriptorType.InterfaceWhiteList getInterfaceWhiteList() {
        return interfaceWhiteList;
    }

    /**
     * Sets the value of the interfaceWhiteList property.
     * 
     * @param value
     *     allowed object is
     *     {@link TransportDescriptorType.InterfaceWhiteList }
     *     
     */
    public void setInterfaceWhiteList(TransportDescriptorType.InterfaceWhiteList value) {
        this.interfaceWhiteList = value;
    }

    /**
     * Gets the value of the netmaskFilter property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getNetmaskFilter() {
        return netmaskFilter;
    }

    /**
     * Sets the value of the netmaskFilter property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setNetmaskFilter(String value) {
        this.netmaskFilter = value;
    }

    /**
     * Gets the value of the interfaces property.
     * 
     * @return
     *     possible object is
     *     {@link InterfacesType }
     *     
     */
    public InterfacesType getInterfaces() {
        return interfaces;
    }

    /**
     * Sets the value of the interfaces property.
     * 
     * @param value
     *     allowed object is
     *     {@link InterfacesType }
     *     
     */
    public void setInterfaces(InterfacesType value) {
        this.interfaces = value;
    }

    /**
     * Gets the value of the ttl property.
     * 
     * @return
     *     possible object is
     *     {@link Short }
     *     
     */
    public Short getTTL() {
        return ttl;
    }

    /**
     * Sets the value of the ttl property.
     * 
     * @param value
     *     allowed object is
     *     {@link Short }
     *     
     */
    public void setTTL(Short value) {
        this.ttl = value;
    }

    /**
     * Gets the value of the nonBlockingSend property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isNonBlockingSend() {
        return nonBlockingSend;
    }

    /**
     * Sets the value of the nonBlockingSend property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setNonBlockingSend(Boolean value) {
        this.nonBlockingSend = value;
    }

    /**
     * Gets the value of the outputPort property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getOutputPort() {
        return outputPort;
    }

    /**
     * Sets the value of the outputPort property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setOutputPort(Integer value) {
        this.outputPort = value;
    }

    /**
     * Gets the value of the wanAddr property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getWanAddr() {
        return wanAddr;
    }

    /**
     * Sets the value of the wanAddr property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setWanAddr(String value) {
        this.wanAddr = value;
    }

    /**
     * Gets the value of the keepAliveFrequencyMs property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getKeepAliveFrequencyMs() {
        return keepAliveFrequencyMs;
    }

    /**
     * Sets the value of the keepAliveFrequencyMs property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setKeepAliveFrequencyMs(Long value) {
        this.keepAliveFrequencyMs = value;
    }

    /**
     * Gets the value of the keepAliveTimeoutMs property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getKeepAliveTimeoutMs() {
        return keepAliveTimeoutMs;
    }

    /**
     * Sets the value of the keepAliveTimeoutMs property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setKeepAliveTimeoutMs(Long value) {
        this.keepAliveTimeoutMs = value;
    }

    /**
     * Gets the value of the maxLogicalPort property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getMaxLogicalPort() {
        return maxLogicalPort;
    }

    /**
     * Sets the value of the maxLogicalPort property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setMaxLogicalPort(Integer value) {
        this.maxLogicalPort = value;
    }

    /**
     * Gets the value of the logicalPortRange property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getLogicalPortRange() {
        return logicalPortRange;
    }

    /**
     * Sets the value of the logicalPortRange property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setLogicalPortRange(Integer value) {
        this.logicalPortRange = value;
    }

    /**
     * Gets the value of the logicalPortIncrement property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getLogicalPortIncrement() {
        return logicalPortIncrement;
    }

    /**
     * Sets the value of the logicalPortIncrement property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setLogicalPortIncrement(Integer value) {
        this.logicalPortIncrement = value;
    }

    /**
     * Gets the value of the listeningPorts property.
     * 
     * @return
     *     possible object is
     *     {@link TransportDescriptorType.ListeningPorts }
     *     
     */
    public TransportDescriptorType.ListeningPorts getListeningPorts() {
        return listeningPorts;
    }

    /**
     * Sets the value of the listeningPorts property.
     * 
     * @param value
     *     allowed object is
     *     {@link TransportDescriptorType.ListeningPorts }
     *     
     */
    public void setListeningPorts(TransportDescriptorType.ListeningPorts value) {
        this.listeningPorts = value;
    }

    /**
     * Gets the value of the calculateCrc property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isCalculateCrc() {
        return calculateCrc;
    }

    /**
     * Sets the value of the calculateCrc property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setCalculateCrc(Boolean value) {
        this.calculateCrc = value;
    }

    /**
     * Gets the value of the checkCrc property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isCheckCrc() {
        return checkCrc;
    }

    /**
     * Sets the value of the checkCrc property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setCheckCrc(Boolean value) {
        this.checkCrc = value;
    }

    /**
     * Gets the value of the enableTcpNodelay property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isEnableTcpNodelay() {
        return enableTcpNodelay;
    }

    /**
     * Sets the value of the enableTcpNodelay property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setEnableTcpNodelay(Boolean value) {
        this.enableTcpNodelay = value;
    }

    /**
     * Gets the value of the tls property.
     * 
     * @return
     *     possible object is
     *     {@link TlsConfigType }
     *     
     */
    public TlsConfigType getTls() {
        return tls;
    }

    /**
     * Sets the value of the tls property.
     * 
     * @param value
     *     allowed object is
     *     {@link TlsConfigType }
     *     
     */
    public void setTls(TlsConfigType value) {
        this.tls = value;
    }

    /**
     * Gets the value of the keepAliveThread property.
     * 
     * @return
     *     possible object is
     *     {@link ThreadSettingsType }
     *     
     */
    public ThreadSettingsType getKeepAliveThread() {
        return keepAliveThread;
    }

    /**
     * Sets the value of the keepAliveThread property.
     * 
     * @param value
     *     allowed object is
     *     {@link ThreadSettingsType }
     *     
     */
    public void setKeepAliveThread(ThreadSettingsType value) {
        this.keepAliveThread = value;
    }

    /**
     * Gets the value of the acceptThread property.
     * 
     * @return
     *     possible object is
     *     {@link ThreadSettingsType }
     *     
     */
    public ThreadSettingsType getAcceptThread() {
        return acceptThread;
    }

    /**
     * Sets the value of the acceptThread property.
     * 
     * @param value
     *     allowed object is
     *     {@link ThreadSettingsType }
     *     
     */
    public void setAcceptThread(ThreadSettingsType value) {
        this.acceptThread = value;
    }

    /**
     * Gets the value of the tcpNegotiationTimeout property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getTcpNegotiationTimeout() {
        return tcpNegotiationTimeout;
    }

    /**
     * Sets the value of the tcpNegotiationTimeout property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setTcpNegotiationTimeout(Long value) {
        this.tcpNegotiationTimeout = value;
    }

    /**
     * Gets the value of the segmentSize property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getSegmentSize() {
        return segmentSize;
    }

    /**
     * Sets the value of the segmentSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setSegmentSize(Long value) {
        this.segmentSize = value;
    }

    /**
     * Gets the value of the portQueueCapacity property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getPortQueueCapacity() {
        return portQueueCapacity;
    }

    /**
     * Sets the value of the portQueueCapacity property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setPortQueueCapacity(Long value) {
        this.portQueueCapacity = value;
    }

    /**
     * Gets the value of the healthyCheckTimeoutMs property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getHealthyCheckTimeoutMs() {
        return healthyCheckTimeoutMs;
    }

    /**
     * Sets the value of the healthyCheckTimeoutMs property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setHealthyCheckTimeoutMs(Long value) {
        this.healthyCheckTimeoutMs = value;
    }

    /**
     * Gets the value of the rtpsDumpFile property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRtpsDumpFile() {
        return rtpsDumpFile;
    }

    /**
     * Sets the value of the rtpsDumpFile property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRtpsDumpFile(String value) {
        this.rtpsDumpFile = value;
    }

    /**
     * Gets the value of the defaultReceptionThreads property.
     * 
     * @return
     *     possible object is
     *     {@link ThreadSettingsType }
     *     
     */
    public ThreadSettingsType getDefaultReceptionThreads() {
        return defaultReceptionThreads;
    }

    /**
     * Sets the value of the defaultReceptionThreads property.
     * 
     * @param value
     *     allowed object is
     *     {@link ThreadSettingsType }
     *     
     */
    public void setDefaultReceptionThreads(ThreadSettingsType value) {
        this.defaultReceptionThreads = value;
    }

    /**
     * Gets the value of the receptionThreads property.
     * 
     * @return
     *     possible object is
     *     {@link ReceptionThreadsListType }
     *     
     */
    public ReceptionThreadsListType getReceptionThreads() {
        return receptionThreads;
    }

    /**
     * Sets the value of the receptionThreads property.
     * 
     * @param value
     *     allowed object is
     *     {@link ReceptionThreadsListType }
     *     
     */
    public void setReceptionThreads(ReceptionThreadsListType value) {
        this.receptionThreads = value;
    }

    /**
     * Gets the value of the dumpThread property.
     * 
     * @return
     *     possible object is
     *     {@link ThreadSettingsType }
     *     
     */
    public ThreadSettingsType getDumpThread() {
        return dumpThread;
    }

    /**
     * Sets the value of the dumpThread property.
     * 
     * @param value
     *     allowed object is
     *     {@link ThreadSettingsType }
     *     
     */
    public void setDumpThread(ThreadSettingsType value) {
        this.dumpThread = value;
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
     *       &lt;sequence maxOccurs="unbounded" minOccurs="0"&gt;
     *         &lt;choice&gt;
     *           &lt;element name="address" maxOccurs="unbounded"&gt;
     *             &lt;simpleType&gt;
     *               &lt;union memberTypes=" {http://www.eprosima.com}ipv4Address {http://www.eprosima.com}ipv6Address"&gt;
     *               &lt;/union&gt;
     *             &lt;/simpleType&gt;
     *           &lt;/element&gt;
     *           &lt;element name="interface" type="{http://www.eprosima.com}string" maxOccurs="unbounded"/&gt;
     *         &lt;/choice&gt;
     *       &lt;/sequence&gt;
     *     &lt;/restriction&gt;
     *   &lt;/complexContent&gt;
     * &lt;/complexType&gt;
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "addressOrInterface"
    })
    public static class InterfaceWhiteList {

        @XmlElementRefs({
            @XmlElementRef(name = "address", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false),
            @XmlElementRef(name = "interface", namespace = "http://www.eprosima.com", type = JAXBElement.class, required = false)
        })
        protected List<JAXBElement<?>> addressOrInterface;

        /**
         * Gets the value of the addressOrInterface property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the addressOrInterface property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getAddressOrInterface().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link JAXBElement }{@code <}{@link List }{@code <}{@link String }{@code >}{@code >}
         * {@link JAXBElement }{@code <}{@link String }{@code >}
         * 
         * 
         */
        public List<JAXBElement<?>> getAddressOrInterface() {
            if (addressOrInterface == null) {
                addressOrInterface = new ArrayList<JAXBElement<?>>();
            }
            return this.addressOrInterface;
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
     *       &lt;sequence maxOccurs="unbounded" minOccurs="0"&gt;
     *         &lt;element name="port" type="{http://www.eprosima.com}uint16" maxOccurs="unbounded" minOccurs="0"/&gt;
     *       &lt;/sequence&gt;
     *     &lt;/restriction&gt;
     *   &lt;/complexContent&gt;
     * &lt;/complexType&gt;
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "port"
    })
    public static class ListeningPorts {

        @XmlElement(type = Integer.class)
        @XmlSchemaType(name = "unsignedShort")
        protected List<Integer> port;

        /**
         * Gets the value of the port property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the port property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getPort().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link Integer }
         * 
         * 
         */
        public List<Integer> getPort() {
            if (port == null) {
                port = new ArrayList<Integer>();
            }
            return this.port;
        }

    }

}
