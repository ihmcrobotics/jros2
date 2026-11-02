# jros2_interop

rclcpp nodes used by `ROS2InteropTest` to check that jros2 interoperates with ROS 2 nodes using `rmw_fastrtps_cpp`:

| Executable | Role |
|---|---|
| `add_two_ints_server`, `add_two_ints_client` | `example_interfaces/srv/AddTwoInts` service server and client |
| `param_client` | `rclcpp::SyncParametersClient` listing, getting and setting the parameters of a jros2 node |
| `fibonacci_server`, `fibonacci_client` | `example_interfaces/action/Fibonacci` action server and client (rclcpp_action) |

```bash
# In a ROS 2 workspace with rclcpp, rclcpp_action and example_interfaces
cp -r <jros2>/src/test/ros2/jros2_interop ~/ros2_ws/src/
cd ~/ros2_ws && colcon build --packages-up-to jros2_interop

# From the jros2 repository
JROS2_INTEROP_SETUP=~/ros2_ws/install/setup.bash ./gradlew test --tests '*ROS2InteropTest*'
```

The test is skipped when `JROS2_INTEROP_SETUP` is not set.

Or against any distro with Docker, using the official `ros:<distro>` image:

```bash
src/test/ros2/run-interop-docker.sh foxy
```

Tested with Foxy, Humble and Lyrical (official Docker images) and Jazzy (built from source).

Note for Foxy: its rclcpp_action client calls the feedback callback with the client's goal mutex locked, so calling
`async_cancel_goal` from that callback deadlocks. `fibonacci_client` cancels from `main` instead.
