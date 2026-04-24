#!/usr/bin/env python3
"""
Launch file for action integration test.
Starts Python action server and Java client.
"""

from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, TimerAction
from launch_ros.actions import Node
def generate_launch_description():
    """Generate launch description for action integration test."""
    return LaunchDescription([

        # Python action server node
        Node(
            package='jros2_interop_tests',
            executable='test_action_server',
            name='python_action_server',
            output='screen',
        ),

        # Java action client - delay to let server start
        TimerAction(
            period=2.0,
            actions=[
                Node(
                    package='jros2_interop_tests',
                    executable='java_action_client_test',
                    name='java_action_client',
                    output='screen',
                ),
            ]
        ),
    ])
