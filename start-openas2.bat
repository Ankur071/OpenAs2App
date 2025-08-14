@echo off
echo Starting OpenAS2 Server...
echo.

cd /d "%~dp0"

REM Check if compiled classes exist
if not exist "Server\target\classes" (
    echo Error: Project not compiled! Please run compile.bat first.
    pause
    exit /b 1
)

REM Set Java options
set JAVA_OPTS=-Xmx512m -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.SimpleLog

REM Start the server
echo OpenAS2 Server is starting...
java %JAVA_OPTS% -cp "Server\target\classes;Server\target\lib\*" org.openas2.app.OpenAS2Server "Server\src\config\config.xml"

pause
