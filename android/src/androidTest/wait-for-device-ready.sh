#!/bin/bash
#
#  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
# Waits until the emulator can run instrumented tests that use app storage. sys.boot_completed, which the emulator action
# waits for, comes before the user is unlocked and before /sdcard is mounted: API 36.1 then failed with
# "mkdir failed: ENOENT" on the test package's data directory.
#
# usage: wait-for-device-ready.sh [timeout seconds, default 300]
set -u

deadline=$((SECONDS + ${1:-300}))

ready() {
   adb shell dumpsys user 2> /dev/null | grep -q 'State: RUNNING_UNLOCKED' || return 1
   adb shell 'test -d /sdcard/Android' > /dev/null 2>&1 || return 1
   adb shell pm path android > /dev/null 2>&1 || return 1
}

until ready; do
   if [ "$SECONDS" -ge "$deadline" ]; then
      echo "device not ready after ${1:-300} s; running the tests anyway" >&2
      adb shell dumpsys user 2> /dev/null | grep 'State:' >&2
      exit 0
   fi
   sleep 2
done
echo "device ready after ${SECONDS} s"
