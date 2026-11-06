/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import us.ihmc.fastddsjava.natives.fastddsjava;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.gen.PropertyPolicyType;
import us.ihmc.fastddsjava.profiles.gen.PropertyType;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * SROS2: rcl settings semantics, keystore lookup, the exact rmw_fastrtps DDS-Security properties, fail-closed Enforce and
 * Permissive fallback, and (with a DDS-Security build of the natives plus a ROS 2 CLI) secure interop with a ROS 2 listener.
 * <p>
 * The keystore is the research keystore {@value #KEYSTORE} (governance and permissions valid for domains 0-232, enclaves
 * {@code /talker_listener/talker} and {@code /talker_listener/listener}).
 * <p>
 * Secure interop (skipped unless {@link fastddsjava#hasSecurity()} is true and a ROS 2 CLI is installed): integration runs it
 * with natives built with {@code -DSECURITY=ON}, for example the N1 dev build:
 * <pre>
 * flock /mnt/jros2-build/locks/dds.lock env ROS_DISTRO=jazzy ROS_DOMAIN_ID=175 JROS2_NATIVE_PATH=/mnt/jros2-build/natives-dev/linux-x86_64 \
 *    ./gradlew :jros2-test:test --tests 'us.ihmc.jros2.SROS2Test'
 * </pre>
 */
public class SROS2Test
{
   static final String KEYSTORE = "/mnt/jros2-build/research/sros2/keystore_anydomain";
   static final String TALKER = "/talker_listener/talker";
   static final String LISTENER = "/talker_listener/listener";

   static
   {
      jros2.load();
   }

   @AfterEach
   public void clearSystemProperties()
   {
      for (jros2Settings.Key key : jros2Settings.Key.values())
      {
         System.clearProperty(key.property);
      }
   }

   static Function<jros2Settings.Key, String> settings(Object... keyValues)
   {
      return ROS2DiscoveryConfigurationTest.settings(keyValues);
   }

   static Function<jros2Settings.Key, String> enforce(String keystore)
   {
      return settings(jros2Settings.Key.SECURITY_ENABLE, "true", jros2Settings.Key.SECURITY_STRATEGY, "Enforce", jros2Settings.Key.SECURITY_KEYSTORE, keystore);
   }

   private static void assumeKeystore()
   {
      assumeTrue(new File(KEYSTORE, "enclaves/talker_listener/talker/cert.pem").isFile(), "research keystore not available");
   }

   @Test
   public void testDisabledUnlessExactlyTrue()
   {
      for (String value : new String[] {null, "", "True", "TRUE", "1", "yes", " true"})
      {
         ROS2SecurityConfiguration configuration = ROS2SecurityConfiguration.resolve("/",
                                                                                     settings(jros2Settings.Key.SECURITY_ENABLE,
                                                                                              value,
                                                                                              jros2Settings.Key.SECURITY_STRATEGY,
                                                                                              "Enforce"),
                                                                                     () -> true);
         assertEquals(ROS2SecurityConfiguration.Status.DISABLED, configuration.getStatus(), String.valueOf(value));
         assertTrue(configuration.getProperties().isEmpty());
      }
   }

   @Test
   public void testExactRmwFastrtpsProperties()
   {
      assumeKeystore();
      ROS2SecurityConfiguration configuration = ROS2SecurityConfiguration.resolve(TALKER, enforce(KEYSTORE), () -> true);
      assertEquals(ROS2SecurityConfiguration.Status.ENABLED, configuration.getStatus());
      assertTrue(configuration.isEnforce());
      String directory = KEYSTORE + "/enclaves/talker_listener/talker";
      assertEquals(new File(directory), configuration.getSecurityDirectory());

      Map<String, String> expected = new LinkedHashMap<>();
      expected.put("dds.sec.auth.plugin", "builtin.PKI-DH");
      expected.put("dds.sec.auth.builtin.PKI-DH.identity_ca", "file://" + directory + "/identity_ca.cert.pem");
      expected.put("dds.sec.auth.builtin.PKI-DH.identity_certificate", "file://" + directory + "/cert.pem");
      expected.put("dds.sec.auth.builtin.PKI-DH.private_key", "file://" + directory + "/key.pem");
      expected.put("dds.sec.crypto.plugin", "builtin.AES-GCM-GMAC");
      expected.put("dds.sec.access.plugin", "builtin.Access-Permissions");
      expected.put("dds.sec.access.builtin.Access-Permissions.permissions_ca", "file://" + directory + "/permissions_ca.cert.pem");
      expected.put("dds.sec.access.builtin.Access-Permissions.governance", "file://" + directory + "/governance.p7s");
      expected.put("dds.sec.access.builtin.Access-Permissions.permissions", "file://" + directory + "/permissions.p7s");
      // Same order as rmw_fastrtps; symlinks are not resolved (identity_ca.cert.pem is a link into public/)
      assertEquals(new ArrayList<>(expected.entrySet()), new ArrayList<>(configuration.getProperties().entrySet()));
   }

   @Test
   public void testParticipantXmlCarriesTheProperties()
   {
      assumeKeystore();
      ParticipantProfiles.Environment environment = new ParticipantProfiles.Environment(enforce(KEYSTORE), new String[0], () -> true, false, host ->
      {
         throw new java.net.UnknownHostException(host);
      });
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_sec",
                                                                                             "talker",
                                                                                             0,
                                                                                             ROS2NodeOptions.defaults()
                                                                                                            .enclave(TALKER)
                                                                                                            .threadSettings(new ROS2ThreadSettings().priority(0)),
                                                                                             environment);
      PropertyPolicyType policy = configuration.profile.getRtps().getPropertiesPolicy();
      assertNotNull(policy);
      List<PropertyType> properties = ((PropertyPolicyType.Properties) policy.getPropertiesOrBinaryProperties().get(0)).getProperty();
      assertEquals(9, properties.size());
      assertEquals("dds.sec.auth.plugin", properties.get(0).getName());

      ProfilesXML profilesXML = new ProfilesXML();
      profilesXML.addParticipantProfile(configuration.profile);
      String xml = profilesXML.marshall();
      String directory = KEYSTORE + "/enclaves/talker_listener/talker";
      assertTrue(xml.contains("<propertiesPolicy><properties><property><name>dds.sec.auth.plugin</name><value>builtin.PKI-DH</value></property>"), xml);
      assertTrue(xml.contains("<property><name>dds.sec.auth.builtin.PKI-DH.private_key</name><value>file://" + directory + "/key.pem</value></property>"), xml);
      // Security log thread settings only when security is active
      assertTrue(xml.contains("<security_log_thread><priority>0</priority></security_log_thread>"), xml);
      // The enclave user data uses the node's enclave
      assertEquals(TALKER, configuration.enclave);
      assertTrue(ROS2NodePrintout.format(ROS2Node.class, configuration).toString().contains("SROS2 security: enabled (Enforce, " + directory + ")"));
   }

   @Test
   public void testEnclaveOverrideAndRootEnclaveLookup()
   {
      assumeKeystore();
      // ROS_SECURITY_ENCLAVE_OVERRIDE replaces the enclave for the directory lookup only
      ROS2SecurityConfiguration overridden = ROS2SecurityConfiguration.resolve("/",
                                                                               settings(jros2Settings.Key.SECURITY_ENABLE,
                                                                                        "true",
                                                                                        jros2Settings.Key.SECURITY_STRATEGY,
                                                                                        "Enforce",
                                                                                        jros2Settings.Key.SECURITY_KEYSTORE,
                                                                                        KEYSTORE,
                                                                                        jros2Settings.Key.SECURITY_ENCLAVE_OVERRIDE,
                                                                                        LISTENER),
                                                                               () -> true);
      assertEquals(new File(KEYSTORE + "/enclaves/talker_listener/listener"), overridden.getSecurityDirectory());

      // Enclave "/" maps to <keystore>/enclaves, which has no cert.pem: "couldn't find all security files"
      IllegalStateException exception = assertThrows(IllegalStateException.class, () -> ROS2SecurityConfiguration.resolve("/", enforce(KEYSTORE), () -> true));
      assertTrue(exception.getMessage().contains("couldn't find all security files"), exception.getMessage());
      assertTrue(exception.getMessage().contains("cert.pem"), exception.getMessage());

      assertEquals(new File("/ks/enclaves"), ROS2SecurityConfiguration.securityDirectory("/ks", "/"));
      assertEquals(new File("/ks/enclaves/a/b"), ROS2SecurityConfiguration.securityDirectory("/ks/", "/a/b"));
   }

   @Test
   public void testEnforceFailsClosed(@TempDir Path tempDir) throws IOException
   {
      // No keystore
      IllegalStateException noKeystore = assertThrows(IllegalStateException.class,
                                                      () -> ROS2SecurityConfiguration.resolve(TALKER,
                                                                                              settings(jros2Settings.Key.SECURITY_ENABLE,
                                                                                                       "true",
                                                                                                       jros2Settings.Key.SECURITY_STRATEGY,
                                                                                                       "Enforce"),
                                                                                              () -> true));
      assertTrue(noKeystore.getMessage().contains("ROS_SECURITY_KEYSTORE"));

      // Missing enclave directory (rcl: "SECURITY ERROR: directory '...' does not exist.")
      IllegalStateException noDirectory = assertThrows(IllegalStateException.class,
                                                       () -> ROS2SecurityConfiguration.resolve("/no/such/enclave", enforce(tempDir.toString()), () -> true));
      assertTrue(noDirectory.getMessage().contains("does not exist"), noDirectory.getMessage());

      // Missing files
      Path enclave = Files.createDirectories(tempDir.resolve("enclaves").resolve("e"));
      for (String file : new String[] {"identity_ca.cert.pem", "cert.pem", "key.pem", "permissions_ca.cert.pem", "governance.p7s"})
      {
         Files.writeString(enclave.resolve(file), "x");
      }
      IllegalStateException missing = assertThrows(IllegalStateException.class, () -> ROS2SecurityConfiguration.resolve("/e", enforce(tempDir.toString()), () -> true));
      assertTrue(missing.getMessage().contains("permissions.p7s"), missing.getMessage());

      // All files present but natives without DDS-Security
      Files.writeString(enclave.resolve("permissions.p7s"), "x");
      IllegalStateException noNativeSecurity = assertThrows(IllegalStateException.class,
                                                            () -> ROS2SecurityConfiguration.resolve("/e", enforce(tempDir.toString()), () -> false));
      assertTrue(noNativeSecurity.getMessage().contains("without DDS-Security"), noNativeSecurity.getMessage());

      // Everything present with security natives: enabled
      assertEquals(ROS2SecurityConfiguration.Status.ENABLED, ROS2SecurityConfiguration.resolve("/e", enforce(tempDir.toString()), () -> true).getStatus());
   }

   /**
    * The Fast-DDS of ROS 2 Foxy, Galactic and Humble only parses the legacy certificate algorithm names in the DDS-Security
    * handshake ("Remote participant PermissionsCA algorithm differs from local" otherwise), so AUTO announces them for those
    * ROS_DISTRO values; Jazzy and newer accept both forms and keep Fast-DDS' default (the DDS-Security 1.1 names).
    */
   @Test
   public void testLegacyAlgorithmNamesFollowRosDistro(@TempDir Path tempDir) throws IOException
   {
      Path enclave = Files.createDirectories(tempDir.resolve("enclaves").resolve("e"));
      for (String file : new String[] {"identity_ca.cert.pem", "cert.pem", "key.pem", "permissions_ca.cert.pem", "governance.p7s", "permissions.p7s"})
      {
         Files.writeString(enclave.resolve(file), "x");
      }
      String keystore = tempDir.toString();

      for (String distro : new String[] {"foxy", "galactic", "humble"})
      {
         Map<String, String> properties = resolveEnforce(keystore, distro, null).getProperties();
         assertEquals("true", properties.get(ROS2SecurityConfiguration.LEGACY_AUTHENTICATION_ALGORITHMS), distro);
         assertEquals("true", properties.get(ROS2SecurityConfiguration.LEGACY_ACCESS_CONTROL_ALGORITHMS), distro);
         assertEquals(11, properties.size(), distro);
      }
      for (String distro : new String[] {null, "", "iron", "jazzy", "kilted", "lyrical", "rolling", "humbled"})
      {
         Map<String, String> properties = resolveEnforce(keystore, distro, "AUTO").getProperties();
         assertFalse(properties.containsKey(ROS2SecurityConfiguration.LEGACY_AUTHENTICATION_ALGORITHMS), String.valueOf(distro));
         assertFalse(properties.containsKey(ROS2SecurityConfiguration.LEGACY_ACCESS_CONTROL_ALGORITHMS), String.valueOf(distro));
         assertEquals(9, properties.size(), String.valueOf(distro));
      }

      // An explicit setting wins over ROS_DISTRO (case-insensitive)
      assertTrue(resolveEnforce(keystore, "jazzy", "true").getProperties().containsKey(ROS2SecurityConfiguration.LEGACY_ACCESS_CONTROL_ALGORITHMS));
      assertFalse(resolveEnforce(keystore, "humble", "FALSE").getProperties().containsKey(ROS2SecurityConfiguration.LEGACY_AUTHENTICATION_ALGORITHMS));
      assertTrue(resolveEnforce(keystore, "humble", "auto").getProperties().containsKey(ROS2SecurityConfiguration.LEGACY_AUTHENTICATION_ALGORITHMS));
      IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class, () -> resolveEnforce(keystore, "humble", "yes"));
      assertTrue(invalid.getMessage().contains("JROS2_SECURITY_LEGACY_ALGORITHMS"), invalid.getMessage());

      // Only secured participants get the properties
      ROS2SecurityConfiguration disabled = ROS2SecurityConfiguration.resolve("/e",
                                                                             settings(jros2Settings.Key.ROS_DISTRO,
                                                                                      "humble",
                                                                                      jros2Settings.Key.SECURITY_LEGACY_ALGORITHMS,
                                                                                      "true"),
                                                                             () -> true);
      assertEquals(ROS2SecurityConfiguration.Status.DISABLED, disabled.getStatus());
      assertTrue(disabled.getProperties().isEmpty());

      // The node printout says so
      ParticipantProfiles.Environment environment = new ParticipantProfiles.Environment(settings(jros2Settings.Key.SECURITY_ENABLE,
                                                                                                 "true",
                                                                                                 jros2Settings.Key.SECURITY_STRATEGY,
                                                                                                 "Enforce",
                                                                                                 jros2Settings.Key.SECURITY_KEYSTORE,
                                                                                                 keystore,
                                                                                                 jros2Settings.Key.ROS_DISTRO,
                                                                                                 "humble"), new String[0], () -> true, false, host ->
      {
         throw new java.net.UnknownHostException(host);
      });
      ParticipantProfiles.ParticipantConfiguration configuration = ParticipantProfiles.build("p_legacy",
                                                                                             "talker",
                                                                                             0,
                                                                                             ROS2NodeOptions.defaults().enclave("/e"),
                                                                                             environment);
      String printout = ROS2NodePrintout.format(ROS2Node.class, configuration).toString();
      assertTrue(printout.contains("SROS2 security: enabled (Enforce, " + enclave + ", legacy algorithm names)"), printout);
   }

   private static ROS2SecurityConfiguration resolveEnforce(String keystore, String rosDistro, String legacySetting)
   {
      return ROS2SecurityConfiguration.resolve("/e",
                                               settings(jros2Settings.Key.SECURITY_ENABLE,
                                                        "true",
                                                        jros2Settings.Key.SECURITY_STRATEGY,
                                                        "Enforce",
                                                        jros2Settings.Key.SECURITY_KEYSTORE,
                                                        keystore,
                                                        jros2Settings.Key.ROS_DISTRO,
                                                        rosDistro,
                                                        jros2Settings.Key.SECURITY_LEGACY_ALGORITHMS,
                                                        legacySetting),
                                               () -> true);
   }

   @Test
   public void testEnforceFailsClosedWithCurrentNatives()
   {
      assumeKeystore();
      assumeTrue(!fastddsjava.hasSecurity(), "the loaded natives support DDS-Security");
      // The real native capability check: the bundled (ABI v1 / SECURITY=OFF) natives must make Enforce fail
      IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                     () -> ROS2SecurityConfiguration.resolve(TALKER, enforce(KEYSTORE), fastddsjava::hasSecurity));
      assertTrue(exception.getMessage().contains("Enforce"), exception.getMessage());

      // End to end through ROS2Node: no participant is created
      System.setProperty(jros2Settings.Key.SECURITY_ENABLE.property, "true");
      System.setProperty(jros2Settings.Key.SECURITY_STRATEGY.property, "Enforce");
      System.setProperty(jros2Settings.Key.SECURITY_KEYSTORE.property, KEYSTORE);
      assertThrows(IllegalStateException.class, () -> new ROS2Node("sros2_enforce_node", ROS2NodeOptions.defaults().enclave(TALKER)).close());
   }

   @Test
   public void testPermissiveWarnsAndRunsUnsecured()
   {
      List<String> logged = new ArrayList<>();
      Handler handler = new Handler()
      {
         @Override
         public void publish(LogRecord record)
         {
            if (record.getLevel().intValue() >= Level.WARNING.intValue())
            {
               logged.add(record.getMessage());
            }
         }

         @Override
         public void flush()
         {
         }

         @Override
         public void close()
         {
         }
      };

      Function<jros2Settings.Key, String> permissive = settings(jros2Settings.Key.SECURITY_ENABLE,
                                                                "true",
                                                                jros2Settings.Key.SECURITY_STRATEGY,
                                                                "Permissive",
                                                                jros2Settings.Key.SECURITY_KEYSTORE,
                                                                "/no/such/keystore");
      ROS2SecurityConfiguration configuration = ROS2SecurityConfiguration.resolve(TALKER, permissive, () -> true);
      assertEquals(ROS2SecurityConfiguration.Status.PERMISSIVE_UNSECURED, configuration.getStatus());
      assertTrue(configuration.getProperties().isEmpty());
      assertEquals(1, configuration.getWarnings().size());
      assertTrue(configuration.getWarnings().get(0).contains("WITHOUT security"));

      // Any strategy other than exactly "Enforce" is permissive
      for (String strategy : new String[] {null, "", "enforce", "ENFORCE"})
      {
         assertEquals(ROS2SecurityConfiguration.Status.PERMISSIVE_UNSECURED,
                      ROS2SecurityConfiguration.resolve(TALKER,
                                                        settings(jros2Settings.Key.SECURITY_ENABLE, "true", jros2Settings.Key.SECURITY_STRATEGY, strategy),
                                                        () -> true).getStatus());
      }

      // End to end: the node starts unsecured and the warning is logged
      jros2.getLogger().addHandler(handler);
      try
      {
         System.setProperty(jros2Settings.Key.SECURITY_ENABLE.property, "true");
         System.setProperty(jros2Settings.Key.SECURITY_STRATEGY.property, "Permissive");
         System.setProperty(jros2Settings.Key.SECURITY_KEYSTORE.property, "/no/such/keystore");
         ROS2Node node = new ROS2Node("sros2_permissive_node", ROS2NodeOptions.defaults().enclave(TALKER));
         node.close();
      }
      finally
      {
         jros2.getLogger().removeHandler(handler);
      }
      assertTrue(logged.stream().anyMatch(message -> message.contains("WITHOUT security")), logged.toString());
   }

   @Test
   public void testPkcs11AndCrlAndLogging(@TempDir Path tempDir) throws IOException
   {
      Path enclave = Files.createDirectories(tempDir.resolve("enclaves"));
      for (String file : new String[] {"identity_ca.cert.pem", "cert.pem", "permissions_ca.cert.pem", "governance.p7s", "permissions.p7s", "crl.pem"})
      {
         Files.writeString(enclave.resolve(file), "x");
      }
      // key.p11 with a pkcs11 URI wins over a missing key.pem; a .p11 without the prefix is ignored
      Files.writeString(enclave.resolve("key.p11"), "  pkcs11:object=key;type=private  trailing\n");
      Files.writeString(enclave.resolve("cert.p11"), "not-a-pkcs11-uri");

      Function<jros2Settings.Key, String> withLogging = settings(jros2Settings.Key.SECURITY_ENABLE,
                                                                 "true",
                                                                 jros2Settings.Key.SECURITY_KEYSTORE,
                                                                 tempDir.toString(),
                                                                 jros2Settings.Key.SECURITY_LOG_FILE,
                                                                 "/tmp/sec.log",
                                                                 jros2Settings.Key.SECURITY_LOG_PUBLISH,
                                                                 "false",
                                                                 jros2Settings.Key.SECURITY_LOG_VERBOSITY,
                                                                 "warn");
      ROS2SecurityConfiguration configuration = ROS2SecurityConfiguration.resolve("/", withLogging, () -> true);
      Map<String, String> properties = configuration.getProperties();
      assertFalse(configuration.isEnforce());
      assertEquals("pkcs11:object=key;type=private", properties.get("dds.sec.auth.builtin.PKI-DH.private_key"));
      // file:// URIs have '/' separators on every platform
      String enclaveUri = "file://" + enclave.toString().replace('\\', '/');
      assertEquals(enclaveUri + "/cert.pem", properties.get("dds.sec.auth.builtin.PKI-DH.identity_certificate"));
      assertEquals(enclaveUri + "/crl.pem", properties.get("dds.sec.auth.builtin.PKI-DH.identity_crl"));
      assertEquals("/tmp/sec.log", properties.get("dds.sec.log.builtin.DDS_LogTopic.log_file"));
      assertEquals("false", properties.get("dds.sec.log.builtin.DDS_LogTopic.distribute"));
      assertEquals("WARNING_LEVEL", properties.get("dds.sec.log.builtin.DDS_LogTopic.logging_level"));
      assertEquals("builtin.DDS_LogTopic", properties.get("dds.sec.log.plugin"));
      List<String> names = new ArrayList<>(properties.keySet());
      assertEquals("dds.sec.log.plugin", names.get(names.size() - 1));

      // Invalid logging values are errors (rmw_fastrtps fails participant creation)
      assertThrows(IllegalArgumentException.class,
                   () -> ROS2SecurityConfiguration.resolve("/",
                                                           settings(jros2Settings.Key.SECURITY_ENABLE,
                                                                    "true",
                                                                    jros2Settings.Key.SECURITY_KEYSTORE,
                                                                    tempDir.toString(),
                                                                    jros2Settings.Key.SECURITY_LOG_PUBLISH,
                                                                    "yes"),
                                                           () -> true));
      assertThrows(IllegalArgumentException.class,
                   () -> ROS2SecurityConfiguration.resolve("/",
                                                           settings(jros2Settings.Key.SECURITY_ENABLE,
                                                                    "true",
                                                                    jros2Settings.Key.SECURITY_KEYSTORE,
                                                                    tempDir.toString(),
                                                                    jros2Settings.Key.SECURITY_LOG_VERBOSITY,
                                                                    "LOUD"),
                                                           () -> true));
      for (String[] mapping : new String[][] {{"FATAL", "EMERGENCY_LEVEL"}, {"error", "ERROR_LEVEL"}, {"INFO", "INFORMATIONAL_LEVEL"}, {"Debug", "DEBUG_LEVEL"}})
      {
         assertEquals(mapping[1],
                      ROS2SecurityConfiguration.resolve("/",
                                                        settings(jros2Settings.Key.SECURITY_ENABLE,
                                                                 "true",
                                                                 jros2Settings.Key.SECURITY_KEYSTORE,
                                                                 tempDir.toString(),
                                                                 jros2Settings.Key.SECURITY_LOG_VERBOSITY,
                                                                 mapping[0]),
                                                        () -> true).getProperties().get("dds.sec.log.builtin.DDS_LogTopic.logging_level"));
      }
   }

   /**
    * Secure interop: a jros2 talker in enclave /talker_listener/talker and a ROS 2 C++ listener in /talker_listener/listener,
    * both with ROS_SECURITY_STRATEGY=Enforce. Needs natives built with DDS-Security and a ROS 2 installation with demo_nodes_cpp.
    * The jros2 node takes ROS_DISTRO from the environment, so with Humble it announces the legacy algorithm names (without them
    * the Humble listener rejects it with "Remote participant PermissionsCA algorithm differs from local").
    */
   @Test
   @Timeout(120)
   public void testSecureInteropWithROS2Listener() throws Exception
   {
      assumeKeystore();
      assumeTrue(fastddsjava.hasSecurity(), "natives without DDS-Security (build with -DSECURITY=ON and load them with JROS2_NATIVE_PATH)");
      assumeTrue(ROS2TestTools.isROS2CLIAvailable(), "no ROS 2 CLI");
      // The Fast-DDS of Foxy (2.1) and Galactic (2.3) completes no DDS-Security handshake with jros2's Fast-DDS 3.6, not even with
      // the legacy algorithm names; a Humble rmw_fastrtps talker cannot reach a secure Foxy listener either. (Humble works: the
      // legacy names are selected automatically for ROS_DISTRO=humble.)
      assumeTrue(!ROS2TestTools.ROS_DISTRO.equals("foxy") && !ROS2TestTools.ROS_DISTRO.equals("galactic"),
                 "no secure interop between Fast-DDS 3 and the Fast-DDS of " + ROS2TestTools.ROS_DISTRO);

      System.setProperty(jros2Settings.Key.SECURITY_ENABLE.property, "true");
      System.setProperty(jros2Settings.Key.SECURITY_STRATEGY.property, "Enforce");
      System.setProperty(jros2Settings.Key.SECURITY_KEYSTORE.property, KEYSTORE);

      ROS2Node node = new ROS2Node("talker", ROS2NodeOptions.defaults().enclave(TALKER));
      ROS2Publisher<std_msgs.String_> publisher = node.createPublisher(new ROS2Topic<>("/chatter", std_msgs.String_.class));
      // The demo listener of ROS 2 Lyrical and newer subscribes to example_interfaces/msg/String instead of std_msgs/msg/String.
      // One participant cannot use two types on one topic, so a second node (same enclave) publishes that type.
      ROS2Node exampleInterfacesNode = new ROS2Node("talker_example_interfaces", ROS2NodeOptions.defaults().enclave(TALKER));
      ROS2Publisher<example_interfaces.String_> exampleInterfacesPublisher = exampleInterfacesNode.createPublisher(new ROS2Topic<>("/chatter",
                                                                                                                                  example_interfaces.String_.class));

      ProcessBuilder processBuilder = new ProcessBuilder("bash",
                                                         "-c",
                                                         "source /opt/ros/" + ROS2TestTools.ROS_DISTRO + "/setup.bash && exec ros2 run demo_nodes_cpp listener --ros-args --enclave "
                                                         + LISTENER);
      processBuilder.environment().put("ROS_DOMAIN_ID", String.valueOf(node.getDomainId()));
      processBuilder.environment().put("ROS_SECURITY_ENABLE", "true");
      processBuilder.environment().put("ROS_SECURITY_STRATEGY", "Enforce");
      processBuilder.environment().put("ROS_SECURITY_KEYSTORE", KEYSTORE);
      // Fast-DDS, also on distros whose default middleware is another one (Galactic: Cyclone DDS)
      processBuilder.environment().put("RMW_IMPLEMENTATION", "rmw_fastrtps_cpp");
      processBuilder.redirectErrorStream(true);
      Process listener = processBuilder.start();
      AtomicBoolean heard = new AtomicBoolean();
      Thread reader = new Thread(() ->
      {
         try (BufferedReader output = new BufferedReader(new InputStreamReader(listener.getInputStream(), StandardCharsets.UTF_8)))
         {
            String line;
            while ((line = output.readLine()) != null)
            {
               System.out.println("[secure listener] " + line);
               if (line.contains("I heard: [secure hello"))
               {
                  heard.set(true);
               }
            }
         }
         catch (IOException ignored)
         {
         }
      });
      reader.setDaemon(true);
      reader.start();

      try
      {
         std_msgs.String_ message = new std_msgs.String_();
         example_interfaces.String_ exampleInterfacesMessage = new example_interfaces.String_();
         long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
         int count = 0;
         while (!heard.get() && System.nanoTime() < deadline)
         {
            message.getData().setLength(0);
            message.getData().append("secure hello ").append(count);
            publisher.publish(message);
            exampleInterfacesMessage.getData().setLength(0);
            exampleInterfacesMessage.getData().append("secure hello ").append(count++);
            exampleInterfacesPublisher.publish(exampleInterfacesMessage);
            Thread.sleep(200);
         }
         assertTrue(heard.get(), "The secure ROS 2 listener did not receive the jros2 talker's messages");
      }
      finally
      {
         listener.destroy();
         listener.waitFor(10, TimeUnit.SECONDS);
         listener.destroyForcibly();
         exampleInterfacesNode.close();
         node.close();
      }
   }
}
