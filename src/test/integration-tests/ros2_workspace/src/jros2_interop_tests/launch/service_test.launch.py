#!/usr/bin/env python3
"""
Launch file for service integration test.
Starts Python service server and Java client.
"""

from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, TimerAction
from launch_ros.actions import Node
def generate_launch_description():
    """Generate launch description for service integration test."""
    return LaunchDescription([

        # Python service server node
        Node(
            package='jros2_interop_tests',
            executable='test_service_server',
            name='python_service_server',
            output='screen',
        ),

        # Java service client - delay to let server start
        TimerAction(
            period=2.0,
            actions=[
                Node(
                    package='jros2_interop_tests',
                    executable='java_service_client_test',
                    name='java_service_client',
                    output='screen',
                ),
            ]
        ),
    ])
