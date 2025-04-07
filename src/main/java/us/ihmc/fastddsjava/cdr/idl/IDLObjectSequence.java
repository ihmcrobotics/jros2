package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;

import java.lang.reflect.Array;
import java.util.Arrays;

public class IDLObjectSequence<T extends CDRSerializable> extends IDLSequence<IDLObjectSequence<T>>
{
   private final Class<T> clazz;
   private T[] elements;
   private int position;

   public IDLObjectSequence(int capacity, Class<T> clazz)
   {
      super(capacity);
      this.clazz = clazz;
   }

   public IDLObjectSequence(Class<T> clazz)
   {
      this.clazz = clazz;
   }

   public void add(T element)
   {
      assert elements != null;
      assert position <= elements.length;

      elements[position++] = element;
   }

   @Override
   protected void ensureCapacity(int capacity)
   {
      if (elements == null)
      {
         elements = (T[]) Array.newInstance(clazz, capacity);
      }
      else if (elements.length != capacity)
      {
         elements = Arrays.copyOf(elements, capacity);
      }
   }

   @Override
   public int elementSizeBytes(int i)
   {
      assert elements != null;

      return elements[i].calculateSizeBytes();
   }

   @Override
   public void readElement(CDRBuffer buffer)
   {
      assert elements != null;
      assert position <= elements.length;

      T element = elements[position++];

      element.deserialize(buffer);
   }

   @Override
   public void writeElement(int i, CDRBuffer buffer)
   {
      assert elements != null;

      elements[i].serialize(buffer);
   }

   @Override
   public void set(IDLObjectSequence<T> other)
   {
      assert clazz == other.clazz;

      elements = Arrays.copyOf(other.elements, other.elements.length);
   }
}
