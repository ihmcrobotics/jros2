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

import us.ihmc.fastddsjava.cdr.idl.IDLByteSequence;

/**
 * Realistic messages for the allocation gates: strings, nested messages, bounded and unbounded sequences and arrays,
 * filled once and then updated in place every control tick without changing their serialized size (as a robot does).
 * {@link MessageKind#sequence} reads back the tick number, so a test can check that the received message is the one
 * that was published.
 */
final class HotPathMessages
{
   private HotPathMessages()
   {
   }

   abstract static class MessageKind<T extends ROS2Message<T>>
   {
      private final String name;
      private final Class<T> type;

      MessageKind(String name, Class<T> type)
      {
         this.name = name;
         this.type = type;
      }

      Class<T> type()
      {
         return type;
      }

      /** @return a message filled with the stable-size contents of this kind. Allocates (setup). */
      abstract T create();

      /** Writes tick {@code i} into {@code message} in place. Allocation-free. */
      abstract void update(T message, int i);

      /** @return the tick last written by {@link #update}. Allocation-free. */
      abstract int sequence(T message);

      @Override
      public String toString()
      {
         return name;
      }
   }

   private static void stamp(std_msgs.Header header, int i)
   {
      header.getStamp().setSec(i);
      header.getStamp().setNanosec(i % 1_000_000_000);
   }

   private static void fill(IDLByteSequence sequence, int size)
   {
      sequence.clear();
      sequence.ensureMinCapacity(size);
      for (int i = 0; i < size; ++i)
      {
         sequence.add((byte) i);
      }
   }

   /** Writes {@code value} as 8 decimal digits at the start of {@code builder} (which keeps its length). */
   private static void writeDigits(StringBuilder builder, int value)
   {
      for (int digit = 7; digit >= 0; --digit)
      {
         builder.setCharAt(digit, (char) ('0' + value % 10));
         value /= 10;
      }
   }

   private static int readDigits(CharSequence chars)
   {
      int value = 0;
      for (int digit = 0; digit < 8; ++digit)
      {
         value = value * 10 + (chars.charAt(digit) - '0');
      }
      return value;
   }

   /** std_msgs/String with 64 characters. */
   static final MessageKind<std_msgs.String_> STRING = new MessageKind<>("std_msgs/String", std_msgs.String_.class)
   {
      @Override
      std_msgs.String_ create()
      {
         std_msgs.String_ message = new std_msgs.String_();
         message.getData().append("00000000 the quick brown fox jumps over the lazy robot, 64 chars");
         return message;
      }

      @Override
      void update(std_msgs.String_ message, int i)
      {
         writeDigits(message.getData(), i);
      }

      @Override
      int sequence(std_msgs.String_ message)
      {
         return readDigits(message.getData());
      }
   };

   /** geometry_msgs/PoseStamped (header with frame id, nested pose). */
   static final MessageKind<geometry_msgs.PoseStamped> POSE_STAMPED = new MessageKind<>("geometry_msgs/PoseStamped", geometry_msgs.PoseStamped.class)
   {
      @Override
      geometry_msgs.PoseStamped create()
      {
         geometry_msgs.PoseStamped message = new geometry_msgs.PoseStamped();
         message.getHeader().setFrameId("map");
         message.getPose().getOrientation().setW(1.0);
         return message;
      }

      @Override
      void update(geometry_msgs.PoseStamped message, int i)
      {
         stamp(message.getHeader(), i);
         message.getPose().getPosition().setX(i * 0.001);
         message.getPose().getPosition().setY(-i * 0.001);
      }

      @Override
      int sequence(geometry_msgs.PoseStamped message)
      {
         return message.getHeader().getStamp().getSec();
      }
   };

   static final int JOINTS = 30;

