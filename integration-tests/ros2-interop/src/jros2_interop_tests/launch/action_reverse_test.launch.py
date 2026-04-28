#!/usr/bin/env python3
"""
Launch file for reverse action integration test.
Starts Java action server and Python client.
"""

from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, TimerAction
from launch_ros.actions import Node
def generate_launch_description():
    """Generate launch description for reverse action integration test."""
    return LaunchDescription([

        # Java action server node
        Node(
            package='jros2_interop_tests',
            executable='java_action_server_test',
            name='java_action_server',
            output='screen',
        ),

        # Python action client - delay to let server start
        TimerAction(
            period=2.0,
            actions=[
                Node(
                    package='jros2_interop_tests',
                    executable='test_action_client',
                    name='python_action_client',
                    output='screen',
                ),
            ]
        ),
    ])
