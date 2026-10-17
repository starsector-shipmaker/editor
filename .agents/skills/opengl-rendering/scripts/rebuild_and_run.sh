#!/bin/bash
# Google Antigravity Skill Helper Script
# Automates clean recompiling, packaging, copying the Shade JAR, and running the Ship Editor.

echo "========================================="
echo "Starsector Ship Editor - Build & Run Script"
echo "========================================="

# 1. Clean compile and package
echo "[1/3] Compiling and packaging jar (skipping tests)..."
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo "✔ Build successful!"
else
    echo "❌ Build failed!"
    exit 1
fi

# 2. Verify the shaded JAR in the root directory
echo "[2/3] Verifying root ship_editor.jar..."
if [ -f "ship_editor.jar" ]; then
    echo "✔ Root ship_editor.jar verified successfully."
else
    echo "❌ Could not locate build jar: ship_editor.jar"
    exit 1
fi

# 3. Launching
echo "[3/3] Launching JVM with 4GB memory limit and OpenGL/X11 flags..."
java -Xmx4g -XX:+UseG1GC -XX:+UseStringDeduplication \
     -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20 \
     -Dsun.java2d.opengl=false -Dsun.java2d.d3d=false \
     -Dsun.java2d.noddraw=true -Dsun.awt.noerasebackground=true \
     -jar ./ship_editor.jar

echo "Application terminated."
