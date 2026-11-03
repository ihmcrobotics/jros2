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

/**
 * A zero-tolerance allocation gate for code that runs on the calling thread.
 *
 * @see SingleThreadAllocationGate every allocation done by bytecode, reported by the java-allocation-instrumenter agent
 * @see ThreadAllocatedBytesGate the bytes HotSpot allocates for the thread, in a JVM without the agent
 */
public interface CallingThreadAllocationGate extends AutoCloseable
{
   /**
    * @return the gate for this JVM: {@link SingleThreadAllocationGate} when the java-allocation-instrumenter agent is
    *       loaded (the allocationTest task), {@link ThreadAllocatedBytesGate} otherwise (the JVM that
    *       {@link HotSpotAllocationTest} starts without the agent)
    */
   static CallingThreadAllocationGate create()
   {
      return SingleThreadAllocationGate.Text.isAgentLoaded() ? new SingleThreadAllocationGate() : new ThreadAllocatedBytesGate();
   }

   @Override
   void close();

   /**
    * Runs {@code body} (created by the caller before this call) and fails if it allocated anything on this thread.
    */
   void assertNoAllocation(String what, Runnable body);

   /**
    * Runs {@code body} and returns the number of allocations (or allocated bytes) seen on this thread, for paths documented
    * as allocating.
    */
   long countAllocations(Runnable body);
}
