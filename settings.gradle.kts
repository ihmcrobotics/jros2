rootProject.name = "jros2"

include("examples:ros2-opencv-webcam")
findProject(":examples:ros2-opencv-webcam")?.name = "ros2-opencv-webcam"

include("examples:ros2-talker-listener")
findProject(":examples:ros2-talker-listener")?.name = "ros2-ros2-talker-listener"
