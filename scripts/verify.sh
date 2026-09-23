#!/usr/bin/env sh
set -eu
rm -rf build/test-classes
mkdir -p build/test-classes
javac -d build/test-classes src/main/java/*.java src/test/java/*.java
java -cp build/test-classes LearnerAvatarServiceTest
