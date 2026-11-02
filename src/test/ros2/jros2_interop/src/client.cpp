// Calls /<name> with a and b, prints "sum=<n>" and exits 0, or exits 1 on timeout
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include "example_interfaces/srv/add_two_ints.hpp"
#include "rclcpp/rclcpp.hpp"
using example_interfaces::srv::AddTwoInts;
using namespace std::chrono_literals;
int main(int argc, char ** argv)
{
  rclcpp::init(argc, argv);
  if (argc < 4) { std::fprintf(stderr, "usage: add_two_ints_client <service> <a> <b>\n"); return 2; }
  auto node = rclcpp::Node::make_shared("rclcpp_add_two_ints_client");
  auto client = node->create_client<AddTwoInts>(argv[1]);
  if (!client->wait_for_service(15s)) { std::fprintf(stderr, "service not available\n"); return 1; }
  auto req = std::make_shared<AddTwoInts::Request>();
  req->a = std::atol(argv[2]);
  req->b = std::atol(argv[3]);
  auto future = client->async_send_request(req);
  if (rclcpp::spin_until_future_complete(node, future, 15s) != rclcpp::FutureReturnCode::SUCCESS) {
    std::fprintf(stderr, "no response\n"); return 1;
  }
  std::printf("sum=%ld\n", (long)future.get()->sum);
  rclcpp::shutdown();
  return 0;
}
