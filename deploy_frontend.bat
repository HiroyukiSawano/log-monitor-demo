@echo off
echo [1/3] Building frontend...
cd frontend
call npm run build
if %errorlevel% neq 0 (
    echo Frontend build failed!
    pause
    exit /b %errorlevel%
)

echo [2/3] Preparing static/monitor directory...
cd ..
if not exist src\main\resources\static\monitor (
    mkdir src\main\resources\static\monitor
)

echo [3/3] Copying new dist files to static/monitor...
xcopy /e /y frontend\dist\* src\main\resources\static\monitor\

echo.
echo Deployment Prep Complete! 
echo The new UI is now at: http://localhost:8080/monitor/index.html
echo Your original HTML files in /static/ remain untouched.
pause
