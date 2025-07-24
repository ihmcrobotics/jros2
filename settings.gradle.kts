rootProject.name = "jros2"

include("examples:ros2-opencv-webcam")
findProject(":examples:ros2-opencv-webcam")?.name = "ros2-opencv-webcam"

include("examples:ros2-talker-listener")
findProject(":examples:ros2-talker-listener")?.name = "ros2-talker-listener"

include("examples:custom-message-class")
findProject(":examples:custom-message-class")?.name = "custom-message-class"
