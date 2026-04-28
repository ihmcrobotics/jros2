# Docker Integration Tests

This directory contains Docker-based integration tests for jros2 interface whitelisting functionality.

## Overview

The interface whitelist feature restricts ROS 2 communication to specific network interfaces or IP address ranges. These integration tests verify that the whitelist correctly allows or blocks traffic based on network configuration.

## Running Tests

### Via Gradle (Recommended)

```bash
# Run Docker integration tests
./gradlew testInterfaceWhitelistDocker

# Run all tests including Docker tests
./gradlew test
```

### Directly

```bash
# Run test script directly
./src/test/docker/test-interface-whitelist.sh
```

## Test Cases

### Test 1: Same Network (Whitelisted) - SHOULD PASS
- Publisher and subscriber on same whitelisted network
- Verifies that communication works on allowed networks

### Test 2: Different Networks - SHOULD FAIL (blocked)
- Publisher on whitelisted network, subscriber on non-whitelisted network
- Verifies that communication is blocked on non-whitelisted networks

### Test 3: Loopback-Only Whitelist - SHOULD FAIL (inter-container blocked)
- Whitelist restricted to loopback interface only
- Publisher can only communicate within its own container
- Subscriber in different container should NOT receive messages

### Test 4: Interface Name Whitelist (eth0) - SHOULD PASS
- Whitelist set to interface name "eth0"
- Should bind to all IP addresses on that interface

### Test 5: CIDR Range Whitelist - SHOULD PASS (in range)
- Whitelist set to "172.20.0.0/16"
- Container with IP in range should communicate

### Test 6: CIDR Range Whitelist - SHOULD FAIL (out of range)
- Whitelist set to "172.20.0.0/16"
- Container with IP 172.21.x.x should NOT communicate

## Requirements

- **Docker**: Tests automatically skip if Docker is not installed or not running
- **Bash**: Unix shell for running test scripts

## Docker Network Topology

```
jros2-test-net1
172.20.0.0/16 (Whitelisted)
  - Publisher
  - Subscriber 1  (SHOULD receive messages)

jros2-test-net2
172.21.0.0/16 (NOT Whitelisted)
  - Subscriber 2  (should NOT receive messages)
```

## Test Files

- `test-interface-whitelist.sh` - Main test script
- `TestPublisher.java` - Simple ROS 2 publisher for testing
- `TestSubscriber.java` - Simple ROS 2 subscriber for testing
- `Dockerfile.test` - Docker image definition (generated during tests)

## Exit Codes

- `0` - All tests passed (or Docker not available)
- `1` - One or more tests failed

## Future Enhancements

Current tests are placeholders that verify Docker network setup. Full integration would require:

1. Building jros2 JAR with dependencies
2. Creating Docker image with jros2
3. Running actual publisher/subscriber with interface whitelist
4. Verifying message delivery/blocking using container exit codes
5. Testing with real Fast-DDS discovery and data exchange

## Troubleshooting

### Docker not found
Install Docker: https://docs.docker.com/get-docker/

### Docker daemon not running
```bash
# Linux
sudo systemctl start docker

# macOS/Windows
# Start Docker Desktop
```

### Network already exists
```bash
# Clean up orphaned networks
docker network rm jros2-test-net1 jros2-test-net2
```
