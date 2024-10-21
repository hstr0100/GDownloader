#!/bin/bash

cd "$(dirname "$0")"

echo "Running: ./gradlew clean build jpackage"
./gradlew clean build jpackage

if [ $? -ne 0 ]; then
    echo "Gradle command failed. Exiting..."
    exit $?
fi

echo "Build and packaging completed successfully."
 
