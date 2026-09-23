#!/usr/bin/env sh
set -eu
rm -rf build/classes
mkdir -p build/classes
javac -d build/classes src/main/java/*.java
exec java -cp build/classes AvatarPipelineServer
