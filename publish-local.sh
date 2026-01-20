#!/usr/bin/env bash

set -e
set -o pipefail

echo "🚀 Publishing library to Maven Local..."

# Ensure gradlew is executable
if [ ! -x "./gradlew" ]; then
  echo "🔧 Making gradlew executable"
  chmod +x ./gradlew
fi

./gradlew clean build publishToMavenLocal

echo "✅ Successfully published to Maven Local (~/.m2)"
