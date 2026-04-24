#!/usr/bin/env python3
"""
ROS2 Python test action client for integration testing with jros2.
Sends Fibonacci action goals.
"""

import rclpy
from rclpy.action import ActionClient
from rclpy.node import Node
from example_interfaces.action import Fibonacci


class TestActionClient(Node):
    def __init__(self):
        super().__init__('python_test_action_client')

        # Create action client
        self._action_client = ActionClient(self, Fibonacci, '/test/fibonacci')

        self.get_logger().info('Python test action client started')

    def send_goal(self, order):
        goal_msg = Fibonacci.Goal()
        goal_msg.order = order

        self.get_logger().info(f'Waiting for action server...')
        self._action_client.wait_for_server()

        self.get_logger().info(f'Sending goal with order {order}')
        send_goal_future = self._action_client.send_goal_async(
            goal_msg, feedback_callback=self.feedback_callback)

        rclpy.spin_until_future_complete(self, send_goal_future)
        goal_handle = send_goal_future.result()

        if not goal_handle.accepted:
            self.get_logger().error('Goal rejected')
            return None

        self.get_logger().info('Goal accepted')

        get_result_future = goal_handle.get_result_async()
        rclpy.spin_until_future_complete(self, get_result_future)

        result = get_result_future.result().result
        self.get_logger().info(f'Result: {result.sequence}')
        return result.sequence

    def feedback_callback(self, feedback_msg):
        feedback = feedback_msg.feedback
        self.get_logger().info(f'Feedback: {feedback.sequence}')


def main(args=None):
    rclpy.init(args=args)
    node = TestActionClient()

    # Send a goal
    result = node.send_goal(10)

    if result and len(result) == 11:
        node.get_logger().info('SUCCESS: Action completed correctly')
    else:
        node.get_logger().error('FAILURE: Action did not complete correctly')

    node.destroy_node()
    rclpy.shutdown()


if __name__ == '__main__':
    main()
