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
package us.ihmc.fastddsjava.natives;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.ihmc.fastddsjava.fastddsjavaTestTools;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.natives.NativeDdsFixture.Topic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static us.ihmc.fastddsjava.fastddsjavaTestTools.waitFor;
import static us.ihmc.fastddsjava.natives.NativeDdsFixture.*;
import static us.ihmc.fastddsjava.natives.fastddsjava.*;

/**
 * Native listener lifecycle: callbacks stop when a listener is removed, Java callbacks (JNI global references) are
 * released when listeners are deleted or callbacks replaced, and (ABI v2) deleting a listener in any order is memory safe.
 */
public class NativeListenerLifecycleTest
{
   @BeforeAll
   public static void loadNatives()
   {
      assertTrue(fastddsjavaNativeLibrary.load(), "native libraries must load");
   }

   private static void assumeAbiV2()
   {
      assumeTrue(abiVersion() >= ABI_V2, "requires ABI v2 natives (run with JROS2_NATIVE_PATH)");
   }

   /** A Java callback that counts invocations and owns a large object, so leaks are visible to the GC test. */
   private static final class CountingCallback implements fastddsjavaCallback
   {
      final AtomicInteger count;
      final byte[] ballast = new byte[256 * 1024];

      CountingCallback(AtomicInteger count)
      {
         this.count = count;
      }

      @Override
      public void call()
      {
         count.incrementAndGet();
      }
   }

   private static boolean collected(WeakReference<?> reference) throws InterruptedException
   {
      for (int i = 0; i < 100 && reference.get() != null; i++)
      {
         System.gc();
         byte[][] garbage = new byte[64][];
         for (int j = 0; j < garbage.length; j++)
            garbage[j] = new byte[64 * 1024];
         Thread.sleep(10);
      }
      return reference.get() == null;
   }

   private static void write(long writer, Topic topic, int seed)
   {
      assertEquals(RETCODE_OK, tryWrite(writer, topic, seed));
   }

   /** For background writer threads: returns the retcode instead of failing (an assertion would end the thread silently). */
   private static int tryWrite(long writer, Topic topic, int seed)
   {
      byte[] content = payload(32, seed);
      int ret;
      if (abiVersion() >= ABI_V2)
      {
         ret = writeBuffer(writer, direct(content, content.length), content.length);
      }
      else
      {
         // ABI v1 topic data needs its (live) type for creation and deletion.
         long data = createData(topic.type);
         topicDataResize(data, content.length);
         topicDataWrite(data, content, 0, content.length);
         ret = dataWriterWrite(writer, data);
         deleteData(topic.type, data);
      }
      return ret;
   }

   /** Writes continuously until {@code running} is cleared; counts failed writes. */
   private static Thread startWriting(long writer, Topic topic, AtomicBoolean running, AtomicInteger failures, String name)
   {
      Thread thread = new Thread(() ->
      {
         int seed = 0;
         while (running.get())
         {
            int ret = tryWrite(writer, topic, seed++);
            if (ret != RETCODE_OK && ret != RETCODE_TIMEOUT)
               failures.incrementAndGet();
            Thread.yield();
         }
      }, name);
      thread.start();
      return thread;
   }

   /** Waits until the counter stops changing for 200 ms. */
   private static int settle(AtomicInteger counter) throws InterruptedException
   {
      int previous;
      do
      {
         previous = counter.get();
         Thread.sleep(200);
      }
      while (counter.get() != previous);
      return previous;
   }

