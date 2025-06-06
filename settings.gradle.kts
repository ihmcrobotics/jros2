rootProject.name = "jros2"

include("examples:ros2-opencv-webcam")
findProject(":examples:ros2-opencv-webcam")?.name = "ros2-opencv-webcam"

include("examples:ros2-talker-listener")
findProject(":examples:ros2-talker-listener")?.name = "ros2-talker-listener"

include("examples:jros2-benchmark")
findProject("examples:jros2-benchmark")?.name = "jros2-benchmark"

include("examples:ihmc-ros2-library-benchmark")
findProject("examples:ihmc-ros2-library-benchmark")?.name = "ihmc-ros2-library-benchmark"
