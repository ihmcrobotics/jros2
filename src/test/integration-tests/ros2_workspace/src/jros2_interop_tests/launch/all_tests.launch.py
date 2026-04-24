#!/usr/bin/env python3
"""
Master launch file that runs all integration tests sequentially.
"""

from launch import LaunchDescription
from launch.actions import IncludeLaunchDescription, TimerAction
from launch.launch_description_sources import PythonLaunchDescriptionSource
from launch.substitutions import PathJoinSubstitution
from launch_ros.substitutions import FindPackageShare


def generate_launch_description():
    """Generate launch description that runs all integration tests."""
    pkg_share = FindPackageShare('jros2_interop_tests')

    return LaunchDescription([
        # Test 1: Python publisher -> Java subscriber
        IncludeLaunchDescription(
            PythonLaunchDescriptionSource([
                PathJoinSubstitution([pkg_share, 'launch', 'pubsub_test.launch.py'])
            ])
        ),

        # Test 2: Java publisher -> Python subscriber (after 5s)
        TimerAction(
            period=5.0,
            actions=[
                IncludeLaunchDescription(
                    PythonLaunchDescriptionSource([
                        PathJoinSubstitution([pkg_share, 'launch', 'pubsub_reverse_test.launch.py'])
                    ]),
                ),
            ]
        ),

        # Test 3: Python service server + Java client (after 10s)
        TimerAction(
            period=10.0,
            actions=[
                IncludeLaunchDescription(
                    PythonLaunchDescriptionSource([
                        PathJoinSubstitution([pkg_share, 'launch', 'service_test.launch.py'])
                    ]),
                ),
            ]
        ),

        # Test 4: Java service server + Python client (after 15s)
        TimerAction(
            period=15.0,
            actions=[
                IncludeLaunchDescription(
                    PythonLaunchDescriptionSource([
                        PathJoinSubstitution([pkg_share, 'launch', 'service_reverse_test.launch.py'])
                    ]),
                ),
            ]
        ),

        # Test 5: Python action server + Java client (after 20s)
        TimerAction(
            period=20.0,
            actions=[
                IncludeLaunchDescription(
                    PythonLaunchDescriptionSource([
                        PathJoinSubstitution([pkg_share, 'launch', 'action_test.launch.py'])
                    ]),
                ),
            ]
        ),

        # Test 6: Java action server + Python client (after 25s)
        TimerAction(
            period=25.0,
            actions=[
                IncludeLaunchDescription(
                    PythonLaunchDescriptionSource([
                        PathJoinSubstitution([pkg_share, 'launch', 'action_reverse_test.launch.py'])
                    ]),
                ),
            ]
        ),

        # Test 7: Java parameter test (after 30s)
        TimerAction(
            period=30.0,
            actions=[
                IncludeLaunchDescription(
                    PythonLaunchDescriptionSource([
                        PathJoinSubstitution([pkg_share, 'launch', 'parameter_test.launch.py'])
                    ]),
                ),
            ]
        ),
    ])
