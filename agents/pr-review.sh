#!/bin/bash

# PR Review Agent Wrapper Script
# Makes it easy to run the PR review agent

cd "$(dirname "$0")"

# Build if needed
if [ ! -f "build/install/agents/bin/agents" ]; then
    echo "Building PR Review Agent..."
    ./gradlew installDist
fi

# Run the agent
exec build/install/agents/bin/agents "$@"

