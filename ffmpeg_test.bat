@echo off

cd /d "%~dp0"

echo Running: .\gradlew run --args="--debug --run-ffmpeg-selftest"
.\gradlew run --args="--debug --run-ffmpeg-selftest"

IF %ERRORLEVEL% NEQ 0 (
    echo Gradle command failed. Exiting...
    pause
    exit /b %ERRORLEVEL%
)

echo Test completed.
pause
