package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.function.Supplier;

public class IDLObjectSequence<T extends CDRSerializable> extends IDLSequence<IDLObjectSequence<T>>
{
   private T[] elements;
   private int position;

   private final Supplier<T> allocator;

   public IDLObjectSequence(int capacity, Class<T> clazz)
   {
      this(capacity, () -> classAllocator(clazz));
   }

   public IDLObjectSequence(int capacity, Supplier<T> allocator)
   {
      this.allocator = allocator;
      position = 0;

      ensureMinCapacity(capacity);
   }

   public IDLObjectSequence(Class<T> clazz)
   {
      this(() -> classAllocator(clazz));
   }

   public IDLObjectSequence(Supplier<T> allocator)
   {
      this.allocator = allocator;
      position = 0;
   }

   private static <T extends CDRSerializable> T classAllocator(Class<T> clazz)
   {
      try
      {
         return clazz.getDeclaredConstructor().newInstance();
      }
      catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e)
      {
         throw new RuntimeException(e);
      }
   }

   @Override
   public int elements()
   {
      return position;
   }

   @Override
   public int capacity()
   {
      if (elements == null)
      {
         return 0;
      }

      return elements.length;
   }

   @Override
   public void clear()
   {
      position = 0;
   }

   public void add(T element)
   {
      if (elements == null)
      {
         ensureMinCapacity(DEFAULT_INITIAL_CAPACITY);
      }
      else if (position == elements.length)
      {
         ensureMinCapacity(2 * elements.length);
      }

      elements[position++] = element;
   }

   public T add()
   {
      if (elements == null)
      {
         ensureMinCapacity(DEFAULT_INITIAL_CAPACITY);
      }
      else if (position == elements.length)
      {
         ensureMinCapacity(2 * elements.length);
      }

      if (elements[position] == null)
         elements[position] = allocator.get();

      return elements[position++];
   }

   public T get(int index)
   {
      assert index < elements();
      return elements[index];
   }

   public T[] getArrayUnsafe()
   {
      return elements;
   }

   @Override
   @SuppressWarnings("unchecked")
   protected void ensureMinCapacity(int desiredCapacity)
   {
      if (elements == null)
      {
         elements = (T[]) new CDRSerializable[desiredCapacity];
      }
      else if (elements.length < desiredCapacity)
      {
         elements = Arrays.copyOf(elements, desiredCapacity);
      }
   }

   @Override
   public int elementSizeBytes(int i)
   {
      assert elements != null;
      assert i < elements();

      return elements[i].calculateSizeBytes();
   }

   @Override
   public void readElement(CDRBuffer buffer)
   {
      assert elements != null;
      assert position < elements.length;

      T element = elements[position++];

      element.deserialize(buffer);
   }

   @Override
   public void writeElement(int i, CDRBuffer buffer)
   {
      assert elements != null;
      assert i < elements();

      elements[i].serialize(buffer);
   }

   @Override
   public void set(IDLObjectSequence<T> other)
   {
      assert other.elements != null;

      clear();

      int othersElements = other.elements();
      ensureMinCapacity(othersElements);

      System.arraycopy(other.elements, 0, elements, 0, othersElements);
      position = other.elements();
   }
}
