/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.natives.fastddsjava;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static us.ihmc.jros2.AndroidTestSupport.DISCOVERY_TIMEOUT_MS;
import static us.ihmc.jros2.AndroidTestSupport.domainId;
import static us.ihmc.jros2.AndroidTestSupport.publishUntil;
import static us.ihmc.jros2.AndroidTestSupport.uniqueNodeName;
import static us.ihmc.jros2.AndroidTestSupport.uniqueTopicName;
import static us.ihmc.jros2.AndroidTestSupport.waitUntil;

/**
 * SROS2 (DDS-Security with the statically linked OpenSSL of the Android natives) on a device: authentication, access
 * control and encryption between two participants, isolation from a participant without security, and fail-closed
 * Enforce for an enclave without security files.
 * <p>
 * Needs an SROS2 keystore with the enclaves {@value #TALKER} and {@value #LISTENER} whose permissions allow publishing
 * and subscribing {@code rt/*} on the test domain, for example made with
 * <pre>
 * ros2 security create_keystore keystore
 * ros2 security create_enclave keystore /talker_listener/talker
 * ros2 security create_enclave keystore /talker_listener/listener
 * </pre>
 * It is packaged into the instrumentation APK with {@code -Pjros2.sros2Keystore=<keystore dir>} (android/build.gradle.kts,
 * the CA private keys under {@code private/} are left out). Without it every test is skipped.
 */
@RunWith(AndroidJUnit4.class)
public class SROS2OnDeviceTest
{
   static final String ASSET_DIRECTORY = "sros2_keystore";
   static final String TALKER = "/talker_listener/talker";
   static final String LISTENER = "/talker_listener/listener";

   @Rule
   public Timeout timeout = new Timeout(180, TimeUnit.SECONDS);

   private final List<ROS2Node> nodes = new ArrayList<>();
   private File keystore;

