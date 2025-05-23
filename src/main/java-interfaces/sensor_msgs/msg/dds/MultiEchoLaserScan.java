/*
 * This class was automatically generated by jros2.
 * Do not modify this file directly.

##################################################################################
This file was generated from the following content:
(sensor_msgs/MultiEchoLaserScan.msg)
##################################################################################
   # Single scan from a multi-echo planar laser range-finder
   #
   # If you have another ranging device with different behavior (e.g. a sonar
   # array), please find or create a different message, since applications
   # will make fairly laser-specific assumptions about this data

   std_msgs/Header header # timestamp in the header is the acquisition time of
                                # the first ray in the scan.
                                #
                                # in frame frame_id, angles are measured around
                                # the positive Z axis (counterclockwise, if Z is up)
                                # with zero angle being forward along the x axis

   float32 angle_min            # start angle of the scan [rad]
   float32 angle_max            # end angle of the scan [rad]
   float32 angle_increment      # angular distance between measurements [rad]

   float32 time_increment       # time between measurements [seconds] - if your scanner
                                # is moving, this will be used in interpolating position
                                # of 3d points
   float32 scan_time            # time between scans [seconds]

   float32 range_min            # minimum range value [m]
   float32 range_max            # maximum range value [m]

   LaserEcho[] ranges           # range data [m]
                                # (Note: NaNs, values < range_min or > range_max should be discarded)
                                # +Inf measurements are out of range
                                # -Inf measurements are too close to determine exact distance.
   LaserEcho[] intensities      # intensity data [device-specific units].  If your
                                # device does not provide intensities, please leave
                                # the array empty.

##################################################################################

 */
package sensor_msgs.msg.dds;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.*;
import us.ihmc.jros2.ROS2Message;

/**
   Single scan from a multi-echo planar laser range-finder

   If you have another ranging device with different behavior (e.g. a sonar
   array), please find or create a different message, since applications
   will make fairly laser-specific assumptions about this data
*/
public class MultiEchoLaserScan implements ROS2Message<MultiEchoLaserScan>
{
   public static final java.lang.String name = "sensor_msgs::msg::dds_::MultiEchoLaserScan_";

   private final std_msgs.msg.dds.Header header_; // timestamp in the header is the acquisition time of
   private float angle_min_; // start angle of the scan [rad]
   private float angle_max_; // end angle of the scan [rad]
   private float angle_increment_; // angular distance between measurements [rad]
   private float time_increment_; // time between measurements [seconds] - if your scanner
   /**
      is moving, this will be used in interpolating position
      of 3d points
   */
   private float scan_time_; // time between scans [seconds]
   private float range_min_; // minimum range value [m]
   private float range_max_; // maximum range value [m]
   private final IDLObjectSequence<sensor_msgs.msg.dds.LaserEcho> ranges_; // range data [m]
   /**
      (Note: NaNs, values < range_min or > range_max should be discarded)
      +Inf measurements are out of range
      -Inf measurements are too close to determine exact distance.
   */
   private final IDLObjectSequence<sensor_msgs.msg.dds.LaserEcho> intensities_; // intensity data [device-specific units].  If your

   public MultiEchoLaserScan()
   {
      header_ = new std_msgs.msg.dds.Header();
      ranges_ = new IDLObjectSequence<sensor_msgs.msg.dds.LaserEcho>(sensor_msgs.msg.dds.LaserEcho.class);
      intensities_ = new IDLObjectSequence<sensor_msgs.msg.dds.LaserEcho>(sensor_msgs.msg.dds.LaserEcho.class);

   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += header_.calculateSizeBytes(currentAlignment);
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // angle_min_
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // angle_max_
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // angle_increment_
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // time_increment_
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // scan_time_
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // range_min_
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // range_max_
      currentAlignment += ranges_.calculateSizeBytes(currentAlignment);
      currentAlignment += intensities_.calculateSizeBytes(currentAlignment);

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      header_.serialize(buffer);
      buffer.writeFloat(angle_min_);
      buffer.writeFloat(angle_max_);
      buffer.writeFloat(angle_increment_);
      buffer.writeFloat(time_increment_);
      buffer.writeFloat(scan_time_);
      buffer.writeFloat(range_min_);
      buffer.writeFloat(range_max_);
      ranges_.serialize(buffer);
      intensities_.serialize(buffer);

   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      header_.deserialize(buffer);
      angle_min_ = buffer.readFloat();
      angle_max_ = buffer.readFloat();
      angle_increment_ = buffer.readFloat();
      time_increment_ = buffer.readFloat();
      scan_time_ = buffer.readFloat();
      range_min_ = buffer.readFloat();
      range_max_ = buffer.readFloat();
      ranges_.deserialize(buffer);
      intensities_.deserialize(buffer);

   }

   @Override
   public void set(MultiEchoLaserScan from)
   {
      header_.set(from.header_);
      angle_min_ = from.angle_min_;
      angle_max_ = from.angle_max_;
      angle_increment_ = from.angle_increment_;
      time_increment_ = from.time_increment_;
      scan_time_ = from.scan_time_;
      range_min_ = from.range_min_;
      range_max_ = from.range_max_;
      ranges_.set(from.ranges_);
      intensities_.set(from.intensities_);

   }

   public std_msgs.msg.dds.Header getHeader()
   {
      return header_;
   }

   public float getAngleMin()
   {
      return angle_min_;
   }

   public void setAngleMin(float angle_min_)
   {
      this.angle_min_ = angle_min_;
   }

   public float getAngleMax()
   {
      return angle_max_;
   }

   public void setAngleMax(float angle_max_)
   {
      this.angle_max_ = angle_max_;
   }

   public float getAngleIncrement()
   {
      return angle_increment_;
   }

   public void setAngleIncrement(float angle_increment_)
   {
      this.angle_increment_ = angle_increment_;
   }

   public float getTimeIncrement()
   {
      return time_increment_;
   }

   public void setTimeIncrement(float time_increment_)
   {
      this.time_increment_ = time_increment_;
   }

   public float getScanTime()
   {
      return scan_time_;
   }

   public void setScanTime(float scan_time_)
   {
      this.scan_time_ = scan_time_;
   }

   public float getRangeMin()
   {
      return range_min_;
   }

   public void setRangeMin(float range_min_)
   {
      this.range_min_ = range_min_;
   }

   public float getRangeMax()
   {
      return range_max_;
   }

   public void setRangeMax(float range_max_)
   {
      this.range_max_ = range_max_;
   }

   public IDLObjectSequence<sensor_msgs.msg.dds.LaserEcho> getRanges()
   {
      return ranges_;
   }

   public IDLObjectSequence<sensor_msgs.msg.dds.LaserEcho> getIntensities()
   {
      return intensities_;
   }


}