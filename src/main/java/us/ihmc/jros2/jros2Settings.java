package us.ihmc.jros2;

interface jros2Settings
{
   /**
    * The default domain ID jros2 will use when constructing new {@link ROS2Node}.
    * See: <a href="https://docs.ros.org/en/humble/Concepts/Intermediate/About-Domain-ID.html#the-ros-domain-id">ROS 2 Domain ID</a>.
    */
   int defaultDomainId();

   /**
    * @return Whether a default domain ID has been specified.
    */
   boolean hasDefaultDomainId();

   /**
    * A list of addresses (IPv4 or IPv6) which correspond to network interfaces on the host system.
    * {@link ROS2Node} will only be able to communicate using whitelisted network interfaces.
    * Empty or null for no whitelist.
    * This is a Fast-DDS parameter. See: <a href="https://fast-dds.docs.eprosima.com/en/latest/fastdds/transport/whitelist.html">Interface Whitelist</a>.
    */
   String[] interfaceWhitelist();

   /**
    * @return Whether an interface whitelist has been specified.
    */
   boolean hasInterfaceWhitelist();
}
