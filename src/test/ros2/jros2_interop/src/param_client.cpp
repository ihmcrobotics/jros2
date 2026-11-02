// Lists, gets and sets the parameters of a remote node with rclcpp::SyncParametersClient.
// Usage: param_client <remote node> <integer parameter to set> <value>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include "rclcpp/rclcpp.hpp"
#ifdef JROS2_INTEROP_FOXY
#define TIMEOUT_ARG
#else
#define TIMEOUT_ARG , 15s
#endif
using namespace std::chrono_literals;
int main(int argc, char ** argv)
{
  rclcpp::init(argc, argv);
  if (argc < 4) { std::fprintf(stderr, "usage: param_client <node> <name> <value>\n"); return 2; }
  auto node = rclcpp::Node::make_shared("rclcpp_param_client");
  auto client = std::make_shared<rclcpp::SyncParametersClient>(node, argv[1]);
  if (!client->wait_for_service(15s)) { std::fprintf(stderr, "parameter services not available\n"); return 1; }

  auto list = client->list_parameters({}, 0 TIMEOUT_ARG);
  for (const auto & name : list.names) { std::printf("name=%s\n", name.c_str()); }

  for (const auto & parameter : client->get_parameters(list.names TIMEOUT_ARG)) {
    std::printf("get %s=%s\n", parameter.get_name().c_str(), parameter.value_to_string().c_str());
  }

  auto results = client->set_parameters({rclcpp::Parameter(argv[2], std::atol(argv[3])),
                                          rclcpp::Parameter(argv[2], std::string("wrong type"))} TIMEOUT_ARG);
  for (const auto & result : results) {
    std::printf("set successful=%d reason=%s\n", result.successful ? 1 : 0, result.reason.c_str());
  }
  std::printf("after %s=%ld\n", argv[2], (long)client->get_parameters({argv[2]} TIMEOUT_ARG)[0].as_int());
  rclcpp::shutdown();
  return 0;
}
