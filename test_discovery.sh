#!/bin/bash

# Source ROS2
source /opt/ros/jazzy/setup.bash
export ROS_DOMAIN_ID=143

echo "Starting ROS2 node list monitor in background..."
while true; do
    echo "=== ros2 node list at $(date +%H:%M:%S) ==="
    ros2 node list
    sleep 2
done &
MONITOR_PID=$!

echo "Building and running Test.java..."
cd /home/d/Desktop/jros2

# Build
./gradlew :jros2-test:classes

# Run the test
timeout 15 java -cp "build/libs/*:$(find ~/.m2/repository/us/ihmc -name "*.jar" | tr '\n' ':')$(find ~/.m2/repository/org/bytedeco -name "*.jar" | tr '\n' ':')src/test/build/classes/java/main" us.ihmc.jros2.Test &
TEST_PID=$!

echo "Test running with PID $TEST_PID"
echo "Monitor running with PID $MONITOR_PID"

# Wait for test
wait $TEST_PID

# Stop monitor
kill $MONITOR_PID 2>/dev/null

echo "Done"
