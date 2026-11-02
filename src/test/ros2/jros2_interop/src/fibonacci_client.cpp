// rclcpp_action Fibonacci client. Prints feedback sizes and the result; with cancel=1 cancels after the first feedback.
// Usage: fibonacci_client <action name> <order> <cancel 0|1>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <memory>
#include <string>
#include <thread>
#include "example_interfaces/action/fibonacci.hpp"
#include "rclcpp/rclcpp.hpp"
#include "rclcpp_action/rclcpp_action.hpp"
using Fibonacci = example_interfaces::action::Fibonacci;
using GoalHandle = rclcpp_action::ClientGoalHandle<Fibonacci>;
using namespace std::chrono_literals;

int main(int argc, char ** argv)
{
  rclcpp::init(argc, argv);
  if (argc < 4) { std::fprintf(stderr, "usage: fibonacci_client <action> <order> <cancel>\n"); return 2; }
  bool cancel = std::atoi(argv[3]) != 0;
  auto node = rclcpp::Node::make_shared("rclcpp_fibonacci_client");
  auto client = rclcpp_action::create_client<Fibonacci>(node, argv[1]);
  if (!client->wait_for_action_server(15s)) { std::fprintf(stderr, "action server not available\n"); return 1; }

  // Set by the feedback callback; the cancel request is sent from main, because in Foxy the feedback callback runs with the
  // client's goal mutex locked and calling async_cancel_goal there deadlocks
  bool feedback_received = false;
  Fibonacci::Goal goal;
  goal.order = std::atoi(argv[2]);
  auto options = rclcpp_action::Client<Fibonacci>::SendGoalOptions();
  options.feedback_callback = [&](GoalHandle::SharedPtr goal_handle, const std::shared_ptr<const Fibonacci::Feedback> feedback) {
      (void)goal_handle;
      std::printf("feedback size=%zu\n", feedback->sequence.size());
      std::fflush(stdout);
      feedback_received = true;
    };
  auto goal_handle_future = client->async_send_goal(goal, options);
  if (rclcpp::spin_until_future_complete(node, goal_handle_future, 15s) != rclcpp::FutureReturnCode::SUCCESS) {
    std::fprintf(stderr, "send goal failed\n"); return 1;
  }
  auto goal_handle = goal_handle_future.get();
  if (!goal_handle) { std::printf("rejected\n"); rclcpp::shutdown(); return 0; }
  std::printf("accepted\n");
  std::fflush(stdout);

  if (cancel) {
    auto deadline = std::chrono::steady_clock::now() + 15s;
    while (!feedback_received && std::chrono::steady_clock::now() < deadline) {
      rclcpp::spin_some(node);
      std::this_thread::sleep_for(10ms);
    }
    auto cancel_future = client->async_cancel_goal(goal_handle);
    if (rclcpp::spin_until_future_complete(node, cancel_future, 15s) != rclcpp::FutureReturnCode::SUCCESS) {
      std::fprintf(stderr, "cancel failed\n"); return 1;
    }
    auto response = cancel_future.get();
    std::printf("cancel return_code=%d canceling=%zu\n", response->return_code, response->goals_canceling.size());
    std::fflush(stdout);
  }

  auto result_future = client->async_get_result(goal_handle);
  if (rclcpp::spin_until_future_complete(node, result_future, 30s) != rclcpp::FutureReturnCode::SUCCESS) {
    std::fprintf(stderr, "no result\n"); return 1;
  }
  auto result = result_future.get();
  std::string sequence;
  for (auto n : result.result->sequence) { sequence += (sequence.empty() ? "" : ",") + std::to_string(n); }
  std::printf("result code=%d sequence=%s\n", static_cast<int>(result.code), sequence.c_str());
  rclcpp::shutdown();
  return 0;
}
