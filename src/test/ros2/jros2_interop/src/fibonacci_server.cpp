// rclcpp_action Fibonacci server like the ROS 2 tutorial: rejects negative orders, accepts cancels, one feedback per step.
// Usage: fibonacci_server <action name>
#include <chrono>
#include <cstdio>
#include <memory>
#include <thread>
#include "example_interfaces/action/fibonacci.hpp"
#include "rclcpp/rclcpp.hpp"
#include "rclcpp_action/rclcpp_action.hpp"
using Fibonacci = example_interfaces::action::Fibonacci;
using GoalHandle = rclcpp_action::ServerGoalHandle<Fibonacci>;
using namespace std::chrono_literals;

static void execute(const std::shared_ptr<GoalHandle> goal_handle)
{
  auto feedback = std::make_shared<Fibonacci::Feedback>();
  auto result = std::make_shared<Fibonacci::Result>();
  feedback->sequence = {0, 1};
  for (int i = 1; i < goal_handle->get_goal()->order && rclcpp::ok(); ++i) {
    if (goal_handle->is_canceling()) {
      result->sequence = feedback->sequence;
      goal_handle->canceled(result);
      std::printf("canceled\n");
      std::fflush(stdout);
      return;
    }
    feedback->sequence.push_back(feedback->sequence[i] + feedback->sequence[i - 1]);
    goal_handle->publish_feedback(feedback);
    std::this_thread::sleep_for(50ms);
  }
  result->sequence = feedback->sequence;
  goal_handle->succeed(result);
  std::printf("succeeded\n");
  std::fflush(stdout);
}

int main(int argc, char ** argv)
{
  rclcpp::init(argc, argv);
  std::string name = argc > 1 ? argv[1] : "/fibonacci";
  auto node = rclcpp::Node::make_shared("rclcpp_fibonacci_server");
  auto server = rclcpp_action::create_server<Fibonacci>(
    node, name,
    [](const rclcpp_action::GoalUUID &, std::shared_ptr<const Fibonacci::Goal> goal) {
      std::printf("goal order=%d\n", goal->order);
      std::fflush(stdout);
      return goal->order < 0 ? rclcpp_action::GoalResponse::REJECT : rclcpp_action::GoalResponse::ACCEPT_AND_EXECUTE;
    },
    [](const std::shared_ptr<GoalHandle>) { return rclcpp_action::CancelResponse::ACCEPT; },
    [](const std::shared_ptr<GoalHandle> goal_handle) { std::thread{execute, goal_handle}.detach(); });
  std::printf("ready\n");
  std::fflush(stdout);
  rclcpp::spin(node);
  rclcpp::shutdown();
  return 0;
}