   @Test
   @Timeout(60)
   public void noCallbacksAfterListenerIsRemovedAndResumeWhenReattached() throws Exception
   {
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_listener_detach");
         AtomicInteger dataCallbacks = new AtomicInteger();
         long listener = createDataReaderListener();
         dataReaderListenerSetOnDataAvailable(listener, new CountingCallback(dataCallbacks));
         long reader = fixture.reader(topic, RELIABLE, 0);
         assertEquals(RETCODE_OK, dataReaderSetListener(reader, listener));
         long writer = fixture.writer(topic, RELIABLE);
         assertTrue(waitForMatch(writer, reader));

         write(writer, topic, 1);
         assertTrue(waitFor(() -> dataCallbacks.get() >= 1, 10000));
         settle(dataCallbacks);

         assertEquals(RETCODE_OK, dataReaderSetListener(reader, 0));
         int detachedCount = dataCallbacks.get();
         for (int i = 0; i < 20; i++)
            write(writer, topic, i);
         Thread.sleep(300);
         assertEquals(detachedCount, dataCallbacks.get(), "no callback after the listener was removed");

         assertEquals(RETCODE_OK, dataReaderSetListener(reader, listener));
         write(writer, topic, 99);
         assertTrue(waitFor(() -> dataCallbacks.get() > detachedCount, 10000), "callbacks resume after re-attaching");

         assertEquals(RETCODE_OK, dataReaderSetListener(reader, 0));
         assertEquals(RETCODE_OK, fixture.deleteReader(reader));
         deleteDataReaderListener(listener);
      }
   }

   @Test
   @Timeout(60)
   public void deletingListenersReleasesJavaCallbacks() throws Exception
   {
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_listener_release");
         List<WeakReference<CountingCallback>> references = new ArrayList<>();
         AtomicInteger dataCallbacks = new AtomicInteger();
         AtomicInteger matchedCallbacks = new AtomicInteger();
         AtomicInteger publicationCallbacks = new AtomicInteger();

         long readerListener = createDataReaderListener();
         long writerListener = createDataWriterListener();
         installCallbacks(readerListener, writerListener, dataCallbacks, matchedCallbacks, publicationCallbacks, references);

         long reader = fixture.reader(topic, RELIABLE, readerListener);
         long writer = fixture.writer(topic, RELIABLE);
         assertEquals(RETCODE_OK, dataWriterSetListener(writer, writerListener));
         assertTrue(waitForMatch(writer, reader));
         write(writer, topic, 5);
         assertTrue(waitFor(() -> dataCallbacks.get() >= 1 && matchedCallbacks.get() >= 1, 10000));

         // Recommended teardown: remove, delete the entity (fence), delete the listener.
         assertEquals(RETCODE_OK, dataReaderSetListener(reader, 0));
         assertEquals(RETCODE_OK, fixture.deleteReader(reader));
         deleteDataReaderListener(readerListener);
         assertEquals(RETCODE_OK, dataWriterSetListener(writer, 0));
         assertEquals(RETCODE_OK, fixture.deleteWriter(writer));
         deleteDataWriterListener(writerListener);

         for (WeakReference<CountingCallback> reference : references)
            assertTrue(collected(reference), "the native listener must not pin its Java callback");
      }
   }

   private static void installCallbacks(long readerListener, long writerListener, AtomicInteger data, AtomicInteger matched, AtomicInteger publication,
                                        List<WeakReference<CountingCallback>> references)
   {
      CountingCallback onData = new CountingCallback(data);
      CountingCallback onMatched = new CountingCallback(matched);
      CountingCallback onPublication = new CountingCallback(publication);
      dataReaderListenerSetOnDataAvailable(readerListener, onData);
      dataReaderListenerSetOnSubscriptionMatched(readerListener, onMatched);
      dataWriterListenerSetOnPublicationMatched(writerListener, onPublication);
      references.add(new WeakReference<>(onData));
      references.add(new WeakReference<>(onMatched));
      references.add(new WeakReference<>(onPublication));
   }

   @Test
   @Timeout(60)
   public void replacedCallbackIsReleased() throws Exception
   {
      long listener = createDataReaderListener();
      AtomicInteger count = new AtomicInteger();
      WeakReference<CountingCallback> first = setNewCallback(listener, count);
      WeakReference<CountingCallback> second = setNewCallback(listener, count);
      assertTrue(collected(first), "replacing a callback releases the previous one");
      assertNotNull(second.get(), "the current callback stays referenced by the listener");
      dataReaderListenerSetOnDataAvailable(listener, null);
      assertTrue(collected(second), "clearing a callback releases it");
      deleteDataReaderListener(listener);
   }

   private static WeakReference<CountingCallback> setNewCallback(long listener, AtomicInteger count)
   {
      CountingCallback callback = new CountingCallback(count);
      dataReaderListenerSetOnDataAvailable(listener, callback);
      return new WeakReference<>(callback);
   }

   @Test
   @Timeout(120)
   public void listenerChurnLeavesNothingBehind() throws Exception
   {
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_listener_churn");
         long writer = fixture.writer(topic, RELIABLE);
         List<WeakReference<CountingCallback>> references = new ArrayList<>();
         AtomicBoolean writing = new AtomicBoolean(true);
         AtomicInteger writeFailures = new AtomicInteger();
         Thread publisher = startWriting(writer, topic, writing, writeFailures, "n1-churn-writer");
         try
         {
            for (int i = 0; i < 40; i++)
            {
               AtomicInteger callbacks = new AtomicInteger();
               long listener = createDataReaderListener();
               references.add(setNewCallback(listener, callbacks));
               long reader = fixture.reader(topic, RELIABLE, listener);
               // Torn down while data keeps flowing to it.
               assertTrue(waitFor(() -> callbacks.get() > 0, 10000), "reader " + i + " received data");
               assertEquals(RETCODE_OK, dataReaderSetListener(reader, 0));
               assertEquals(RETCODE_OK, fixture.deleteReader(reader));
               deleteDataReaderListener(listener);
            }
         }
         finally
         {
            writing.set(false);
            publisher.join();
         }
         assertEquals(0, writeFailures.get());
         for (WeakReference<CountingCallback> reference : references)
            assertTrue(collected(reference), "every churned listener released its Java callback");
      }
   }

   @Test
   @Timeout(60)
   public void writerListenerReportsMatchesUntilRemoved() throws Exception
   {
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_writer_listener");
         AtomicInteger matched = new AtomicInteger();
         long listener = createDataWriterListener();
         dataWriterListenerSetOnPublicationMatched(listener, new CountingCallback(matched));
         long writer = fixture.writer(topic, RELIABLE);
         assertEquals(RETCODE_OK, dataWriterSetListener(writer, listener));

         long reader = fixture.reader(topic, RELIABLE, 0);
         assertTrue(waitFor(() -> matched.get() >= 1, 10000), "matched with the first reader");
         settle(matched);

         assertEquals(RETCODE_OK, dataWriterSetListener(writer, 0));
         int detached = matched.get();
         long secondReader = fixture.reader(topic, RELIABLE, 0);
         assertTrue(waitFor(() -> dataWriterGetPublicationMatchedCount(writer) >= 2, 10000));
         assertEquals(RETCODE_OK, fixture.deleteReader(secondReader));
         assertEquals(RETCODE_OK, fixture.deleteReader(reader));
         Thread.sleep(200);
         assertEquals(detached, matched.get(), "no publication matched callback after the listener was removed");

         assertEquals(RETCODE_OK, fixture.deleteWriter(writer));
         deleteDataWriterListener(listener);
      }
   }

   @Test
   @Timeout(60)
   public void deletingAnAttachedListenerIsSafe() throws Exception
   {
      // ABI v1 frees the listener while the reader still points to it (use after free), so only v2 is exercised.
      assumeAbiV2();
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_listener_delete_attached");
         AtomicInteger dataCallbacks = new AtomicInteger();
         long listener = createDataReaderListener();
         WeakReference<CountingCallback> callback = setNewCallback(listener, dataCallbacks);
         long reader = fixture.reader(topic, RELIABLE, listener);
         long writer = fixture.writer(topic, RELIABLE);
         assertTrue(waitForMatch(writer, reader));
         write(writer, topic, 1);
         assertTrue(waitFor(() -> dataCallbacks.get() >= 1, 10000));
         settle(dataCallbacks);

         // Deleted while the reader still uses it: disabled at once, Java callback released at once, memory kept
         // until the reader is deleted.
         deleteDataReaderListener(listener);
         int deletedCount = dataCallbacks.get();
         for (int i = 0; i < 20; i++)
            write(writer, topic, i);
         Thread.sleep(300);
         assertEquals(deletedCount, dataCallbacks.get(), "a deleted listener never calls Java");
         assertTrue(collected(callback), "the Java callback is released before the reader is deleted");
         assertEquals(RETCODE_PRECONDITION_NOT_MET, dataReaderSetListener(reader, listener), "a deleted listener cannot be installed again");

         assertEquals(RETCODE_OK, fixture.deleteReader(reader)); // frees the deferred listener
      }
   }

   @Test
   @Timeout(60)
   public void deletingAListenerFromItsOwnCallbackIsDeferred() throws Exception
   {
      // A callback that tears its own listener down (e.g. close() called from inside onMessage) must neither deadlock nor
      // free the listener under the running callback; the memory goes when the reader is deleted later.
      assumeAbiV2();
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_listener_self_delete");
         AtomicInteger dataCallbacks = new AtomicInteger();
         long listener = createDataReaderListener();
         AtomicBoolean deleted = new AtomicBoolean();
         dataReaderListenerSetOnDataAvailable(listener, () ->
         {
            dataCallbacks.incrementAndGet();
            if (deleted.compareAndSet(false, true))
            {
               dataReaderListenerSetOnSubscriptionMatched(listener, null);
               deleteDataReaderListener(listener);
            }
         });
         long reader = fixture.reader(topic, RELIABLE, listener);
         long writer = fixture.writer(topic, RELIABLE);
         assertTrue(waitForMatch(writer, reader));

         write(writer, topic, 1);
         assertTrue(waitFor(deleted::get, 10000), "the callback deleted its listener");
         int count = settle(dataCallbacks);
         for (int i = 0; i < 20; i++)
            write(writer, topic, i);
         Thread.sleep(300);
         assertEquals(count, dataCallbacks.get(), "no callback after the listener deleted itself");
         assertEquals(RETCODE_PRECONDITION_NOT_MET, dataReaderSetListener(reader, listener), "a deleted listener cannot be installed again");

         assertEquals(RETCODE_OK, fixture.deleteReader(reader)); // frees the deferred listener
      }
   }

   @Test
   @Timeout(60)
   public void deletingAListenerAfterSetListenerNullKeepsItUntilTheEntityIsDeleted() throws Exception
   {
      // set_listener(nullptr) is not a fence in Fast-DDS, so v2 keeps the native object until the reader is deleted.
      assumeAbiV2();
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_listener_delete_detached");
         AtomicInteger dataCallbacks = new AtomicInteger();
         long writer = fixture.writer(topic, RELIABLE);
         long reader = fixture.reader(topic, RELIABLE, 0);
         assertTrue(waitForMatch(writer, reader));

         AtomicBoolean writing = new AtomicBoolean(true);
         AtomicInteger writeFailures = new AtomicInteger();
         Thread publisher = startWriting(writer, topic, writing, writeFailures, "n1-detach-writer");
         try
         {
            for (int i = 0; i < 50; i++)
            {
               long listener = createDataReaderListener();
               setNewCallback(listener, dataCallbacks);
               int before = dataCallbacks.get();
               assertEquals(RETCODE_OK, dataReaderSetListener(reader, listener));
               waitFor(() -> dataCallbacks.get() > before, 2000);
               assertEquals(RETCODE_OK, dataReaderSetListener(reader, 0));
               deleteDataReaderListener(listener); // reader still alive: deferred
            }
         }
         finally
         {
            writing.set(false);
            publisher.join();
         }
         assertEquals(RETCODE_OK, fixture.deleteReader(reader)); // frees all 50 deferred listeners
         assertTrue(dataCallbacks.get() > 0);
         assertEquals(0, writeFailures.get());
      }
   }

   /** Runs {@link NativeCallbackThreadsChildMain} in a new JVM with this JVM's class path and environment. */
   private static String runCallbackThreadsChild(String mode, int rounds, String expectedPrefix) throws Exception
   {
      List<String> command = new ArrayList<>();
      command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
      command.add("-Xmx256m");
      command.addAll(fastddsjavaTestTools.coverageAgentArguments());
      for (String property : new String[] {fastddsjavaNativeLibrary.NATIVE_PATH_PROPERTY, fastddsjavaNativeLibrary.EXTRACT_DIR_PROPERTY})
      {
         if (System.getProperty(property) != null)
            command.add("-D" + property + "=" + System.getProperty(property));
      }
      command.add("-cp");
      command.add(System.getProperty("java.class.path"));
      command.add(NativeCallbackThreadsChildMain.class.getName());
      command.add(mode);
      command.add(Integer.toString(rounds));
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      StringBuilder output = new StringBuilder();
      Thread pump = new Thread(() ->
      {
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
         {
            String line;
            while ((line = reader.readLine()) != null)
            {
               synchronized (output)
               {
                  output.append(line).append('\n');
               }
            }
         }
         catch (IOException ignored)
         {
            // Process ended.
         }
      });
      pump.start();
      boolean exited = process.waitFor(90, TimeUnit.SECONDS);
      if (!exited)
         process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
      pump.join(10000);
      String text;
      synchronized (output)
      {
         text = output.toString();
      }
      assertTrue(exited, "the child JVM (" + mode + ") did not terminate:\n" + text);
      assertEquals(0, process.exitValue(), "child JVM (" + mode + ") exit code:\n" + text);
      String found = null;
      for (String line : text.split("\\R"))
      {
         if (line.startsWith(expectedPrefix))
            found = line;
      }
      assertNotNull(found, "no '" + expectedPrefix + "' line from the child JVM (" + mode + "):\n" + text);
      return found;
   }

   @Test
   @Timeout(300)
   public void callbackThreadsAreDetachedWhenTheirParticipantIsDeleted() throws Exception
   {
      // The natives attach a Fast-DDS thread to the JVM the first time it runs a Java callback and keep it attached. A
      // thread that ends while attached leaks its java.lang.Thread and JVM thread structures (HotSpot never detaches it)
      // and makes ART log or abort (Android), so every deleted participant would leak its callback threads.
      assumeAbiV2(); // ABI v1 never detaches
      String[] threads = runCallbackThreadsChild("rounds", 4, "THREADS ").split(" ");
      int warm = Integer.parseInt(threads[1]);
      int after = Integer.parseInt(threads[2]);
      assertTrue(after <= warm, "callback threads of deleted participants are still attached: " + after + " after 5 rounds, " + warm + " after the first");
   }

   @Test
   @Timeout(300)
   public void processExitWithLiveParticipantsDoesNotHang() throws Exception
   {
      // With live participants, Fast-DDS stops their threads in static destructors after the JVM has exited. A callback
      // thread must then not call into the JVM to detach (HotSpot blocks such threads forever, so exit would hang).
      assumeAbiV2();
      assertTrue(Integer.parseInt(runCallbackThreadsChild("exit", 1, "LIVE ").split(" ")[1]) > 0, "callback threads were attached");
      assertTrue(Integer.parseInt(runCallbackThreadsChild("halt", 1, "LIVE ").split(" ")[1]) > 0, "callback threads were attached");
   }

   @Test
   @Timeout(60)
   public void replacingCallbacksWhileDeliveringIsSafe() throws Exception
   {
      // ABI v1 replaces the std::function while a Fast-DDS thread may be calling it (data race), so only v2.
      assumeAbiV2();
      try (NativeDdsFixture fixture = new NativeDdsFixture())
      {
         Topic topic = fixture.topic("n1_listener_swap");
         AtomicInteger first = new AtomicInteger();
         AtomicInteger second = new AtomicInteger();
         long listener = createDataReaderListener();
         long reader = fixture.reader(topic, RELIABLE, listener);
         long writer = fixture.writer(topic, RELIABLE);
         assertTrue(waitForMatch(writer, reader));

         AtomicBoolean writing = new AtomicBoolean(true);
         Thread publisher = new Thread(() ->
         {
            ByteBuffer buffer = direct(payload(64, 1), 64);
            while (writing.get())
               writeBuffer(writer, buffer, 64);
         }, "n1-swap-writer");
         publisher.start();
         try
         {
            for (int i = 0; i < 3000; i++)
            {
               AtomicInteger target = i % 2 == 0 ? first : second;
               dataReaderListenerSetOnDataAvailable(listener, target::incrementAndGet);
               dataReaderListenerSetOnSubscriptionMatched(listener, target::incrementAndGet);
               if (i % 500 == 0)
                  Thread.sleep(5);
            }
         }
         finally
         {
            writing.set(false);
            publisher.join();
         }
         assertTrue(first.get() + second.get() > 0, "callbacks were delivered while being replaced");

         assertEquals(RETCODE_OK, dataReaderSetListener(reader, 0));
         assertEquals(RETCODE_OK, fixture.deleteReader(reader));
         deleteDataReaderListener(listener);
      }
   }
}
