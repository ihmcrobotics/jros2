#!/usr/bin/env python3
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
"""Checks an R8 dex output (dexdump + mapping.txt) for the members jros2 looks up by name at runtime.

Usage: check_dex.py <mapping.txt> <dexdump.txt> <native-method-count> <expect-typeHash: true|false>
Prints one line per expectation and exits with the number of failed expectations.
"""
import re
import sys


def read_mapping(path):
    """original class name -> obfuscated class name (classes absent from the mapping keep their name or were removed)."""
    renames = {}
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            m = re.match(r"^(\S+) -> (\S+):$", line.rstrip("\n"))
            if m:
                renames[m.group(1)] = m.group(2)
    return renames


def read_dexdump(path):
    """class descriptor -> list of (name, type, access) for every field and method."""
    classes = {}
    current = None
    member_name = None
    member_type = None
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            m = re.match(r"^\s+Class descriptor\s+:\s+'(L[^']+;)'", line)
            if m:
                current = m.group(1)
                classes.setdefault(current, [])
                continue
            m = re.match(r"^\s+name\s+:\s+'([^']*)'", line)
            if m:
                member_name = m.group(1)
                continue
            m = re.match(r"^\s+type\s+:\s+'([^']*)'", line)
            if m:
                member_type = m.group(1)
                continue
            m = re.match(r"^\s+access\s+:\s+0x[0-9a-f]+ \(([^)]*)\)", line)
            if m and current is not None and member_name is not None:
                classes[current].append((member_name, member_type, m.group(1)))
                member_name = member_type = None
    return classes


def main():
    mapping, dexdump, native_count, expect_type_hash = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4] == "true"
    renames = read_mapping(mapping)
    classes = read_dexdump(dexdump)

    def descriptor(java_name):
        return "L" + renames.get(java_name, java_name).replace(".", "/") + ";"

    def members(java_name):
        return classes.get(descriptor(java_name))

    failures = 0

    def expect(what, ok, detail=""):
        nonlocal failures
        if not ok:
            failures += 1
        print("[dex-check] %-4s %s%s" % ("OK" if ok else "FAIL", what, (" (" + detail + ")") if detail else ""))

    natives_class = "us.ihmc.fastddsjava.natives.fastddsjava"
    natives = members(natives_class)
    expect("class %s keeps its name" % natives_class, renames.get(natives_class, natives_class) == natives_class and natives is not None,
           "renamed to " + renames.get(natives_class, "?") if natives_class in renames and renames[natives_class] != natives_class else "")
    kept_natives = [n for (n, t, a) in (natives or []) if "NATIVE" in a]
    expect("all %d native methods are present under their JNI names" % native_count, len(kept_natives) == native_count,
           "%d present" % len(kept_natives))

    callback = "us.ihmc.fastddsjava.natives.fastddsjavaCallback"
    callback_members = members(callback)
    expect("interface %s keeps its name and call()V" % callback,
           renames.get(callback, callback) == callback and callback_members is not None
           and any(n == "call" and t == "()V" for (n, t, a) in callback_members))

    string_members = members("std_msgs.String_") or []
    expect("std_msgs.String_ keeps public static final String 'name'",
           any(n == "name" and t == "Ljava/lang/String;" and "STATIC" in a for (n, t, a) in string_members))
    if expect_type_hash:
        expect("std_msgs.String_ keeps public static final String 'typeHash'",
               any(n == "typeHash" and t == "Ljava/lang/String;" and "STATIC" in a for (n, t, a) in string_members))

    expect("std_msgs.ColorRGBA keeps its public no-arg constructor (ROS2Message.createInstance)",
           any(n == "<init>" and t == "()V" for (n, t, a) in (members("std_msgs.ColorRGBA") or [])))

    header = descriptor("std_msgs.Header")
    expect("sensor_msgs.Image keeps a public method returning std_msgs.Header (ROS2Message.getHeaderMethod)",
           any(t == "()" + header for (n, t, a) in (members("sensor_msgs.Image") or [])))

    expect("sensor_msgs.PointField keeps its public no-arg constructor (IDLObjectSequence)",
           any(n == "<init>" and t == "()V" for (n, t, a) in (members("sensor_msgs.PointField") or [])))

    print("[dex-check] %s" % ("ALL CHECKS PASSED" if failures == 0 else "%d CHECK(S) FAILED" % failures))
    sys.exit(failures)


if __name__ == "__main__":
    main()
