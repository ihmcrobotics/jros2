package us.ihmc.fastdds;

import org.bytedeco.javacpp.annotation.Platform;
import org.bytedeco.javacpp.annotation.Properties;
import org.bytedeco.javacpp.tools.Info;
import org.bytedeco.javacpp.tools.InfoMap;
import org.bytedeco.javacpp.tools.InfoMapper;

@Properties(value = {
      @Platform(
            includepath = "install/include",
            include = {
                  "fastddsjava.h",
            },
            linkpath = "install/lib",
            link = {"fastcdr", "fastdds"},
            preload = "jnifastdds"
      )
},
      target = "us.ihmc.fastdds",
      global = "us.ihmc.fastdds.global.fastdds"
)
public class fastddsConfig implements InfoMapper
{
   @Override
   public void map(InfoMap infoMap)
   {
      infoMap.put(new Info("JAVACPP_SKIP").skip());
      infoMap.put(new Info("eprosima::fastdds::dds::TopicDataType").pointerTypes("Pointer"));
      infoMap.put(new Info("eprosima::fastdds::dds::DataReaderListener").pointerTypes("Pointer"));
      infoMap.put(new Info("std::vector<uint8_t>").pointerTypes("fastddsjava_ByteVector").define());
   }
}