   /** sensor_msgs/JointState of a 30-joint robot: 30 names and 3 x 30 doubles. */
   static final MessageKind<sensor_msgs.JointState> JOINT_STATE = new MessageKind<>("sensor_msgs/JointState", sensor_msgs.JointState.class)
   {
      @Override
      sensor_msgs.JointState create()
      {
         sensor_msgs.JointState message = new sensor_msgs.JointState();
         message.getHeader().setFrameId("base_link");
         for (int j = 0; j < JOINTS; ++j)
         {
            message.getName().add(String.format("joint_%02d_actuator", j));
            message.getPosition().add(j);
            message.getVelocity().add(j);
            message.getEffort().add(j);
         }
         return message;
      }

      @Override
      void update(sensor_msgs.JointState message, int i)
      {
         stamp(message.getHeader(), i);
         for (int j = 0; j < JOINTS; ++j)
         {
            message.getPosition().putAt(j, i + j * 0.01);
            message.getVelocity().putAt(j, -i);
            message.getEffort().putAt(j, j);
         }
      }

      @Override
      int sequence(sensor_msgs.JointState message)
      {
         return message.getHeader().getStamp().getSec();
      }
   };

   static final int MARKERS = 10;
   static final int MARKER_POINTS = 50;

   /** visualization_msgs/MarkerArray: 10 line-strip markers with 50 points and 50 colors each, strings and nested messages. */
   static final MessageKind<visualization_msgs.MarkerArray> MARKER_ARRAY = new MessageKind<>("visualization_msgs/MarkerArray",
                                                                                             visualization_msgs.MarkerArray.class)
   {
      @Override
      visualization_msgs.MarkerArray create()
      {
         visualization_msgs.MarkerArray message = new visualization_msgs.MarkerArray();
         for (int m = 0; m < MARKERS; ++m)
         {
            visualization_msgs.Marker marker = message.getMarkers().add();
            marker.getHeader().setFrameId("odom");
            marker.setNs("footsteps");
            marker.setId(m);
            marker.setType(visualization_msgs.Marker.LINE_STRIP);
            marker.getPose().getOrientation().setW(1.0);
            marker.getScale().setX(0.02);
            marker.getColor().setA(1.0f);
            marker.setText("step");
            for (int p = 0; p < MARKER_POINTS; ++p)
            {
               marker.getPoints().add().setX(p);
               marker.getColors().add().setR(p / 50.0f);
            }
         }
         return message;
      }

      @Override
      void update(visualization_msgs.MarkerArray message, int i)
      {
         for (int m = 0; m < MARKERS; ++m)
         {
            visualization_msgs.Marker marker = message.getMarkers().get(m);
            stamp(marker.getHeader(), i);
            for (int p = 0; p < MARKER_POINTS; ++p)
            {
               marker.getPoints().get(p).setY(i * 0.001 + p);
            }
         }
      }

      @Override
      int sequence(visualization_msgs.MarkerArray message)
      {
         return message.getMarkers().get(MARKERS - 1).getHeader().getStamp().getSec();
      }
   };

   static final int CLOUD_POINTS = 20_000;

   /** sensor_msgs/PointCloud2 with 20,000 XYZI points (320 kB): fragmented by Fast-DDS over UDP. */
   static final MessageKind<sensor_msgs.PointCloud2> POINT_CLOUD = new MessageKind<>("sensor_msgs/PointCloud2", sensor_msgs.PointCloud2.class)
   {
      @Override
      sensor_msgs.PointCloud2 create()
      {
         sensor_msgs.PointCloud2 message = new sensor_msgs.PointCloud2();
         message.getHeader().setFrameId("lidar");
         String[] names = {"x", "y", "z", "intensity"};
         for (int f = 0; f < names.length; ++f)
         {
            sensor_msgs.PointField field = message.getFields().add();
            field.setName(names[f]);
            field.setOffset(4 * f);
            field.setDatatype(sensor_msgs.PointField.FLOAT32);
            field.setCount(1);
         }
         message.setHeight(1);
         message.setWidth(CLOUD_POINTS);
         message.setPointStep(16);
         message.setRowStep(16 * CLOUD_POINTS);
         message.setIsDense(true);
         fill(message.getData(), 16 * CLOUD_POINTS);
         return message;
      }

      @Override
      void update(sensor_msgs.PointCloud2 message, int i)
      {
         stamp(message.getHeader(), i);
         message.getData().putAt(0, (byte) i);
      }

      @Override
      int sequence(sensor_msgs.PointCloud2 message)
      {
         return message.getHeader().getStamp().getSec();
      }
   };

