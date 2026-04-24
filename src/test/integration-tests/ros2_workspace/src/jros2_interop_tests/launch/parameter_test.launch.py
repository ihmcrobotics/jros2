#!/usr/bin/env python3
"""
Launch file for parameter test.
Tests bidirectional Java-Python ROS2 parameter interoperability via parameter services.
"""

from launch import LaunchDescription
from launch.actions import TimerAction
from launch_ros.actions import Node


def generate_launch_description():
    """Generate launch description for parameter test."""

    return LaunchDescription([
        # Java parameter test node (creates parameters and parameter services)
        Node(
            package='jros2_interop_tests',
            executable='java_parameter_test',
            name='java_parameter_test',
            output='screen',
        ),

        # Python parameter client (starts after 2 seconds to allow Java node to initialize)
        TimerAction(
            period=2.0,
            actions=[
                Node(
                    package='jros2_interop_tests',
                    executable='test_parameter_client',
                    name='python_parameter_client',
                    output='screen',
                ),
            ]
        ),
    ])
