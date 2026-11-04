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

import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A shared DDS participant for several {@link ROS2Node}s, like an rclcpp context.
 * <p>
 * By default every {@link ROS2Node} creates its own DDS participant, which costs threads, sockets, memory and discovery
 * traffic per node. Nodes created with {@link ROS2NodeOptions#context(ROS2Context)} instead share the participant of
 * the context: topics and types are registered once per participant (reference-counted), and one ROS graph writer
 * announces all nodes of the context.
 * <pre>{@code
 * try (ROS2Context context = ROS2Context.create(ROS2NodeOptions.defaults().domainId(42)))
 * {
 *    ROS2Node talker = new ROS2Node("talker", ROS2NodeOptions.defaults().context(context));
 *    ROS2Node listener = new ROS2Node("listener", ROS2NodeOptions.defaults().namespace("/robot").context(context));
 *    ...
 * } // closes both nodes, then the participant
 * }</pre>
 * Participant-level options (domain ID, transports, shared-memory segment size, thread settings, enclave, discovery range
 * and static peers) are taken from the options the context was created with. Node options that use the context may repeat
 * them with the same values but must not set different ones. Node-level options (namespace, callback executor) stay per node.
 * <p>
 * Because the nodes share one participant, a subscription callback of any node of the context counts as a callback of
 * every node of the context (see {@link ROS2Node} about creating endpoints from callbacks), and one topic name can only
 * have one message type within the context.
 */
public final class ROS2Context implements Closeable
{
   static
   {
      jros2.load();
   }

   private static final AtomicLong contextIdCounter = new AtomicLong(0);
   private static final long CLOSE_WAIT_TIMEOUT_MS = 30000;

   private final ROS2NodeOptions options;
   private final int domainId;
   private final ROS2Participant participant;
   private final List<ROS2Node> nodes = new ArrayList<>();
   private boolean closed = false; // guarded by nodes
   private final AtomicBoolean closeRequested = new AtomicBoolean(false);
   private final CountDownLatch closeDone = new CountDownLatch(1);
   private final Thread shutdownHook;

   private ROS2Context(ROS2NodeOptions options)
   {
      this.options = options == null ? ROS2NodeOptions.defaults() : new ROS2NodeOptions(options);
      if (this.options.getContext() != null)
      {
         throw new IllegalArgumentException("The options of a ROS2Context must not reference another context");
      }

      int domainId = this.options.getDomainId() != null ? this.options.getDomainId() : jros2.get().rosDomainId();
      if (domainId < 0 || domainId > 232)
      {
         throw new IllegalArgumentException("Invalid domain ID used when constructing a ROS2Context (" + domainId + "); it must be in [0, 232]");
      }
      this.domainId = domainId;

      participant = ROS2Participant.create("jros2_context_" + contextIdCounter.getAndIncrement(), domainId, this.options, ROS2Node.class);

      shutdownHook = new Thread(this::close, "ROS2ContextShutdownHook");
      Runtime.getRuntime().addShutdownHook(shutdownHook);
   }

   /**
    * Creates a context whose participant uses the participant-level settings of {@code options}
    * (domain, transports, shared-memory segment size, thread settings, enclave, discovery). Unset options fall back to
    * the jros2 settings.
    *
    * @param options participant-level options; may be null for the defaults
    * @throws IllegalArgumentException if the domain ID is invalid
    * @throws IllegalStateException    if Fast-DDS cannot create the participant
    */
   public static ROS2Context create(ROS2NodeOptions options)
   {
      return new ROS2Context(options);
   }

   /**
    * Creates a context with the default settings (ROS_DOMAIN_ID, builtin transports, ...).
    */
   public static ROS2Context create()
   {
      return new ROS2Context(null);
   }

   /**
    * @return the domain ID of the shared participant
    */
   public int getDomainId()
   {
      return domainId;
   }

   /**
    * @return an unmodifiable snapshot of the nodes that currently use this context
    */
   public List<ROS2Node> getNodes()
   {
      synchronized (nodes)
      {
         return Collections.unmodifiableList(new ArrayList<>(nodes));
      }
   }

   /**
    * @return true once {@link #close()} has been called
    */
   public boolean isClosed()
   {
      synchronized (nodes)
      {
         return closed;
      }
   }

   ROS2Participant getParticipant()
   {
      return participant;
   }

   /**
    * Checks that the options of a node created with this context do not ask for different participant-level settings.
    *
    * @throws IllegalArgumentException if they do
    */
   void checkNodeOptions(ROS2NodeOptions nodeOptions)
   {
      if (nodeOptions.getDomainId() != null && nodeOptions.getDomainId() != domainId)
      {
         throw conflict("domainId", nodeOptions.getDomainId(), domainId);
      }
      if (nodeOptions.getTransports() != null && !sameTransports(nodeOptions.getTransports(), options.getTransports()))
      {
         throw conflict("transports", describe(nodeOptions.getTransports()), describe(options.getTransports()));
      }
      if (nodeOptions.getShmSegmentSize() != null && !nodeOptions.getShmSegmentSize().equals(options.getShmSegmentSize()))
      {
         throw conflict("shmSegmentSize", nodeOptions.getShmSegmentSize(), options.getShmSegmentSize());
      }
      if (nodeOptions.getEnclave() != null && !nodeOptions.getEnclave().equals(options.getEnclave()))
      {
         throw conflict("enclave", nodeOptions.getEnclave(), options.getEnclave());
      }
      if (nodeOptions.getDiscoveryRange() != null && nodeOptions.getDiscoveryRange() != options.getDiscoveryRange())
      {
         throw conflict("discoveryRange", nodeOptions.getDiscoveryRange(), options.getDiscoveryRange());
      }
      if (nodeOptions.getStaticPeers() != null && !Arrays.equals(nodeOptions.getStaticPeers(), options.getStaticPeers()))
      {
         throw conflict("staticPeers", Arrays.toString(nodeOptions.getStaticPeers()), Arrays.toString(options.getStaticPeers()));
      }
      if (nodeOptions.getThreadSettings() != null && !sameThreadSettings(nodeOptions.getThreadSettings(), options.getThreadSettings()))
      {
         throw conflict("threadSettings", "(node value)", "(context value)");
      }
   }

   private static IllegalArgumentException conflict(String option, Object nodeValue, Object contextValue)
   {
      return new IllegalArgumentException("ROS2NodeOptions." + option + " is a participant-level option and is taken from the ROS2Context; the node asks for "
                                          + nodeValue + " but the context uses " + contextValue
                                          + ". Set it when creating the context, or leave it unset in the node options.");
   }

   /**
    * Transports are the same when they are the same descriptors or have the same transport IDs: Fast-DDS identifies a
    * loaded transport descriptor by its ID, and descriptors have no value equality.
    */
   static boolean sameTransports(TransportDescriptorType[] a, TransportDescriptorType[] b)
   {
      if (a == null || b == null)
      {
         return a == b;
      }
      if (a.length != b.length)
      {
         return false;
      }
      for (int i = 0; i < a.length; ++i)
      {
         if (a[i] == b[i])
         {
            continue;
         }
         if (a[i] == null || b[i] == null || a[i].getTransportId() == null || !a[i].getTransportId().equals(b[i].getTransportId()))
         {
            return false;
         }
      }
      return true;
   }

   private static String describe(TransportDescriptorType[] transports)
   {
      if (transports == null)
      {
         return "the default transports";
      }
      List<String> ids = new ArrayList<>();
      for (TransportDescriptorType transport : transports)
      {
         ids.add(transport == null ? "null" : transport.getTransportId());
      }
      return ids.toString();
   }

   private static boolean sameThreadSettings(ROS2ThreadSettings a, ROS2ThreadSettings b)
   {
      if (a == b)
      {
         return true;
      }
      if (a == null || b == null)
      {
         return false;
      }
      if (declaresMethod("equals", Object.class))
      {
         return a.equals(b);
      }
      if (a.getSchedulingPolicy() != b.getSchedulingPolicy() || a.getPriority() != b.getPriority() || a.getAffinityMask() != b.getAffinityMask()
          || a.getStackSizeBytes() != b.getStackSizeBytes())
      {
         return false;
      }
      // Settings that are not part of the getters above (e.g. per thread kind) show up in the description
      return !declaresMethod("toString") || a.toString().equals(b.toString());
   }

   private static boolean declaresMethod(String name, Class<?>... parameterTypes)
   {
      try
      {
         return ROS2ThreadSettings.class.getMethod(name, parameterTypes).getDeclaringClass() == ROS2ThreadSettings.class;
      }
      catch (NoSuchMethodException e)
      {
         return false;
      }
   }

   /**
    * Adds a node to this context and announces it in the graph.
    *
    * @throws IllegalStateException if the context is closed
    */
   void attach(ROS2Node node)
   {
      synchronized (nodes)
      {
         if (closed)
         {
            throw new IllegalStateException("Cannot create node '" + node.getFullyQualifiedName() + "': its ROS2Context is closed");
         }
         nodes.add(node);
      }
      try
      {
         participant.addNode(node);
      }
      catch (RuntimeException | Error e)
      {
         synchronized (nodes)
         {
            nodes.remove(node);
         }
         throw e;
      }
   }

   /**
    * Removes a closed node from this context and announces the change in the graph.
    */
   void detach(ROS2Node node)
   {
      synchronized (nodes)
      {
         nodes.remove(node);
      }
      participant.removeNode(node);
   }

   /**
    * Closes every node that still uses this context, then deletes the shared participant. Idempotent.
    * <p>
    * When called from a subscription callback, the context is marked closed immediately and the teardown runs on a
    * jros2 thread after this method returns.
    */
   @Override
   public void close()
   {
      boolean defer = CallbackContext.isInCallback();

      if (!closeRequested.compareAndSet(false, true))
      {
         if (!defer)
         {
            try
            {
               if (!closeDone.await(CLOSE_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
               {
                  jros2.getLogger().warning("Timed out waiting for a ROS2Context to close");
               }
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
         }
         return;
      }

      List<ROS2Node> nodesToClose;
      synchronized (nodes)
      {
         closed = true;
         nodesToClose = new ArrayList<>(nodes);
      }

      // From a callback the node teardowns are deferred; the participant teardown is queued behind them.
      for (ROS2Node node : nodesToClose)
      {
         try
         {
            node.close();
         }
         catch (Throwable t)
         {
            jros2.logError("Failed to close node '" + node.getFullyQualifiedName() + "'", t);
         }
      }

      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CLOSE_WAIT_TIMEOUT_MS);
      if (defer)
      {
         ROS2Node.runDeferredTeardown(() -> closeParticipant(nodesToClose, deadline));
      }
      else
      {
         closeParticipant(nodesToClose, deadline);
      }
   }

   /**
    * Deletes the participant once the teardown of every node has completed. On the deferred thread, a node teardown
    * that is not done yet is either queued behind this task or running on another thread, so the check is queued again
    * instead of blocking the deferred thread.
    */
   private void closeParticipant(List<ROS2Node> closingNodes, long deadline)
   {
      for (ROS2Node node : closingNodes)
      {
         if (node.isTeardownDone())
         {
            continue;
         }
         if (System.nanoTime() - deadline >= 0)
         {
            jros2.getLogger().warning("Closing " + this + " while node '" + node.getFullyQualifiedName() + "' is still closing");
            break;
         }
         if (ROS2Node.onDeferredThread())
         {
            try
            {
               Thread.sleep(1);
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
            ROS2Node.runDeferredTeardown(() -> closeParticipant(closingNodes, deadline));
            return;
         }
         try
         {
            node.awaitClosed(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())), TimeUnit.MILLISECONDS);
         }
         catch (InterruptedException e)
         {
            Thread.currentThread().interrupt();
            break;
         }
      }

      try
      {
         participant.close();
      }
      catch (Throwable t)
      {
         jros2.logError("Failed to close a ROS2Context", t);
      }
      finally
      {
         closeDone.countDown();
         // Kept until here so that a JVM exit waits for a teardown deferred from a callback
         if (Thread.currentThread() != shutdownHook)
         {
            try
            {
               Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
            catch (IllegalStateException ignored)
            {
               // JVM shutdown already in progress
            }
         }
      }
   }

   /**
    * Waits until the participant of this context was deleted (for tests).
    */
   boolean awaitClosed(long timeout, TimeUnit unit) throws InterruptedException
   {
      return closeDone.await(timeout, unit);
   }

   @Override
   public String toString()
   {
      return "ROS2Context[domain=" + domainId + ", nodes=" + getNodes().size() + (isClosed() ? ", closed" : "") + "]";
   }
}
