رصد 1.0.0 — تطبيق أندرويد (Android Studio)
==========================================
البرنامج كله جوه التطبيق (assets/www) ومش محتاج نت عشان يشتغل.
نفس مفتاح التوقيع بتاع دفتري (D:\daftari\keys\android.keystore).

1) فك الضغط في D:\rasd  (يبقى عندك D:\rasd\android-app\build-apk.bat)
2) دبل كليك على build-apk.bat واكتب باسورد الـ keystore بتاع دفتري
   → هيطلع Rasd-1.0.0.apk في نفس الفولدر
3) عشان Google Drive يشتغل في التطبيق: Google Cloud (مشروع Daftari) → Clients → + Create client
   → Application type: Android
     Package name: com.mahmoud.rasd
     SHA-1: نفس الـ SHA-1 اللي في الـ Android client بتاع دفتري
   → Create

ملفات المشروع:
  app/src/main/java/com/mahmoud/rasd/MainActivity.java   كود الأندرويد
  app/src/main/assets/www/index.html                    التطبيق نفسه (نفس اللي على GitHub)

التحديثات (زي دفتري):
  التطبيق بيشيك على https://mahmoudbassiuoni.github.io/rasd/version.json
  ولو فيه رقم أكبر من اللي عندك بيطلع «تحديث جديد» وينزّل rasd.apk ويثبّته.
  كل مرة: Claude بيعدّل الملفات → انت تشغّل build-apk.bat → Claude يرفع الـ APK ويحدّث version.json.
