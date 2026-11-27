@echo off
title Rasd 1.0.7 - build APK
cd /d "%~dp0"
if not exist "gradlew.bat" (
  echo Copying Gradle files from the Daftari project...
  copy /y "D:\daftari\android-app\gradlew.bat" "%~dp0" >nul
  copy /y "D:\daftari\android-app\gradlew" "%~dp0" >nul
  if not exist "gradle\wrapper" mkdir "gradle\wrapper"
  copy /y "D:\daftari\android-app\gradle\wrapper\gradle-wrapper.jar" "%~dp0gradle\wrapper\" >nul
)
if not exist "gradlew.bat" (
  echo.
  echo ==== Could not find D:\daftari\android-app\gradlew.bat ====
  pause
  exit /b 1
)
if exist "C:\jdk17\bin\java.exe" set "JAVA_HOME=C:\jdk17"
if not defined ANDROID_HOME set "ANDROID_HOME=%USERPROFILE%\.bubblewrap\android_sdk"
echo.
echo  Same key as Daftari (D:\daftari\keys\android.keystore).
echo  Type the Key Store password (nothing appears while typing) then press Enter:
set "RASD_KS_PASS="
for /f "usebackq delims=" %%p in (`powershell -NoProfile -Command "$p=Read-Host 'Key Store password' -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))"`) do set "RASD_KS_PASS=%%p"
echo.
echo  Password for the Key - just press Enter if it is the same one:
set "RASD_KEY_PASS="
for /f "usebackq delims=" %%p in (`powershell -NoProfile -Command "$p=Read-Host 'Key password' -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))"`) do set "RASD_KEY_PASS=%%p"
if not defined RASD_KEY_PASS set "RASD_KEY_PASS=%RASD_KS_PASS%"
echo.
echo ==== Building Rasd 1.0.7 ====
call gradlew.bat assembleRelease
set "RC=%errorlevel%"
set "RASD_KS_PASS="
set "RASD_KEY_PASS="
if not "%RC%"=="0" (
  echo.
  echo ==== BUILD FAILED - take a photo of the red lines above ====
  pause
  exit /b 1
)
copy /y "app\build\outputs\apk\release\app-release.apk" "%~dp0Rasd-1.0.7.apk" >nul
echo.
echo ==== DONE: %~dp0Rasd-1.0.7.apk ====
pause
