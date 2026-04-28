#!/usr/bin/env python3
"""
ROS2 Python test publisher for integration testing with jros2.
Publishes various message types to test interoperability.
"""

import rclpy
from rclpy.node import Node
from std_msgs.msg import String, Int32, Bool


class TestPublisher(Node):
    def __init__(self):
        super().__init__('python_test_publisher')

        # Create publishers
        self.string_pub = self.create_publisher(String, '/test/string', 10)
        self.int_pub = self.create_publisher(Int32, '/test/int32', 10)
        self.bool_pub = self.create_publisher(Bool, '/test/bool', 10)

        # Create timer to publish messages
        self.timer = self.create_timer(0.5, self.timer_callback)
        self.count = 0

        self.get_logger().info('Python test publisher started')

    def timer_callback(self):
        # Publish string message
        string_msg = String()
        string_msg.data = f'Hello from Python {self.count}'
        self.string_pub.publish(string_msg)

        # Publish int message
        int_msg = Int32()
        int_msg.data = self.count
        self.int_pub.publish(int_msg)

        # Publish bool message
        bool_msg = Bool()
        bool_msg.data = (self.count % 2 == 0)
        self.bool_pub.publish(bool_msg)

        self.get_logger().info(f'Published messages: count={self.count}')
        self.count += 1

        # Stop after 10 messages
        if self.count >= 10:
            self.get_logger().info('Finished publishing 10 messages')
            rclpy.shutdown()


def main(args=None):
    rclpy.init(args=args)
    node = TestPublisher()

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
