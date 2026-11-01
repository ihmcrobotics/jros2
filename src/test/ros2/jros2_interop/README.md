# jros2_interop

rclcpp `AddTwoInts` server and client used by `ROS2ServiceInteropTest` to check that jros2 services and clients
interoperate with ROS 2 nodes using `rmw_fastrtps_cpp`.

```bash
# In a ROS 2 workspace (tested with Jazzy built from source; any distro with rclcpp and example_interfaces works)
cp -r <jros2>/src/test/ros2/jros2_interop ~/ros2_ws/src/
cd ~/ros2_ws && colcon build --packages-up-to jros2_interop

# From the jros2 repository
JROS2_INTEROP_SETUP=~/ros2_ws/install/setup.bash ./gradlew test --tests '*ROS2ServiceInteropTest*'
```

The test is skipped when `JROS2_INTEROP_SETUP` is not set.
