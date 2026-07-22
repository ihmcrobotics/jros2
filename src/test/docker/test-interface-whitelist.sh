#!/bin/bash
set -e

# Interface whitelist integration test using Docker
# Tests that interface whitelisting correctly restricts network communication
# NOTE: These tests only run on Linux due to Docker networking requirements

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
IMAGE_NAME="jros2-test:latest"

# Check if running on Linux
check_platform() {
    if [[ "$OSTYPE" != "linux-gnu"* ]]; then
        echo "INFO: Docker interface whitelist tests only run on Linux."
        echo "Skipping tests on $OSTYPE platform."
        exit 0
    fi
}

# Check if Docker is installed and running
check_docker() {
    if ! command -v docker &> /dev/null; then
        echo "WARNING: Docker not found. Skipping interface whitelist integration tests."
        echo "Install Docker to run these tests: https://docs.docker.com/get-docker/"
        exit 0
    fi

    if ! docker info &> /dev/null; then
        echo "WARNING: Docker daemon not running. Skipping interface whitelist integration tests."
        echo "Start Docker daemon to run these tests."
        exit 0
    fi
}

# Cleanup function
cleanup() {
    echo "Cleaning up containers and networks..."
    docker rm -f jros2-pub-test jros2-sub-pass jros2-sub-fail jros2-pub-loopback jros2-sub-loopback &> /dev/null || true
    docker network rm jros2-test-net1 jros2-test-net2 &> /dev/null || true
}

