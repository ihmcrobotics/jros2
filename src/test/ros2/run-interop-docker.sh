#!/bin/bash
#  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Runs ROS2InteropTest against a ROS 2 distro in Docker (official ros:<distro> image), e.g.:
#   src/test/ros2/run-interop-docker.sh foxy
#   src/test/ros2/run-interop-docker.sh lyrical
#
# Builds example_interfaces (not in the ros:<distro> images) and jros2_interop in a container that shares the host network
# and IPC, and generates host-side wrapper executables that run the rclcpp nodes inside it. Then runs the test with Gradle.
set -e

DISTRO=${1:?usage: run-interop-docker.sh <distro>}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
WORK_DIR="${JROS2_INTEROP_WORK_DIR:-$REPO_ROOT/build/interop-$DISTRO}"
CONTAINER="jros2-interop-$DISTRO"

mkdir -p "$WORK_DIR/ws/src" "$WORK_DIR/wrappers/lib/jros2_interop"
if [ ! -d "$WORK_DIR/ws/src/example_interfaces" ]; then
  git clone --depth 1 -b "$DISTRO" https://github.com/ros2/example_interfaces.git "$WORK_DIR/ws/src/example_interfaces"
fi
cp -r "$SCRIPT_DIR/jros2_interop" "$WORK_DIR/ws/src/"

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  docker run -d --name "$CONTAINER" --network host --ipc host -v "$WORK_DIR/ws:/ws" "ros:$DISTRO" sleep infinity
fi
docker exec "$CONTAINER" bash -c "source /opt/ros/$DISTRO/setup.bash && cd /ws && colcon build --cmake-args -DBUILD_TESTING=OFF"

echo "# Executables run inside the $CONTAINER container" > "$WORK_DIR/wrappers/setup.bash"
for exe in add_two_ints_server add_two_ints_client param_client fibonacci_server fibonacci_client; do
  cat > "$WORK_DIR/wrappers/lib/jros2_interop/$exe" <<WRAPPER
#!/bin/bash
# Runs $exe inside $CONTAINER. A unique tag is appended as an extra argument (ignored by the executable) so the process in the
# container can be killed when this wrapper is terminated.
tag="jros2tag_\$\$_\$RANDOM"
trap 'docker exec $CONTAINER pkill -f -- "\$tag" >/dev/null 2>&1; exit 143' TERM INT
docker exec -e ROS_DOMAIN_ID="\$ROS_DOMAIN_ID" -e RMW_IMPLEMENTATION="\$RMW_IMPLEMENTATION" $CONTAINER \\
  timeout 90 bash -c 'source /opt/ros/$DISTRO/setup.bash && source /ws/install/setup.bash && exec /ws/install/jros2_interop/lib/jros2_interop/$exe "\$@"' _ "\$@" "\$tag" &
wait \$!
WRAPPER
  chmod +x "$WORK_DIR/wrappers/lib/jros2_interop/$exe"
done

cd "$REPO_ROOT"
JROS2_INTEROP_SETUP="$WORK_DIR/wrappers/setup.bash" ./gradlew test --tests '*ROS2InteropTest*'
