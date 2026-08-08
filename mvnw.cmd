@REM Maven Wrapper script for Windows
@REM This script downloads Maven if not present and runs the build

@echo off
setlocal

set "MAVEN_PROJECTBASEDIR=%~dp0"
set "MAVEN_WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"

@REM Read distribution URL from properties
for /f "tokens=1,* delims==" %%a in ('findstr "distributionUrl" "%MAVEN_WRAPPER_PROPERTIES%"') do set "distributionUrl=%%b"

@REM Default Maven home
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists"

@REM Check if Maven is already downloaded
if not exist "%MAVEN_HOME%\apache-maven-3.9.9" (
    echo Downloading Maven...
    mkdir "%MAVEN_HOME%" 2>nul
    powershell -Command "Invoke-WebRequest -Uri '%distributionUrl%' -OutFile '%MAVEN_HOME%\maven.zip'"
    powershell -Command "Expand-Archive -Path '%MAVEN_HOME%\maven.zip' -DestinationPath '%MAVEN_HOME%' -Force"
    del "%MAVEN_HOME%\maven.zip"
)

set "MAVEN_CMD=%MAVEN_HOME%\apache-maven-3.9.9\bin\mvn.cmd"

if not exist "%MAVEN_CMD%" (
    echo Error: Maven executable not found at %MAVEN_CMD%
    exit /b 1
)

"%MAVEN_CMD%" %*

endlocal
