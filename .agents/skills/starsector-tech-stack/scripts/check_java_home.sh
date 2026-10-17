#!/bin/bash
# Helper script to check java compiler version compatibility.
if [ -z "$JAVA_HOME" ]; then
  echo "WARNING: JAVA_HOME is not set in your environment. Relying on system default."
else
  echo "JAVA_HOME is set to: $JAVA_HOME"
fi

JAVA_VER=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "Running Java major version: $JAVA_VER"

if [ "$JAVA_VER" -lt 17 ] || [ "$JAVA_VER" -gt 24 ]; then
  echo "WARNING: starsector-shipmaker requires JDK 17-24 to compile successfully with Lombok 1.18.46."
else
  echo "Java version check: OK"
fi
