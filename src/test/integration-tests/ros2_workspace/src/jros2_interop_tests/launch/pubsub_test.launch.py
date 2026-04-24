#!/usr/bin/env python3
"""
Launch file for publisher-subscriber integration test.
Starts both Python publisher and Java subscriber.
"""

from launch import LaunchDescription
from launch_ros.actions import Node
def generate_launch_description():
    """Generate launch description for pub/sub integration test."""
    return LaunchDescription([

        # Python publisher node
        Node(
            package='jros2_interop_tests',
            executable='test_publisher',
            name='python_publisher',
            output='screen',
        ),

        # Java subscriber node
        Node(
            package='jros2_interop_tests',
            executable='java_subscriber_test',
            name='java_subscriber',
            output='screen',
        ),
    ])
