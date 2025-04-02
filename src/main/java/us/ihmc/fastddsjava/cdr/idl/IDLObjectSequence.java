package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;

import java.lang.reflect.Constructor;
import java.util.ArrayList;

public class IDLObjectSequence<T extends CDRSerializable> extends ArrayList<T> implements IDLSequence<IDLObjectSequence<T>>
{
   private final Constructor<T> clazzConstructor;

   public IDLObjectSequence(Class<T> clazz, int initialCapacity)
   {
      super(initialCapacity);
      try
      {
         this.clazzConstructor = clazz.getDeclaredConstructor();
      }
      catch (NoSuchMethodException e)
      {
         throw new RuntimeException(e);
      }
   }

   public IDLObjectSequence(Class<T> clazz)
   {
      this(clazz, 10);
   }

   @Override
   public int elements()
   {
      return size();
   }

   @Override
   public int elementSizeBytes(int i)
   {
      T element = get(i);

      return element.calculateSizeBytes();
   }

   @Override
   public void readElement(CDRBuffer buffer)
   {
      T element = createInstance();

      element.deserialize(buffer);

      add(element);
   }

   @Override
   public void writeElement(int i, CDRBuffer buffer)
   {
      T element = get(i);

      element.serialize(buffer);
   }

   @Override
   public void set(IDLObjectSequence<T> other)
   {
      // TODO: speed this up?
      clear();

      this.addAll(other);
   }

   private T createInstance()
   {
      try
      {
         return clazzConstructor.newInstance();
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }
}
