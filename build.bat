@echo off

cd /d "%~dp0"

echo Running: .\gradlew clean build jpackage
.\gradlew clean build jpackage

IF %ERRORLEVEL% NEQ 0 (
    echo Gradle command failed. Exiting...
    exit /b %ERRORLEVEL%
)

echo Build and packaging completed successfully.
