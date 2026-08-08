#!/bin/sh
# Maven Wrapper script for Unix/macOS
# This script downloads Maven if not present and runs the build

set -e

# Determine the project base directory
if [ -z "$MAVEN_PROJECTBASEDIR" ]; then
  MAVEN_PROJECTBASEDIR=$(cd "$(dirname "$0")" && pwd)
  export MAVEN_PROJECTBASEDIR
fi

MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"

# Read distribution URL
if [ -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
  distributionUrl=$(grep "distributionUrl" "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
  wrapperUrl=$(grep "wrapperUrl" "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
fi

# Default Maven home
MAVEN_HOME="${HOME}/.m2/wrapper/dists/$(basename "$distributionUrl" .zip)"

if [ ! -d "$MAVEN_HOME" ]; then
  echo "Downloading Maven from $distributionUrl..."
  mkdir -p "$MAVEN_HOME"
  if command -v curl > /dev/null 2>&1; then
    curl -fsSL "$distributionUrl" -o "$MAVEN_HOME/maven.zip"
  elif command -v wget > /dev/null 2>&1; then
    wget -q "$distributionUrl" -O "$MAVEN_HOME/maven.zip"
  else
    echo "Error: Neither curl nor wget found. Cannot download Maven."
    exit 1
  fi
  unzip -q "$MAVEN_HOME/maven.zip" -d "$MAVEN_HOME"
  rm "$MAVEN_HOME/maven.zip"
fi

# Find the Maven executable
MVN_CMD=$(find "$MAVEN_HOME" -name "mvn" -path "*/bin/mvn" | head -1)

if [ -z "$MVN_CMD" ]; then
  echo "Error: Maven executable not found in $MAVEN_HOME"
  exit 1
fi

exec "$MVN_CMD" "$@"
