#!/usr/bin/env python3
"""
ROS2 Python test action server for integration testing with jros2.
Provides Fibonacci action.
"""

import rclpy
from rclpy.action import ActionServer
from rclpy.node import Node
from example_interfaces.action import Fibonacci


class TestActionServer(Node):
    def __init__(self):
        super().__init__('python_test_action_server')

        # Create action server
        self._action_server = ActionServer(
            self,
            Fibonacci,
            '/test/fibonacci',
            self.execute_callback)

        self.get_logger().info('Python test action server started on /test/fibonacci')

    def execute_callback(self, goal_handle):
        self.get_logger().info(f'Executing Fibonacci goal for order {goal_handle.request.order}')

        # Compute Fibonacci sequence
        feedback_msg = Fibonacci.Feedback()
        feedback_msg.sequence = [0, 1]

        for i in range(1, goal_handle.request.order):
            feedback_msg.sequence.append(
                feedback_msg.sequence[i] + feedback_msg.sequence[i - 1])

            # Publish feedback
            goal_handle.publish_feedback(feedback_msg)
            self.get_logger().info(f'Feedback: {feedback_msg.sequence}')

        # Mark goal as succeeded
        goal_handle.succeed()

        # Return result
        result = Fibonacci.Result()
        result.sequence = feedback_msg.sequence
        self.get_logger().info(f'Result: {result.sequence}')

        return result


def main(args=None):
    rclpy.init(args=args)
    node = TestActionServer()

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
