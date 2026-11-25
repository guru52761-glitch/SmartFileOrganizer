@echo off
title Smart File Organizer
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
  echo Java JDK was not found. Install JDK 8 or newer and add it to PATH.
  pause
  exit /b 1
)
javac SmartFileOrganizer.java
if errorlevel 1 (
  echo Compilation failed. Check the error messages above.
  pause
  exit /b 1
)
java SmartFileOrganizer
pause
