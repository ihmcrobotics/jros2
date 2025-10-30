package us.ihmc.jros2.generator;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import us.ihmc.jros2.generator.context.MsgContext;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class ROS2InterfaceUtil
{
   public static List<MsgContext> findMsgsInResources()
   {
      List<MsgContext> msgs = new ArrayList<>();

      URL manifestUrl = getResource("ros2_interfaces.manifest");
      if (manifestUrl == null)
      {
         return msgs; // No manifest, return empty list
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(manifestUrl.openStream())))
      {
         String line;
         while ((line = reader.readLine()) != null)
         {
            if (!line.endsWith(".msg"))
               continue;

            String packageName = readPackageNameFromManifestLine(line);

            String msgContent = readResourceAsString(line);
            msgs.add(new MsgContext(packageName, line, msgContent));
         }
      }
      catch (IOException e)
      {
         throw new RuntimeException("Error reading manifest file", e);
      }

      return msgs;
   }

   public static List<MsgContext> findMsgsInPkg(Path ros2pkgPath)
   {
      List<MsgContext> msgs = new LinkedList<>();

      if (!ros2pkgPath.resolve("package.xml").toFile().exists())
      {
         throw new RuntimeException(ros2pkgPath + " is not a ROS 2 package path");
      }

      String packageName;
      try
      {
         packageName = parsePackageXMLForPackageName(ros2pkgPath.resolve("package.xml"));
      }
      catch (Exception e)
      {
         throw new RuntimeException("Unable to parse package name");
      }

      File msgDir = ros2pkgPath.resolve("msg").toFile();

      if (!msgDir.exists() || !msgDir.isDirectory())
      {
         throw new RuntimeException(ros2pkgPath + " does not contain a msg directory");
      }

      for (File file : Objects.requireNonNull(msgDir.listFiles((f, name) -> name.endsWith(".msg"))))
      {
         String fileContent;

         try
         {
            fileContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
         }
         catch (IOException e)
         {
            throw new RuntimeException("Could not read .msg file:  " + file.getName());
         }

         MsgContext context = new MsgContext(packageName, file.getName(), fileContent);

         msgs.add(context);
      }

      return msgs;
   }

   public static String parsePackageXMLForPackageName(Path xmlPath) throws Exception
   {
      return parsePackageXMLForPackageName(Files.readString(xmlPath));
   }

   public static String parsePackageXMLForPackageName(String xml) throws Exception
   {
      InputSource is = new InputSource(new StringReader(xml));

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      DocumentBuilder builder = factory.newDocumentBuilder();

      Document document = builder.parse(is);
      document.getDocumentElement().normalize();

      return document.getElementsByTagName("name").item(0).getTextContent();
   }

   private static URL getResource(String resourcePath)
   {
      return ROS2MessageGenerator.class.getClassLoader().getResource(resourcePath);
   }

   private static String readResourceAsString(String resourcePath) throws IOException
   {
      URL url = getResource(resourcePath);
      if (url == null)
      {
         throw new RuntimeException("Resource not found: " + resourcePath);
      }
      try (InputStream stream = url.openStream())
      {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   private static String getWalkedBackDir(String manifestLine)
   {
      int lastSlash = manifestLine.lastIndexOf('/');
      String withoutFile = manifestLine.substring(0, lastSlash);
      int secondSlash = withoutFile.lastIndexOf('/');
      return withoutFile.substring(0, secondSlash);
   }

   private static String readPackageNameFromManifestLine(String manifestLine) throws IOException
   {
      String walkedBackDir = getWalkedBackDir(manifestLine);
      String packageXmlPath = walkedBackDir + "/package.xml";
      String xml = readResourceAsString(packageXmlPath);
      try
      {
         return parsePackageXMLForPackageName(xml);
      }
      catch (Exception e)
      {
         throw new RuntimeException("Unable to parse package name for: " + manifestLine, e);
      }
   }
}
