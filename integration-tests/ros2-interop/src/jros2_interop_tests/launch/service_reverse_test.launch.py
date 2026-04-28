#!/usr/bin/env python3
"""
Launch file for reverse service integration test.
Starts Java service server and Python client.
"""

from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, TimerAction
from launch_ros.actions import Node
def generate_launch_description():
    """Generate launch description for reverse service integration test."""
    return LaunchDescription([

        # Java service server node
        Node(
            package='jros2_interop_tests',
            executable='java_service_server_test',
            name='java_service_server',
            output='screen',
        ),

        # Python service client - delay to let server start
        TimerAction(
            period=2.0,
            actions=[
                Node(
                    package='jros2_interop_tests',
                    executable='test_service_client',
                    name='python_service_client',
                    output='screen',
                ),
            ]
        ),
    ])
