@echo off
setlocal enabledelayedexpansion

:: abdm.bat — CLI wrapper bundled with AB Download Manager desktop install
:: Find JVM from desktop app's bundled runtime, then launch CLI.

set "SCRIPT_DIR=%~dp0"

:: Step up from: app/resources/cli/abdm.bat -> app/ -> install root
set "APP_DIR=%SCRIPT_DIR%..\..\..\"
pushd "%APP_DIR%" 2>nul || goto :no_install
set "INSTALL_DIR=%CD%"
popd

:: Find bundled JVM (desktop ships JRE in runtime/)
if exist "%INSTALL_DIR%\runtime\bin\java.exe" (
    set "JAVA_EXE=%INSTALL_DIR%\runtime\bin\java.exe"
    goto :run
)

:: Fallback: JAVA_HOME
if defined JAVA_HOME (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    if exist "!JAVA_EXE!" goto :run
)

:: Fallback: PATH
where /q java.exe 2>nul
if %ERRORLEVEL% equ 0 (
    set "JAVA_EXE=java.exe"
    goto :run
)

echo ERROR: No Java runtime found.
echo The bundled JVM was not found at: %INSTALL_DIR%\runtime\bin\java.exe
echo Please reinstall AB Download Manager or install JDK 17+ and set JAVA_HOME.
pause
exit /b 1

:no_install
echo ERROR: AB Download Manager installation not found.
echo This script must be run from within the AB Download Manager install directory.
echo Expected structure: install_dir\runtime\bin\java.exe
pause
exit /b 1

:run
:: Build classpath from all JARs in the cli/lib directory
set "CLI_LIB=%SCRIPT_DIR%lib"
set "CLASSPATH="
for %%j in ("%CLI_LIB%\*.jar") do (
    if defined CLASSPATH (
        set "CLASSPATH=!CLASSPATH!;%%~fj"
    ) else (
        set "CLASSPATH=%%~fj"
    )
)

if not defined CLASSPATH (
    echo ERROR: CLI distribution not found at %CLI_LIB%
    pause
    exit /b 1
)

"%JAVA_EXE%" -cp "%CLASSPATH%" com.abdownloadmanager.cli.CliMainKt %*

endlocal