// Serves /<name> (default /add_two_ints) and prints each request, flushing so the caller can read it
#include <cstdio>
#include <memory>
#include "example_interfaces/srv/add_two_ints.hpp"
#include "rclcpp/rclcpp.hpp"
using example_interfaces::srv::AddTwoInts;
int main(int argc, char ** argv)
{
  rclcpp::init(argc, argv);
  std::string name = argc > 1 ? argv[1] : "/add_two_ints";
  auto node = rclcpp::Node::make_shared("rclcpp_add_two_ints_server");
  auto service = node->create_service<AddTwoInts>(name,
    [](const std::shared_ptr<AddTwoInts::Request> req, std::shared_ptr<AddTwoInts::Response> res) {
      res->sum = req->a + req->b;
      std::printf("request a=%ld b=%ld\n", (long)req->a, (long)req->b);
      std::fflush(stdout);
    });
  std::printf("ready\n");
  std::fflush(stdout);
  rclcpp::spin(node);
  rclcpp::shutdown();
  return 0;
}
