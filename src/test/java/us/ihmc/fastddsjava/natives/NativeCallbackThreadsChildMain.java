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

import us.ihmc.fastddsjava.fastddsjavaTools;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.natives.NativeDdsFixture.Topic;
import us.ihmc.fastddsjava.profiles.ProfilesXML;

import java.util.concurrent.atomic.AtomicInteger;

import static us.ihmc.fastddsjava.fastddsjavaTestTools.waitFor;
import static us.ihmc.fastddsjava.natives.NativeDdsFixture.*;
import static us.ihmc.fastddsjava.natives.fastddsjava.*;

/**
 * Child JVM of {@link NativeListenerLifecycleTest#callbackThreadsAreDetachedWhenTheirParticipantIsDeleted()}. Intraprocess
 * delivery is switched off for the whole process (which is why this runs in its own JVM), so data and matched callbacks
 * run on Fast-DDS threads, which the natives attach to the JVM. Prints {@code THREADS <after first round> <after all rounds>}:
 * the number of attached callback threads still known to the JVM after the participants of the rounds were deleted.
 * <p>
 * Modes: {@code rounds <n>} runs the rounds and returns normally; {@code exit <n>} runs the rounds, then creates a
 * participant pair with Java callbacks and calls {@link System#exit} without deleting it (process exit with live
 * participants must not hang); {@code halt <n>} does the same with {@link Runtime#halt}.
 */
public final class NativeCallbackThreadsChildMain
{
   /** Name the natives give to the Fast-DDS threads they attach to run Java callbacks. */
   static final String CALLBACK_THREAD_NAME = "fastdds-callback";

   private NativeCallbackThreadsChildMain()
   {
   }

   static int countCallbackThreads()
   {
      int count = 0;
      for (Thread thread : Thread.getAllStackTraces().keySet())
      {
         if (CALLBACK_THREAD_NAME.equals(thread.getName()))
            count++;
      }
      return count;
   }

   private static void check(boolean condition, String message)
   {
      if (!condition)
         throw new IllegalStateException(message);
   }

   /** Live participant pair whose callbacks keep running. */
   private static final class Exchange
   {
      final NativeDdsFixture readerSide = new NativeDdsFixture();
      final NativeDdsFixture writerSide = new NativeDdsFixture();
      final AtomicInteger data = new AtomicInteger();
      final AtomicInteger matched = new AtomicInteger();
      final AtomicInteger publication = new AtomicInteger();
      final long readerListener = createDataReaderListener();
      final long writerListener = createDataWriterListener();
      final long reader;
      final long writer;

      Exchange(int round) throws Exception
      {
         String topicName = uniqueName("n1_thread_detach");
         String typeName = uniqueName("n1_thread_detach_type");
         Topic readerTopic = readerSide.topic(topicName, typeName);
         Topic writerTopic = writerSide.topic(topicName, typeName);
         dataReaderListenerSetOnDataAvailable(readerListener, data::incrementAndGet);
         dataReaderListenerSetOnSubscriptionMatched(readerListener, matched::incrementAndGet);
         dataWriterListenerSetOnPublicationMatched(writerListener, publication::incrementAndGet);
         writer = writerSide.writer(writerTopic, RELIABLE);
         check(dataWriterSetListener(writer, writerListener) == RETCODE_OK, "dataWriterSetListener");
         reader = readerSide.reader(readerTopic, RELIABLE, readerListener);
         check(waitForMatch(writer, reader), "match in round " + round);
         long topicData = createData(writerTopic.type);
         byte[] content = payload(32, round);
         topicDataResize(topicData, content.length);
         topicDataWrite(topicData, content, 0, content.length);
         check(dataWriterWrite(writer, topicData) == RETCODE_OK, "write in round " + round);
         deleteData(writerTopic.type, topicData);
         check(waitFor(() -> data.get() > 0 && matched.get() > 0 && publication.get() > 0, 10000), "callbacks in round " + round);
      }

      void close()
      {
         check(dataReaderSetListener(reader, 0) == RETCODE_OK, "dataReaderSetListener");
         check(readerSide.deleteReader(reader) == RETCODE_OK, "deleteReader");
         deleteDataReaderListener(readerListener);
         check(dataWriterSetListener(writer, 0) == RETCODE_OK, "dataWriterSetListener");
         check(writerSide.deleteWriter(writer) == RETCODE_OK, "deleteWriter");
         deleteDataWriterListener(writerListener);
         readerSide.close();
         writerSide.close();
      }
   }

   public static void main(String[] args) throws Exception
   {
      String mode = args[0];
      int rounds = Integer.parseInt(args[1]);

      check(fastddsjavaNativeLibrary.load(), "natives must load");
      String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<dds xmlns=\"" + ProfilesXML.FAST_DDS_NAMESPACE_URI + "\">\n"
                   + "<library_settings><intraprocess_delivery>OFF</intraprocess_delivery></library_settings>\n</dds>\n";
      fastddsjavaTools.retcodeThrowOnError(loadXmlProfilesString(xml));

      new Exchange(0).close();
      waitFor(() -> false, 500);
      int warm = countCallbackThreads();
      for (int round = 1; round <= rounds; round++)
         new Exchange(round).close();
      int[] after = {countCallbackThreads()};
      waitFor(() -> (after[0] = countCallbackThreads()) <= warm, 5000);
      System.out.println("THREADS " + warm + " " + after[0]);

      if (!"rounds".equals(mode))
      {
         Exchange live = new Exchange(rounds + 1);
         System.out.println("LIVE " + countCallbackThreads());
         System.out.flush();
         if ("halt".equals(mode))
            Runtime.getRuntime().halt(0);
         System.exit(0);
      }
      System.out.flush();
   }
}