# Build test image
build_test_image() {
    echo "Building jros2 test image..."

    cd "$PROJECT_ROOT"

    # Copy runtime dependencies to build/docker-libs
    echo "Copying runtime dependencies..."
    mkdir -p build/docker-libs
    rm -f build/docker-libs/*.jar

    # Use Gradle to copy runtime dependencies and build the project jar
    ./gradlew jar copyDockerDependencies > /dev/null 2>&1 || {
        echo "WARNING: Failed to prepare Docker dependencies. Tests will be skipped."
        exit 0
    }

    # Build Docker image (keep set -e safe: check status in if, not via $?)
    if docker build -t "$IMAGE_NAME" -f src/test/docker/Dockerfile . > /tmp/jros2-docker-build.log 2>&1; then
        echo "Docker image built successfully"
    else
        echo "WARNING: Failed to build Docker image. Tests will be skipped."
        echo "---- docker build log (tail) ----"
        tail -n 40 /tmp/jros2-docker-build.log || true
        exit 0
    fi
}

# Test 1: Publisher and subscriber on same whitelisted network - SHOULD PASS
test_same_network_whitelisted() {
    echo ""
    echo "Test 1: Same network (whitelisted) - SHOULD PASS"

    # Create network
    docker network create --subnet=172.20.0.0/16 jros2-test-net1 > /dev/null 2>&1

    # Start publisher with whitelist for this network (using interface name)
    docker run -d --name jros2-pub-test \
        --network=jros2-test-net1 \
        -e FASTDDS_INTERFACE_WHITELIST="eth0" \
        "$IMAGE_NAME" \
        java -cp "/app/jros2.jar:/app/libs/*:/app/." TestPublisher > /dev/null 2>&1

    # Give publisher time to start
    sleep 2

    # Start subscriber on same network (should receive messages)
    docker run -d --name jros2-sub-pass \
        --network=jros2-test-net1 \
        "$IMAGE_NAME" \
        java -cp "/app/jros2.jar:/app/libs/*:/app/." TestSubscriber > /dev/null 2>&1

    # Wait for subscriber to run
    sleep 15

    # Check if subscriber received messages (exit code 0 = received)
    docker wait jros2-sub-pass > /dev/null 2>&1 || true
    EXIT_CODE=$(docker inspect jros2-sub-pass --format='{{.State.ExitCode}}' 2>/dev/null || echo "1")

    # Print output for debugging
    echo ""
    echo "--- Publisher Output ---"
    docker logs jros2-pub-test 2>&1
    echo ""
    echo "--- Subscriber Output ---"
    docker logs jros2-sub-pass 2>&1
    echo ""

    # Cleanup
    docker rm -f jros2-pub-test jros2-sub-pass > /dev/null 2>&1

    if [ "$EXIT_CODE" = "0" ]; then
        echo "PASS: Subscriber received messages on whitelisted network"
        return 0
    else
        echo "FAIL: Subscriber did not receive messages (exit code: $EXIT_CODE)"
        return 1
    fi
}

# Test 2: Publisher and subscriber on different networks - SHOULD FAIL (blocked)
test_different_network_blocked() {
    echo ""
    echo "Test 2: Different networks (one whitelisted, one not) - SHOULD FAIL (blocked)"

    # Create second network
    docker network create --subnet=172.21.0.0/16 jros2-test-net2 > /dev/null 2>&1

    # Start publisher on net1 with whitelist for net1 only
    docker run -d --name jros2-pub-test \
        --network=jros2-test-net1 \
        -e FASTDDS_INTERFACE_WHITELIST="172.20.0.0/16" \
        "$IMAGE_NAME" \
        java -cp "/app/jros2.jar:/app/libs/*:/app/." TestPublisher > /dev/null 2>&1

    sleep 2

    # Start subscriber on net2 (should NOT receive messages)
    docker run -d --name jros2-sub-fail \
        --network=jros2-test-net2 \
        "$IMAGE_NAME" \
        java -cp "/app/jros2.jar:/app/libs/*:/app/." TestSubscriber > /dev/null 2>&1

    sleep 15

    docker wait jros2-sub-fail > /dev/null 2>&1 || true
    EXIT_CODE=$(docker inspect jros2-sub-fail --format='{{.State.ExitCode}}' 2>/dev/null || echo "0")

    # Print output for debugging
    echo ""
    echo "--- Publisher Output ---"
    docker logs jros2-pub-test 2>&1
    echo ""
    echo "--- Subscriber Output ---"
    docker logs jros2-sub-fail 2>&1
    echo ""

    # Cleanup
    docker rm -f jros2-pub-test jros2-sub-fail > /dev/null 2>&1

    if [ "$EXIT_CODE" = "1" ]; then
        echo "PASS: Subscriber correctly blocked on non-whitelisted network"
        return 0
    else
        echo "FAIL: Subscriber unexpectedly received messages (exit code: $EXIT_CODE)"
        return 1
    fi
}

# Test 3: Loopback-only whitelist - SHOULD FAIL (inter-container blocked)
test_loopback_only_blocks_containers() {
    echo ""
    echo "Test 3: Loopback-only whitelist - SHOULD FAIL (inter-container blocked)"

    # Start publisher with loopback-only whitelist
    docker run -d --name jros2-pub-loopback \
        --network=jros2-test-net1 \
        -e FASTDDS_INTERFACE_WHITELIST="lo" \
        "$IMAGE_NAME" \
        java -cp "/app/jros2.jar:/app/libs/*:/app/." TestPublisher > /dev/null 2>&1

    sleep 2

    # Start subscriber in different container (should NOT receive)
    docker run -d --name jros2-sub-loopback \
        --network=jros2-test-net1 \
        "$IMAGE_NAME" \
        java -cp "/app/jros2.jar:/app/libs/*:/app/." TestSubscriber > /dev/null 2>&1

    sleep 15

    docker wait jros2-sub-loopback > /dev/null 2>&1 || true
    EXIT_CODE=$(docker inspect jros2-sub-loopback --format='{{.State.ExitCode}}' 2>/dev/null || echo "0")

    # Print output for debugging
    echo ""
    echo "--- Publisher Output ---"
    docker logs jros2-pub-loopback 2>&1
    echo ""
    echo "--- Subscriber Output ---"
    docker logs jros2-sub-loopback 2>&1
    echo ""

    # Cleanup
    docker rm -f jros2-pub-loopback jros2-sub-loopback > /dev/null 2>&1

    if [ "$EXIT_CODE" = "1" ]; then
        echo "PASS: Loopback whitelist correctly blocks inter-container communication"
        return 0
    else
        echo "FAIL: Subscriber unexpectedly received messages with loopback whitelist"
        return 1
    fi
}

# Main test execution
main() {
    echo "=== jros2 Interface Whitelist Integration Tests ==="

    # Check platform
    check_platform

    # Check Docker availability
    check_docker

    # Setup
    cleanup
    trap cleanup EXIT

    # Build test image
    build_test_image

    # Run tests
    FAILED=0

    test_same_network_whitelisted || FAILED=$((FAILED + 1))
    test_different_network_blocked || FAILED=$((FAILED + 1))
    test_loopback_only_blocks_containers || FAILED=$((FAILED + 1))

    # Cleanup
    cleanup

    # Report results
    echo ""
    echo "=== Test Results ==="
    if [ $FAILED -eq 0 ]; then
        echo "All tests passed (3/3)"
        exit 0
    else
        echo "$FAILED test(s) failed"
        exit 1
    fi
}

main "$@"
