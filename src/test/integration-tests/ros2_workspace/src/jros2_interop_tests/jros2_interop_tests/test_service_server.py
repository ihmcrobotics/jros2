#!/usr/bin/env python3
"""
ROS2 Python test service server for integration testing with jros2.
Provides AddTwoInts service.
"""

import rclpy
from rclpy.node import Node
from example_interfaces.srv import AddTwoInts


class TestServiceServer(Node):
    def __init__(self):
        super().__init__('python_test_service_server')

        # Create service
        self.srv = self.create_service(
            AddTwoInts, '/test/add_two_ints', self.add_two_ints_callback)

        self.request_count = 0
        self.get_logger().info('Python test service server started on /test/add_two_ints')

    def add_two_ints_callback(self, request, response):
        self.request_count += 1
        response.sum = request.a + request.b
        self.get_logger().info(
            f'Request #{self.request_count}: {request.a} + {request.b} = {response.sum}')
        return response


def main(args=None):
    rclpy.init(args=args)
    node = TestServiceServer()

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
