#!/usr/bin/env python3
"""
ROS2 Python test service client for integration testing with jros2.
Calls AddTwoInts service.
"""

import rclpy
from rclpy.node import Node
from example_interfaces.srv import AddTwoInts
import sys


class TestServiceClient(Node):
    def __init__(self):
        super().__init__('python_test_service_client')

        # Create service client
        self.cli = self.create_client(AddTwoInts, '/test/add_two_ints')

        # Wait for service to be available
        while not self.cli.wait_for_service(timeout_sec=5.0):
            self.get_logger().info('Waiting for service /test/add_two_ints...')

        self.get_logger().info('Python test service client started')

    def send_request(self, a, b):
        request = AddTwoInts.Request()
        request.a = a
        request.b = b

        future = self.cli.call_async(request)
        rclpy.spin_until_future_complete(self, future, timeout_sec=5.0)

        if future.result() is not None:
            result = future.result()
            self.get_logger().info(f'Result: {a} + {b} = {result.sum}')
            return result.sum
        else:
            self.get_logger().error('Service call failed')
            return None


def main(args=None):
    rclpy.init(args=args)
    node = TestServiceClient()

    # Test multiple service calls
    test_cases = [(5, 3), (10, 20), (100, 200)]
    success_count = 0

    for a, b in test_cases:
        result = node.send_request(a, b)
        if result == a + b:
            success_count += 1

    if success_count == len(test_cases):
        node.get_logger().info(f'SUCCESS: All {len(test_cases)} service calls passed')
    else:
        node.get_logger().error(f'FAILURE: Only {success_count}/{len(test_cases)} passed')

    node.destroy_node()
    rclpy.shutdown()


if __name__ == '__main__':
    main()
