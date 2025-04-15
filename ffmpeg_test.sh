#!/bin/bash

cd "$(dirname "$0")"

echo "Running: ./gradlew run --args=\"--debug --run-ffmpeg-selftest\""
./gradlew run --args="--debug --run-ffmpeg-selftest"

if [ $? -ne 0 ]; then
    echo "Gradle command failed. Exiting..."
    exit $?
fi

echo "Test completed."


