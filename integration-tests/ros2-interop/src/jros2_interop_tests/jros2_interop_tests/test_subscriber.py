#!/usr/bin/env python3
"""
ROS2 Python test subscriber for integration testing with jros2.
Subscribes to various message types to test interoperability.
"""

import rclpy
from rclpy.node import Node
from std_msgs.msg import String, Int32, Bool
import sys


class TestSubscriber(Node):
    def __init__(self):
        super().__init__('python_test_subscriber')

        # Message counters
        self.string_count = 0
        self.int_count = 0
        self.bool_count = 0

        # Create subscribers
        self.string_sub = self.create_subscription(
            String, '/test/string', self.string_callback, 10)
        self.int_sub = self.create_subscription(
            Int32, '/test/int32', self.int_callback, 10)
        self.bool_sub = self.create_subscription(
            Bool, '/test/bool', self.bool_callback, 10)

        # Timer to check if we received enough messages
        self.check_timer = self.create_timer(1.0, self.check_completion)

        self.get_logger().info('Python test subscriber started')

    def string_callback(self, msg):
        self.get_logger().info(f'Received String: "{msg.data}"')
        self.string_count += 1

    def int_callback(self, msg):
        self.get_logger().info(f'Received Int32: {msg.data}')
        self.int_count += 1

    def bool_callback(self, msg):
        self.get_logger().info(f'Received Bool: {msg.data}')
        self.bool_count += 1

    def check_completion(self):
        # Exit after receiving at least 5 messages on each topic
        if self.string_count >= 5 and self.int_count >= 5 and self.bool_count >= 5:
            self.get_logger().info(
                f'SUCCESS: Received {self.string_count} strings, '
                f'{self.int_count} ints, {self.bool_count} bools')
            rclpy.shutdown()


def main(args=None):
    rclpy.init(args=args)
    node = TestSubscriber()

    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        if rclpy.ok():
            rclpy.shutdown()


if __name__ == '__main__':
    main()
