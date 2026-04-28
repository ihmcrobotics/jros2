# jros2 Integration Tests

Standalone ROS2 workspace for testing jros2 interoperability with Python.

Contains both Java and Python test sources in a single ROS2 package.

## Prerequisites

The parent jros2 project must be built first:

```bash
# From jros2 root directory
./gradlew jar
```

This creates the jros2 JAR files needed by the integration tests.

## Build

```bash
# Set ROS distribution
export ROS_DISTRO=jazzy

# Build everything (Java + ROS2 workspace)
./gradlew build
```

## Run Tests

```bash
# Run all integration tests
export ROS_DISTRO=jazzy
./gradlew test
```

This will run all integration tests via ROS2 launch files.

## Gradle Tasks

```bash
# Build Java test classes
./gradlew buildJavaTests

# Build ROS2 workspace only
./gradlew buildROS2Workspace

# Build everything
./gradlew build

# Run all integration tests
./gradlew test

# Clean
./gradlew clean
```

## Manual Testing

After building, you can run tests manually:

```bash
# Source the workspace
export ROS_DISTRO=jazzy
source install/setup.bash
export ROS_DOMAIN_ID=200

# Run all tests
ros2 launch jros2_interop_tests all_tests.launch.py

# Run individual tests
ros2 launch jros2_interop_tests pubsub_test.launch.py
ros2 launch jros2_interop_tests pubsub_reverse_test.launch.py
ros2 launch jros2_interop_tests service_test.launch.py
ros2 launch jros2_interop_tests service_reverse_test.launch.py
ros2 launch jros2_interop_tests action_test.launch.py
ros2 launch jros2_interop_tests action_reverse_test.launch.py
ros2 launch jros2_interop_tests parameter_test.launch.py
```

**Known Issues:**
- Parameter service discovery from Python to Java fails (services not found)
- Action test from Java to Python needs timeout adjustment

## Package Structure

```
src/jros2_interop_tests/
├── src/main/java/              # Java test sources
├── jros2_interop_tests/        # Python test sources  
├── scripts/                    # Java wrapper scripts
├── launch/                     # ROS2 launch files
├── package.xml
└── setup.py
```

## Requirements

- ROS2 (Jazzy or newer)
- Java 17+
- Python 3.8+
- ROS2 packages: rclpy, std_msgs, example_interfaces
- jros2 JAR files (built from parent project)
