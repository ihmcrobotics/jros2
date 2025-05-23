/*
 * This class was automatically generated by jros2.
 * Do not modify this file directly.

##################################################################################
This file was generated from the following content:
(visualization_msgs/MenuEntry.msg)
##################################################################################
   # MenuEntry message.
   #
   # Each InteractiveMarker message has an array of MenuEntry messages.
   # A collection of MenuEntries together describe a
   # menu/submenu/subsubmenu/etc tree, though they are stored in a flat
   # array.  The tree structure is represented by giving each menu entry
   # an ID number and a "parent_id" field.  Top-level entries are the
   # ones with parent_id = 0.  Menu entries are ordered within their
   # level the same way they are ordered in the containing array.  Parent
   # entries must appear before their children.
   #
   # Example:
   # - id = 3
   #   parent_id = 0
   #   title = "fun"
   # - id = 2
   #   parent_id = 0
   #   title = "robot"
   # - id = 4
   #   parent_id = 2
   #   title = "pr2"
   # - id = 5
   #   parent_id = 2
   #   title = "turtle"
   #
   # Gives a menu tree like this:
   #  - fun
   #  - robot
   #    - pr2
   #    - turtle

   # ID is a number for each menu entry.  Must be unique within the
   # control, and should never be 0.
   uint32 id

   # ID of the parent of this menu entry, if it is a submenu.  If this
   # menu entry is a top-level entry, set parent_id to 0.
   uint32 parent_id

   # menu / entry title
   string title

   # Arguments to command indicated by command_type (below)
   string command

   # Command_type stores the type of response desired when this menu
   # entry is clicked.
   # FEEDBACK: send an InteractiveMarkerFeedback message with menu_entry_id set to this entry's id.
   # ROSRUN: execute "rosrun" with arguments given in the command field (above).
   # ROSLAUNCH: execute "roslaunch" with arguments given in the command field (above).
   uint8 FEEDBACK=0
   uint8 ROSRUN=1
   uint8 ROSLAUNCH=2
   uint8 command_type

##################################################################################

 */
package visualization_msgs.msg.dds;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.idl.*;
import us.ihmc.jros2.ROS2Message;

/**
   MenuEntry message.

   Each InteractiveMarker message has an array of MenuEntry messages.
   A collection of MenuEntries together describe a
   menu/submenu/subsubmenu/etc tree, though they are stored in a flat
   array.  The tree structure is represented by giving each menu entry
   an ID number and a "parent_id" field.  Top-level entries are the
   ones with parent_id = 0.  Menu entries are ordered within their
   level the same way they are ordered in the containing array.  Parent
   entries must appear before their children.

   Example:
   - id = 3
   parent_id = 0
   title = "fun"
   - id = 2
   parent_id = 0
   title = "robot"
   - id = 4
   parent_id = 2
   title = "pr2"
   - id = 5
   parent_id = 2
   title = "turtle"

   Gives a menu tree like this:
   - fun
   - robot
   - pr2
   - turtle
*/
public class MenuEntry implements ROS2Message<MenuEntry>
{
   public static final java.lang.String name = "visualization_msgs::msg::dds_::MenuEntry_";

   /**
      ID is a number for each menu entry.  Must be unique within the
      control, and should never be 0.
   */
   private int id_;
   /**
      ID of the parent of this menu entry, if it is a submenu.  If this
      menu entry is a top-level entry, set parent_id to 0.
   */
   private int parent_id_;
   /**
      menu / entry title
   */
   private final StringBuilder title_;
   /**
      Arguments to command indicated by command_type (below)
   */
   private final StringBuilder command_;
   /**
      Command_type stores the type of response desired when this menu
      entry is clicked.
      FEEDBACK: send an InteractiveMarkerFeedback message with menu_entry_id set to this entry's id.
      ROSRUN: execute "rosrun" with arguments given in the command field (above).
      ROSLAUNCH: execute "roslaunch" with arguments given in the command field (above).
   */
   public static final byte FEEDBACK = 0;
   public static final byte ROSRUN = 1;
   public static final byte ROSLAUNCH = 2;
   private byte command_type_;

   public MenuEntry()
   {
      title_ = new StringBuilder();
      command_ = new StringBuilder();

   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // id_
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // parent_id_
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4) + (1 * title_.length()) + 1; // title_
      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4) + (1 * command_.length()) + 1; // command_
      currentAlignment += 1 + CDRBuffer.alignment(currentAlignment, 1); // command_type_

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      buffer.writeInt(id_);
      buffer.writeInt(parent_id_);
      buffer.writeString(title_);
      buffer.writeString(command_);
      buffer.writeByte(command_type_);

   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      id_ = buffer.readInt();
      parent_id_ = buffer.readInt();
      buffer.readString(title_);
      buffer.readString(command_);
      command_type_ = buffer.readByte();

   }

   @Override
   public void set(MenuEntry from)
   {
      id_ = from.id_;
      parent_id_ = from.parent_id_;
      title_.delete(0, title_.length());
      title_.insert(0, from.title_);
      command_.delete(0, command_.length());
      command_.insert(0, from.command_);
      command_type_ = from.command_type_;

   }

   public int getId()
   {
      return id_;
   }

   public void setId(int id_)
   {
      this.id_ = id_;
   }

   public int getParentId()
   {
      return parent_id_;
   }

   public void setParentId(int parent_id_)
   {
      this.parent_id_ = parent_id_;
   }

   public StringBuilder getTitle()
   {
      return title_;
   }

   public StringBuilder getCommand()
   {
      return command_;
   }

   public byte getCommandType()
   {
      return command_type_;
   }

   public void setCommandType(byte command_type_)
   {
      this.command_type_ = command_type_;
   }


}