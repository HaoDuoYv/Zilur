@echo off
setlocal
set GRADLE_VERSION=8.9
set GRADLE_HOME=%CD%\.gradle-local\gradle-%GRADLE_VERSION%
set GRADLE_ZIP=%CD%\.gradle-local\gradle-%GRADLE_VERSION%-bin.zip

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%CD%\.gradle-local" mkdir "%CD%\.gradle-local"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%GRADLE_ZIP%' -DestinationPath '%CD%\.gradle-local' -Force"
)

call "%GRADLE_HOME%\bin\gradle.bat" %*
