@echo off
setlocal
set APP_HOME=%~dp0
set GRADLE_VERSION=8.14.3
set INSTALL_DIR=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%\albarq-bootstrap
set GRADLE_BIN=%INSTALL_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat
if not exist "%GRADLE_BIN%" (
  if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip'; Invoke-WebRequest $u -OutFile '%INSTALL_DIR%\gradle.zip'; Expand-Archive -Force '%INSTALL_DIR%\gradle.zip' '%INSTALL_DIR%'; Remove-Item '%INSTALL_DIR%\gradle.zip'"
)
call "%GRADLE_BIN%" %*