   /** sensor_msgs/Image, 1280 x 720 rgb8 (2.76 MB): fragmented by Fast-DDS. */
   static final MessageKind<sensor_msgs.Image> IMAGE = image(1280, 720);
   /**
    * sensor_msgs/Image, 400 x 400 rgb8 (480 kB): fragmented by Fast-DDS, but small enough for its default 512 kB
    * shared-memory segment, which BEST_EFFORT needs (see LargeMessageAllocationTest).
    */
   static final MessageKind<sensor_msgs.Image> SEGMENT_SIZED_IMAGE = image(400, 400);

   private static MessageKind<sensor_msgs.Image> image(int width, int height)
   {
      return new MessageKind<>("sensor_msgs/Image " + width + "x" + height + " rgb8", sensor_msgs.Image.class)
      {
         @Override
         sensor_msgs.Image create()
         {
            sensor_msgs.Image message = new sensor_msgs.Image();
            message.getHeader().setFrameId("camera_color_optical_frame");
            message.setHeight(height);
            message.setWidth(width);
            message.setEncoding("rgb8");
            message.setStep(3 * width);
            fill(message.getData(), 3 * width * height);
            return message;
         }

         @Override
         void update(sensor_msgs.Image message, int i)
         {
            stamp(message.getHeader(), i);
            message.getData().putAt(0, (byte) i);
            message.getData().putAt(message.getData().size() - 1, (byte) -i);
         }

         @Override
         int sequence(sensor_msgs.Image message)
         {
            return message.getHeader().getStamp().getSec();
         }
      };
   }

   /**
    * jros2_example_interfaces/TestMsg: a custom type with a header, bounded and unbounded strings and sequences, fixed
    * arrays of strings and integers, sequences of strings and an array of three nested 32 x 32 images.
    */
   static final MessageKind<jros2_example_interfaces.TestMsg> CUSTOM = new MessageKind<>("jros2_example_interfaces/TestMsg",
                                                                                          jros2_example_interfaces.TestMsg.class)
   {
      @Override
      jros2_example_interfaces.TestMsg create()
      {
         jros2_example_interfaces.TestMsg message = new jros2_example_interfaces.TestMsg();
         message.getHeader().setFrameId("custom");
         message.setMyString("00000000 custom string");
         for (int k = 0; k < 100; ++k)
         {
            message.getUnboundedIntegerArray().add(k);
         }
         for (int k = 0; k < 5; ++k)
         {
            message.getUpToFiveIntegersArray().add(k);
            message.getUpToFiveUnboundedStrings().add("unbounded " + k);
            message.getUpToFiveStringsUpToTenCharactersEach().add("ten " + k);
         }
         message.setStringOfUnboundedSize("a longer string of unbounded size");
         message.setUpToTenCharactersString("ten chars");
         for (int k = 0; k < 20; ++k)
         {
            message.getUnboundedArrayOfStringsUpToTenCharactersEach().add("item " + k);
         }
         for (int k = 0; k < 3; ++k)
         {
            message.getThreeStrings()[k].append("string ").append(k);
            message.getThreeStringsEachMaxLengthOfFiveChars()[k].append("five").append(k);
            sensor_msgs.Image image = message.getThreeImages()[k];
            image.setHeight(32);
            image.setWidth(32);
            image.setEncoding("mono8");
            image.setStep(32);
            fill(image.getData(), 32 * 32);
         }
         return message;
      }

      @Override
      void update(jros2_example_interfaces.TestMsg message, int i)
      {
         stamp(message.getHeader(), i);
         message.setMyInt(i);
         writeDigits(message.getMyString(), i);
         message.getUnboundedIntegerArray().putAt(0, i);
         message.getFiveIntegersArray()[4] = i;
         message.getThreeImages()[2].getData().putAt(0, (byte) i);
      }

      @Override
      int sequence(jros2_example_interfaces.TestMsg message)
      {
         return message.getMyInt();
      }
   };
}
