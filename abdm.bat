@echo off
setlocal
set "ABDM_DIR=%~dp0cli\app\build\install\abdm"
set "ABDM_BIN=%ABDM_DIR%\bin\abdm.bat"
if exist "%ABDM_BIN%" (
    set "JAVA_OPTS=-Djava.awt.headless=true"
    call "%ABDM_BIN%" %*
) else (
    echo Building AB Download Manager CLI first...
    set "JAVA_HOME=C:\Program Files\JetBrains\PyCharm 2025.2.1\jbr"
    cd /d "%~dp0"
    call gradlew.bat :cli:app:installDist
    call "%ABDM_BIN%" %*
)