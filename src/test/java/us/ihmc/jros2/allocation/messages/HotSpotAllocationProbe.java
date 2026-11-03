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
package us.ihmc.jros2.allocation.messages;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import com.sun.management.HotSpotDiagnosticMXBean;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Runs allocation test classes (tag "allocation") with JUnit in this JVM, which {@link HotSpotAllocationTest} starts
 * without the java-allocation-instrumenter agent: there {@link CallingThreadAllocationGate#create()} returns a
 * {@link ThreadAllocatedBytesGate}, so the same gates check HotSpot's allocated-bytes counter of the thread.
 * <p>
 * Prints the failures and a last line {@code PROBE agent=... found=... succeeded=... failed=... skipped=... archivedHeap=...
 * vm=[...]}; exits with 0 only if every test passed.
 * </p>
 */
public final class HotSpotAllocationProbe
{
   private HotSpotAllocationProbe()
   {
   }

   public static void main(String[] testClassNames)
   {
      LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                                                                        .selectors(Arrays.stream(testClassNames).map(name -> selectClass(name)).toList())
                                                                        .filters(TagFilter.includeTags("allocation"))
                                                                        .build();
      Launcher launcher = LauncherFactory.create();
      SummaryGeneratingListener listener = new SummaryGeneratingListener();
      launcher.execute(request, listener);

      TestExecutionSummary summary = listener.getSummary();
      PrintWriter out = new PrintWriter(System.out, true);
      summary.printFailuresTo(out, 40);
      out.println("PROBE agent=" + SingleThreadAllocationGate.Text.isAgentLoaded() + " found=" + summary.getTestsFoundCount() + " succeeded="
                  + summary.getTestsSucceededCount() + " failed=" + summary.getTotalFailureCount() + " skipped=" + summary.getTestsSkippedCount()
                  + " archivedHeap=" + isArchivedHeapUsable() + " vm=[" + System.getProperty("java.vm.info") + "]");
      out.flush();
      System.exit(summary.getTotalFailureCount() == 0 && summary.getTestsSucceededCount() > 0 ? 0 : 1);
   }

   /**
    * Whether this JVM can use the heap objects of the class data sharing archive, among them the string literals of the
    * archived JDK classes, already resolved. JDK 17 maps them only with class data sharing, G1, compressed oops and
    * compressed class pointers (-Xlog:cds otherwise prints "CDS heap data is being ignored. UseG1GC, UseCompressedOops and
    * UseCompressedClassPointers are required."); without them HotSpot resolves the literals of a JDK class on the thread that
    * first requests a C2 compilation of one of its methods.
    */
   static boolean isArchivedHeapUsable()
   {
      HotSpotDiagnosticMXBean vm = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
      return System.getProperty("java.vm.info", "").contains("sharing") && isEnabled(vm, "UseG1GC") && isEnabled(vm, "UseCompressedOops")
             && isEnabled(vm, "UseCompressedClassPointers");
   }

   private static boolean isEnabled(HotSpotDiagnosticMXBean vm, String option)
   {
      return Boolean.parseBoolean(vm.getVMOption(option).getValue());
   }
}
