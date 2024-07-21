@echo off
setlocal

rem Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo Java is not installed. Please install Java 21 or later to run this program.
    pause
    exit /b 1
)

rem Get and navigate to the directory where this batch file resides
set "batchDir=%~dp0"

cd /d "%batchDir%"

rem Run the JAR file using its relative path to this batch file
rem You might see a console window open up every time this program is started
start "GDownloader" javaw -Xmx512M -jar build\libs\GDownloader-*.jar

endlocal