   @Before
   public void installKeystore() throws IOException
   {
      Context context = InstrumentationRegistry.getInstrumentation().getContext(); // the instrumentation APK holds the assets
      String[] entries = context.getAssets().list(ASSET_DIRECTORY);
      assumeTrue("no SROS2 keystore packaged; run with -Pjros2.sros2Keystore=<keystore dir>", entries != null && entries.length > 0);
      // fastddsjava.hasSecurity() can only answer once the natives are loaded (this class may run first).
      assertTrue("fastddsjavaNativeLibrary.load() failed; see logcat", fastddsjavaNativeLibrary.load());

      keystore = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(), ASSET_DIRECTORY);
      deleteRecursively(keystore);
      copyAssets(context.getAssets(), ASSET_DIRECTORY, keystore);
      assertTrue("keystore lacks the talker enclave", new File(keystore, "enclaves" + TALKER + "/cert.pem").isFile());
      assertTrue("keystore lacks the listener enclave", new File(keystore, "enclaves" + LISTENER + "/cert.pem").isFile());
   }

   @After
   public void closeNodesAndDisableSecurity()
   {
      AndroidTestSupport.closeQuietly(nodes);
      nodes.clear();
      disableSecurity();
   }

   private void enableSecurity(String strategy)
   {
      System.setProperty(jros2Settings.Key.SECURITY_ENABLE.property, "true");
      System.setProperty(jros2Settings.Key.SECURITY_STRATEGY.property, strategy);
      System.setProperty(jros2Settings.Key.SECURITY_KEYSTORE.property, keystore.getAbsolutePath());
   }

   private static void disableSecurity()
   {
      System.clearProperty(jros2Settings.Key.SECURITY_ENABLE.property);
      System.clearProperty(jros2Settings.Key.SECURITY_STRATEGY.property);
      System.clearProperty(jros2Settings.Key.SECURITY_KEYSTORE.property);
   }

   /**
    * @param enclave the SROS2 enclave, or null for the default enclave "/".
    */
   private ROS2Node node(String name, String enclave)
   {
      ROS2NodeOptions options = ROS2NodeOptions.defaults().domainId(domainId());
      if (enclave != null)
      {
         options.enclave(enclave);
      }
      ROS2Node node = new ROS2Node(uniqueNodeName(name), options);
      nodes.add(node);
      return node;
   }

   private static ROS2QoSProfile reliable()
   {
      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.reliability(ROS2QoSProfile.Reliability.RELIABLE);
      qos.durability(ROS2QoSProfile.Durability.VOLATILE);
      qos.history(ROS2QoSProfile.History.KEEP_LAST);
      qos.depth(10);
      return qos;
   }

   @Test
   public void securedTalkerAndListenerCommunicate()
   {
      assertTrue("the bundled natives were built without DDS-Security", fastddsjava.hasSecurity());
      enableSecurity("Enforce");

      ROS2Node talker = node("sros2_talker", TALKER);
      ROS2Node listener = node("sros2_listener", LISTENER);
      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(uniqueTopicName("sros2_chatter"), std_msgs.String_.class);
      AtomicReference<String> received = new AtomicReference<>();
      ROS2Subscription<std_msgs.String_> subscription = listener.createSubscription(topic, reader ->
      {
         std_msgs.String_ message = reader.read();
         if (message != null)
         {
            received.compareAndSet(null, message.getDataAsString());
         }
      }, reliable());
      ROS2Publisher<std_msgs.String_> publisher = talker.createPublisher(topic, reliable());

      // Authentication (PKI-DH handshake) and the permission checks happen before the endpoints match.
      assertTrue("secured endpoints did not match", publisher.waitForSubscription(DISCOVERY_TIMEOUT_MS) && subscription.waitForPublisher(DISCOVERY_TIMEOUT_MS));
      std_msgs.String_ message = new std_msgs.String_();
      message.getData().append("encrypted hello ✓");
      assertTrue("no message over the secured (encrypted) channel", publishUntil(publisher, message, () -> received.get() != null, 200));
      assertEquals("encrypted hello ✓", received.get());
   }

   /**
    * The keystore's governance does not allow unauthenticated participants, so a node without security must neither match
    * nor receive anything from a secured talker.
    */
   @Test
   public void unsecuredNodeIsIsolatedFromSecuredNodes()
   {
      assertTrue("the bundled natives were built without DDS-Security", fastddsjava.hasSecurity());
      enableSecurity("Enforce");
      ROS2Node talker = node("sros2_talker", TALKER);
      disableSecurity();
      ROS2Node plain = node("plain_listener", null);

      ROS2Topic<std_msgs.String_> topic = new ROS2Topic<>(uniqueTopicName("sros2_isolated"), std_msgs.String_.class);
      AtomicReference<String> received = new AtomicReference<>();
      ROS2Subscription<std_msgs.String_> subscription = plain.createSubscription(topic, reader ->
      {
         std_msgs.String_ message = reader.read();
         if (message != null)
         {
            received.compareAndSet(null, message.getDataAsString());
         }
      }, reliable());
      ROS2Publisher<std_msgs.String_> publisher = talker.createPublisher(topic, reliable());

      std_msgs.String_ message = new std_msgs.String_();
      message.getData().append("secret");
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (System.nanoTime() < deadline)
      {
         publisher.publish(message);
         waitUntil(() -> received.get() != null, 100);
      }
      assertFalse("the unsecured subscription matched the secured publisher", subscription.waitForPublisher(1));
      assertNull("an unsecured node received data from a secured node", received.get());
   }

   /**
    * Enforce with an enclave that has no directory, or a directory without the security files, must refuse to create the
    * participant instead of falling back to an unsecured one (rcl's ROS_SECURITY_STRATEGY=Enforce semantics).
    */
   @Test
   public void enforceFailsClosedForEnclaveWithoutSecurityFiles()
   {
      assertTrue("the bundled natives were built without DDS-Security", fastddsjava.hasSecurity());
      File emptyEnclave = new File(keystore, "enclaves/empty_enclave");
      assertTrue(emptyEnclave.mkdirs());
      enableSecurity("Enforce");

      for (String enclave : new String[] {"/no_such_enclave", "/empty_enclave"})
      {
         try
         {
            node("sros2_enforce", enclave);
            fail("Enforce created a node for the enclave " + enclave + " without security files");
         }
         catch (IllegalStateException expected)
         {
            assertTrue(expected.getMessage(), expected.getMessage() != null && expected.getMessage().contains("Enforce"));
         }
      }
      assertTrue("no node may have been created", nodes.isEmpty());
   }

   private static void copyAssets(AssetManager assets, String path, File destination) throws IOException
   {
      String[] children = assets.list(path);
      if (children != null && children.length > 0)
      {
         if (!destination.isDirectory() && !destination.mkdirs())
         {
            throw new IOException("cannot create " + destination);
         }
         for (String child : children)
         {
            copyAssets(assets, path + "/" + child, new File(destination, child));
         }
         return;
      }

      try (InputStream in = assets.open(path); OutputStream out = new FileOutputStream(destination))
      {
         byte[] buffer = new byte[8192];
         int read;
         while ((read = in.read(buffer)) != -1)
         {
            out.write(buffer, 0, read);
         }
      }
   }

   private static void deleteRecursively(File file)
   {
      File[] children = file.listFiles();
      if (children != null)
      {
         for (File child : children)
         {
            deleteRecursively(child);
         }
      }
      if (file.exists() && !file.delete())
      {
         throw new IllegalStateException("cannot delete " + file);
      }
   }
}
