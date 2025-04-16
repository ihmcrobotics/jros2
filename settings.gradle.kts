rootProject.name = "jros2"

include("examples:ros2-opencv-webcam")
findProject(":examples:ros2-opencv-webcam")?.name = "ros2-opencv-webcam"
