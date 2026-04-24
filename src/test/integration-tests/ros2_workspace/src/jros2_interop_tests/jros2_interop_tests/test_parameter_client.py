#!/usr/bin/env python3
"""
Test Python parameter client accessing Java node parameters via parameter services.
"""

import rclpy
from rclpy.node import Node
from rcl_interfaces.srv import GetParameters, SetParameters
from rcl_interfaces.msg import Parameter, ParameterValue, ParameterType


class ParameterClientTest(Node):
    """Test parameter client interoperability with Java node."""

    def __init__(self):
        super().__init__('python_parameter_client')
        self.get_logger().info('Python parameter client started')

        # Create service clients for the Java node's parameter services
        self.get_params_client = self.create_client(
            GetParameters,
            '/java_parameter_test/get_parameters'
        )
        self.set_params_client = self.create_client(
            SetParameters,
            '/java_parameter_test/set_parameters'
        )

        # Wait for services to become available
        self.get_logger().info('Waiting for parameter services...')
        if not self.get_params_client.wait_for_service(timeout_sec=5.0):
            self.get_logger().error('Get parameters service not available')
            return

        if not self.set_params_client.wait_for_service(timeout_sec=5.0):
            self.get_logger().error('Set parameters service not available')
            return

        self.get_logger().info('Parameter services available!')
        self.run_test()

    def run_test(self):
        """Run the parameter interop test."""
        # Test 1: Get parameters from Java node
        self.get_logger().info('\n=== Test 1: Getting parameters from Java node ===')
        get_request = GetParameters.Request()
        get_request.names = ['bool_param', 'int_param', 'double_param', 'string_param']

        future = self.get_params_client.call_async(get_request)
        rclpy.spin_until_future_complete(self, future, timeout_sec=2.0)

        if future.result() is not None:
            response = future.result()
            self.get_logger().info(f'Got {len(response.values)} parameter values:')
            for i, name in enumerate(get_request.names):
                value = response.values[i]
                if value.type == ParameterType.PARAMETER_BOOL:
                    self.get_logger().info(f'  {name}: {value.bool_value} (bool)')
                elif value.type == ParameterType.PARAMETER_INTEGER:
                    self.get_logger().info(f'  {name}: {value.integer_value} (int)')
                elif value.type == ParameterType.PARAMETER_DOUBLE:
                    self.get_logger().info(f'  {name}: {value.double_value} (double)')
                elif value.type == ParameterType.PARAMETER_STRING:
                    self.get_logger().info(f'  {name}: {value.string_value} (string)')
        else:
            self.get_logger().error('Failed to get parameters')
            return

        # Test 2: Set a parameter on Java node from Python
        self.get_logger().info('\n=== Test 2: Setting parameter from Python ===')
        set_request = SetParameters.Request()

        param = Parameter()
        param.name = 'string_param'
        param.value.type = ParameterType.PARAMETER_STRING
        param.value.string_value = 'modified by python!'

        set_request.parameters = [param]

        future = self.set_params_client.call_async(set_request)
        rclpy.spin_until_future_complete(self, future, timeout_sec=2.0)

        if future.result() is not None:
            response = future.result()
            if len(response.results) > 0 and response.results[0].successful:
                self.get_logger().info('Successfully set parameter from Python!')
            else:
                reason = response.results[0].reason if len(response.results) > 0 else 'unknown'
                self.get_logger().error(f'Failed to set parameter: {reason}')
        else:
            self.get_logger().error('Set parameter request failed')

        # Test 3: Verify the parameter was set
        self.get_logger().info('\n=== Test 3: Verifying parameter was set ===')
        get_request2 = GetParameters.Request()
        get_request2.names = ['string_param']

        future = self.get_params_client.call_async(get_request2)
        rclpy.spin_until_future_complete(self, future, timeout_sec=2.0)

        if future.result() is not None:
            response = future.result()
            if len(response.values) > 0:
                final_value = response.values[0].string_value
                self.get_logger().info(f'Final value: {final_value}')
                if final_value == 'modified by python!':
                    self.get_logger().info('SUCCESS: Bidirectional parameter interop works!')
                else:
                    self.get_logger().error(f'FAILED: Expected "modified by python!", got "{final_value}"')
        else:
            self.get_logger().error('Failed to verify parameter')


def main(args=None):
    """Main entry point."""
    rclpy.init(args=args)

    node = ParameterClientTest()

    # Keep node alive briefly
    rclpy.spin_once(node, timeout_sec=1.0)

    node.destroy_node()
    rclpy.shutdown()

    print('Python parameter client test complete')


if __name__ == '__main__':
    main()
