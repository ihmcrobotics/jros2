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

                  "fastdds/rtps/common/Time_t.hpp",

                  "fastdds/dds/subscriber/SampleState.hpp",
                  "fastdds/dds/subscriber/ViewState.hpp",
                  "fastdds/dds/subscriber/InstanceState.hpp",
                  "fastdds/dds/subscriber/SampleInfo.hpp",
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

      infoMap.put(new Info("TIME_T_INFINITE_SECONDS").javaText("public static final int TIME_T_INFINITE_SECONDS = 0x7fffffff;"));
      infoMap.put(new Info("TIME_T_INFINITE_NANOSECONDS").javaText("public static final int TIME_T_INFINITE_NANOSECONDS = 0xffffffff;"));

      infoMap.put(new Info("eprosima::fastdds::rtps::Time_t::nanosec").annotations("@Function"));

      infoMap.put(new Info("eprosima::fastdds::dds::Duration_t").pointerTypes("Time_t").cast());

      infoMap.put(new Info("eprosima::fastdds::dds::TopicDataType").pointerTypes("Pointer"));
      infoMap.put(new Info("eprosima::fastdds::dds::DataReaderListener").pointerTypes("Pointer"));
      
      infoMap.put(new Info("std::vector<uint8_t>").pointerTypes("ByteVector").define());

      infoMap.put(new Info("eprosima::fastdds::rtps::InstanceHandle_t", "InstanceHandle_t").skip());
      infoMap.put(new Info("eprosima::fastdds::rtps::SampleIdentity").skip());
   }
}