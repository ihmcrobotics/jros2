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

import us.ihmc.fastddsjava.cdr.JdkPreparation;

/**
 * Interns, once, when the first participant is created (setup), the string constants of the JDK classes whose methods run
 * on jros2's publish and receive paths, so that HotSpot does not create them later on a hot thread.
 * <p>
 * When HotSpot first queues a method of a class for C2 compilation, the requesting thread (one that ran the method often
 * enough: a publishing, callback, executor or async publish thread) creates every string constant of that class that is
 * not resolved yet (see {@link HotPathText} for jros2's own classes). A constant is only created if the string table does
 * not have an equal string: with G1 and the JDK's class data sharing archive (the defaults) the JDK's constants are mostly
 * there already, but not all (on JDK 17 {@code AtomicReferenceArray}'s, used by {@link AllocationFreeLock} under contention
 * and by {@link ROS2CallbackExecutor}), and with ZGC, ParallelGC, a heap of 32 GB or more or {@code -Xshare:off} none are:
 * then, for example, the first C2 compilation of {@code Thread.getName()} (every inline callback) creates 9 strings of
 * {@code java.lang.Thread} on a callback thread, and {@code TimeUnit}, {@code LockSupport}, {@code ReentrantLock} and the
 * reflection classes of the statistics follow at times that depend on the JIT. Interning them here makes those
 * resolutions find existing strings, which allocates nothing.
 * <p>
 * The JDK classes under the CDR serialization are prepared the same way by the CDR code ({@link JdkPreparation}, which
 * also reads and interns the constants listed here). Best effort: a class that does not exist or whose class file cannot
 * be read (Android has no class files) is skipped.
 */
final class JdkStringConstants
{
   /** The JDK classes whose methods run on jros2's own hot paths (besides the CDR code). */
   static final String[] CLASSES = {"java.lang.Thread",
                                    "java.lang.ThreadLocal",
                                    "java.lang.ThreadLocal$ThreadLocalMap",
                                    "java.lang.ThreadLocal$SuppliedThreadLocal",
                                    "java.lang.Enum",
                                    "java.lang.System",
                                    "java.lang.Math",
                                    "java.lang.Double",
                                    "java.util.concurrent.TimeUnit",
                                    "java.util.concurrent.locks.LockSupport",
                                    "java.util.concurrent.locks.ReentrantLock",
                                    "java.util.concurrent.locks.ReentrantLock$Sync",
                                    "java.util.concurrent.locks.ReentrantLock$NonfairSync",
                                    "java.util.concurrent.locks.AbstractQueuedSynchronizer",
                                    "java.util.concurrent.locks.AbstractOwnableSynchronizer",
                                    "java.util.concurrent.atomic.AtomicBoolean",
                                    "java.util.concurrent.atomic.AtomicInteger",
                                    "java.util.concurrent.atomic.AtomicLong",
                                    "java.util.concurrent.atomic.AtomicIntegerArray",
                                    "java.util.concurrent.atomic.AtomicLongArray",
                                    "java.util.concurrent.atomic.AtomicReferenceArray",
                                    "java.lang.invoke.VarHandle",
                                    "java.lang.invoke.VarHandleGuards",
                                    "java.lang.invoke.VarHandleReferences$Array",
                                    "java.lang.invoke.Invokers",
                                    "java.lang.reflect.Method",
                                    "java.lang.reflect.Executable",
                                    "jdk.internal.reflect.MethodAccessorImpl",
                                    "jdk.internal.reflect.DelegatingMethodAccessorImpl",
                                    "jdk.internal.reflect.NativeMethodAccessorImpl"};

   /** Strong references to the interned constants: the JVM's string table alone would let them be collected. */
   private static String[] interned;

   private JdkStringConstants()
   {
   }

   /** Interns the string constants of {@link #CLASSES}; does nothing after the first call. */
   static synchronized void prepare()
   {
      if (interned == null)
      {
         interned = JdkPreparation.internStringConstants(CLASSES);
      }
   }

   /** @return the number of string constants interned and kept (0 before {@link #prepare()}) */
   static synchronized int getInternedConstantCount()
   {
      return interned == null ? 0 : interned.length;
   }
}
