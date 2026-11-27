Rasd — Android source. Every push that changes rasd-src/ is built by
.github/workflows/rasd-build.yml: it signs the APK with the RASD_KEYSTORE secret,
publishes rasd/rasd.apk + rasd/version.json + the web app, and sends the update push.
Release = bump versionCode/versionName in android-app/app/build.gradle and edit notes.json.
