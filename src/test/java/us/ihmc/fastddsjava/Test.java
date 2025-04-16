package us.ihmc.fastddsjava;

public class Test
{
   public static void main(String[] args)
   {
      String s = "𓉓";

      for (int i = 0; i < s.length(); i++)
      {
         System.out.println(s.codePointAt(i));
      }

//      for (int i = 0; i < s.codePointCount(0, s.length()); i++)
//      {
//         char[] chars = Character.toChars(s.codePointAt(i));
//
//         for (char aChar : chars)
//         {
//            System.out.printf("%02X\n", (int) aChar); // Output: 41
//         }
//      }
   }
}
