#!/usr/bin/env python3
"""
Launch file for reverse publisher-subscriber integration test.
Starts Java publisher and Python subscriber.
"""

from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument
from launch_ros.actions import Node
def generate_launch_description():
    """Generate launch description for reverse pub/sub integration test."""
    return LaunchDescription([

        # Java publisher node
        Node(
            package='jros2_interop_tests',
            executable='java_publisher_test',
            name='java_publisher',
            output='screen',
        ),

        # Python subscriber node
        Node(
            package='jros2_interop_tests',
            executable='test_subscriber',
            name='python_subscriber',
            output='screen',
        ),
    ])
