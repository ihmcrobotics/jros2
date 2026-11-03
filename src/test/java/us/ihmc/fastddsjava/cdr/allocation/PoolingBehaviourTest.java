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
package us.ihmc.fastddsjava.cdr.allocation;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.IDLObjectSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLStringSequence;
import us.ihmc.fastddsjava.cdr.idl.IDLWStringSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour of the allocation-free paths (normal test suite; the allocation gates themselves are tagged "allocation"):
 * pooled string builders are reused and cleared, the collection overloads read lists by index and other collections by
 * iteration with the same result, and the CharSequence setters of generated messages.
 */
public class PoolingBehaviourTest
{
   @Test
   public void testStringSequenceReusesClearedBuilders()
   {
      for (IDLStringSequence strings : new IDLStringSequence[] {new IDLStringSequence(), new IDLWStringSequence(0, 10, 4)})
      {
         String type = strings.getClass().getSimpleName();
         strings.add("first_€");
         strings.add().append("second");
         StringBuilder first = strings.get(0);
         StringBuilder second = strings.get(1);

         strings.clear();
         strings.add("x");
         assertSame(first, strings.get(0), type + ": add(String) reuses the pooled builder");
         assertEquals("x", strings.getAsString(0), type);
         StringBuilder added = strings.add();
         assertSame(second, added, type + ": add() reuses the pooled builder");
         assertEquals(0, added.length(), type + ": add() returns an empty builder");
         added.append("y");
         assertArrayEquals(new String[] {"x", "y"}, strings.toStringArray(), type);

         strings.clear();
         StringBuilder sized = strings.add(100);
         assertSame(first, sized, type);
         assertEquals(0, sized.length(), type + ": add(int) returns an empty builder");
         assertTrue(sized.capacity() >= 100, type);

         int size = strings.size();
         assertThrows(NullPointerException.class, () -> strings.add((String) null), type);
         assertEquals(size, strings.size(), type + ": nothing is added for null");
      }
   }

   @Test
   public void testStringSequenceAddAllCollections()
   {
      List<String> expected = List.of("a", "bé", "c😀");
      for (Iterable<String> source : List.<Iterable<String>>of(new ArrayList<>(expected), new LinkedList<>(expected), new LinkedHashSet<>(expected), new ArrayDeque<>(expected)))
      {
         IDLStringSequence strings = new IDLStringSequence();
         strings.add("stale_content_that_is_longer");
         strings.add("stale");
         strings.clear();
         strings.addAll((java.util.Collection<String>) source);
         assertArrayEquals(expected.toArray(new String[0]), strings.toStringArray(), source.getClass().getSimpleName());
      }
   }

   @Test
   public void testObjectSequenceAddAllCollections()
   {
      geometry_msgs.Point a = new geometry_msgs.Point();
      geometry_msgs.Point b = new geometry_msgs.Point();
      for (java.util.Collection<geometry_msgs.Point> source : List.<java.util.Collection<geometry_msgs.Point>>of(new ArrayList<>(List.of(a, b)),
                                                                                                                new LinkedList<>(List.of(a, b)),
                                                                                                                new ArrayDeque<>(List.of(a, b))))
      {
         IDLObjectSequence<geometry_msgs.Point> points = new IDLObjectSequence<>(geometry_msgs.Point.class);
         points.add();
         points.addAll(source);
         assertEquals(3, points.size());
         assertSame(a, points.get(1), source.getClass().getSimpleName());
         assertSame(b, points.get(2), source.getClass().getSimpleName());
      }
   }

   @Test
   public void testStringSetSharedElementGetsItsOwnBuilder()
   {
      IDLStringSequence source = new IDLStringSequence();
      IDLStringSequence copy = new IDLStringSequence();
      StringBuilder shared = new StringBuilder("shared");
      source.add(shared);
      copy.add(shared);
      copy.set(source);
      assertNotSame(shared, copy.get(0));
      assertEquals("shared", copy.getAsString(0));
      assertEquals("shared", shared.toString());
   }

   @Test
   public void testGeneratedCharSequenceSetters()
   {
      std_msgs.Header header = new std_msgs.Header();
      StringBuilder frame = new StringBuilder("frame_€");
      header.setFrameId(frame);
      assertEquals("frame_€", header.getFrameIdAsString());
      assertNotSame(frame, header.getFrameId());

      header.setFrameId(header.getFrameId());
      assertEquals("frame_€", header.getFrameIdAsString(), "self-assignment keeps the content");

      header.setFrameId("plain");
      assertEquals("plain", header.getFrameIdAsString());
      header.setFrameId((CharSequence) "char_sequence");
      assertEquals("char_sequence", header.getFrameIdAsString());

      std_msgs.String_ string = new std_msgs.String_();
      string.setData(new StringBuilder("😀"));
      assertEquals("😀", string.getDataAsString());
   }

   @Test
   public void testNewStringBuilder()
   {
      assertEquals(0, CDRBuffer.newStringBuilder(0).length());
      assertEquals(1, CDRBuffer.newStringBuilder(-5).capacity());
      assertEquals(32, CDRBuffer.newStringBuilder(32).capacity());
      StringBuilder content = CDRBuffer.newStringBuilder("défaut 😀");
      assertEquals("défaut 😀", content.toString());
      assertEquals("défaut 😀".length() + 16, content.capacity());

      // Generated messages start with empty strings, or their default values
      assertEquals("", new std_msgs.Header().getFrameIdAsString());
      assertEquals("John Doe", new jros2_example_interfaces.TestMsg().getFullNameAsString());
   }
}
