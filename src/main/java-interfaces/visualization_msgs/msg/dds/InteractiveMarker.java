/*
 * This class was automatically generated by jros2.
 * Do not modify this file directly.

##################################################################################
This file was generated from the following content:
(visualization_msgs/InteractiveMarker.msg)
##################################################################################
   # Time/frame info.
   # If header.time is set to 0, the marker will be retransformed into
   # its frame on each timestep. You will receive the pose feedback
   # in the same frame.
   # Otherwise, you might receive feedback in a different frame.
   # For rviz, this will be the current 'fixed frame' set by the user.
   std_msgs/Header header

   # Initial pose. Also, defines the pivot point for rotations.
   geometry_msgs/Pose pose

   # Identifying string. Must be globally unique in
   # the topic that this message is sent through.
   string name

   # Short description (< 40 characters).
   string description

   # Scale to be used for default controls (default=1).
   float32 scale

   # All menu and submenu entries associated with this marker.
   MenuEntry[] menu_entries

   # List of controls displayed for this marker.
   InteractiveMarkerControl[] controls

##################################################################################

 */
package visualization_msgs.msg.dds;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.*;
import us.ihmc.jros2.ROS2Message;

public class InteractiveMarker implements ROS2Message<InteractiveMarker>
{
   public static final java.lang.String name = "visualization_msgs::msg::dds_::InteractiveMarker_";

   /**
      Time/frame info.
      If header.time is set to 0, the marker will be retransformed into
      its frame on each timestep. You will receive the pose feedback
      in the same frame.
      Otherwise, you might receive feedback in a different frame.
      For rviz, this will be the current 'fixed frame' set by the user.
   */
   private final std_msgs.msg.dds.Header header_;
   /**
      Initial pose. Also, defines the pivot point for rotations.
   */
   private final geometry_msgs.msg.dds.Pose pose_;
   /**
      Identifying string. Must be globally unique in
      the topic that this message is sent through.
   */
   private final StringBuilder name_;
   /**
      Short description (< 40 characters).
   */
   private final StringBuilder description_;
   /**
      Scale to be used for default controls (default=1).
   */
   private float scale_;
   /**
      All menu and submenu entries associated with this marker.
   */
   private final IDLObjectSequence<visualization_msgs.msg.dds.MenuEntry> menu_entries_;
   /**
      List of controls displayed for this marker.
   */
   private final IDLObjectSequence<visualization_msgs.msg.dds.InteractiveMarkerControl> controls_;

   public InteractiveMarker()
   {
      header_ = new std_msgs.msg.dds.Header();
      pose_ = new geometry_msgs.msg.dds.Pose();
      name_ = new StringBuilder();
      description_ = new StringBuilder();
      menu_entries_ = new IDLObjectSequence<visualization_msgs.msg.dds.MenuEntry>(visualization_msgs.msg.dds.MenuEntry.class);
      controls_ = new IDLObjectSequence<visualization_msgs.msg.dds.InteractiveMarkerControl>(visualization_msgs.msg.dds.InteractiveMarkerControl.class);

   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += header_.calculateSizeBytes(currentAlignment);
      currentAlignment += pose_.calculateSizeBytes(currentAlignment);
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4) + (1 * name_.length()) + 1; // name_
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4) + (1 * description_.length()) + 1; // description_
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // scale_
      currentAlignment += menu_entries_.calculateSizeBytes(currentAlignment);
      currentAlignment += controls_.calculateSizeBytes(currentAlignment);

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      header_.serialize(buffer);
      pose_.serialize(buffer);
      buffer.writeString(name_);
      buffer.writeString(description_);
      buffer.writeFloat(scale_);
      menu_entries_.serialize(buffer);
      controls_.serialize(buffer);

   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      header_.deserialize(buffer);
      pose_.deserialize(buffer);
      buffer.readString(name_);
      buffer.readString(description_);
      scale_ = buffer.readFloat();
      menu_entries_.deserialize(buffer);
      controls_.deserialize(buffer);

   }

   @Override
   public void set(InteractiveMarker from)
   {
      header_.set(from.header_);
      pose_.set(from.pose_);
      name_.delete(0, name_.length());
      name_.insert(0, from.name_);
      description_.delete(0, description_.length());
      description_.insert(0, from.description_);
      scale_ = from.scale_;
      menu_entries_.set(from.menu_entries_);
      controls_.set(from.controls_);

   }

   public std_msgs.msg.dds.Header getHeader()
   {
      return header_;
   }

   public geometry_msgs.msg.dds.Pose getPose()
   {
      return pose_;
   }

   public StringBuilder getName()
   {
      return name_;
   }

   public StringBuilder getDescription()
   {
      return description_;
   }

   public float getScale()
   {
      return scale_;
   }

   public void setScale(float scale_)
   {
      this.scale_ = scale_;
   }

   public IDLObjectSequence<visualization_msgs.msg.dds.MenuEntry> getMenuEntries()
   {
      return menu_entries_;
   }

   public IDLObjectSequence<visualization_msgs.msg.dds.InteractiveMarkerControl> getControls()
   {
      return controls_;
   }


}