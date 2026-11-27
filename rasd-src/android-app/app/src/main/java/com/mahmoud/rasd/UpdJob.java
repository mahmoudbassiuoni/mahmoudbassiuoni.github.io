package com.mahmoud.rasd;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Checks rasd/version.json in the background (about every hour) and shows a
 *  "new update" notification; tapping it opens the app's update dialog. */
public class UpdJob extends JobService {
    static final int JOB_ID = 7101, NOTI_ID = 7101;
    static final String CH = "rasd_updates";

    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
        JobInfo ji = new JobInfo.Builder(JOB_ID, new ComponentName(c, UpdJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(60 * 60 * 1000L)
                .setPersisted(true)
                .build();
        try { js.schedule(ji); } catch (Exception e) { /* ignore */ }
    }

    @Override
    public boolean onStartJob(final JobParameters p) {
        new Thread(() -> {
            try { check(this); } catch (Exception e) { /* offline */ }
            jobFinished(p, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) {
        return true;
    }

    static boolean ar() {
        return "ar".equals(Locale.getDefault().getLanguage());
    }

    @SuppressWarnings("deprecation")
    static long myCode(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 28) return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) { return 0; }
    }

    static void check(Context c) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(MainActivity.UPD_URL + "?t=" + System.currentTimeMillis()).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(10000);
        h.setUseCaches(false);
        if (h.getResponseCode() != 200) return;
        JSONObject j = new JSONObject(MainActivity.readAll(h.getInputStream()));
        long code = j.optLong("code", 0);
        if (code <= myCode(c)) return;
        if (!MainActivity.apkReady(j.optString("apk", ""), j.optLong("size", 0))) return;
        SharedPreferences sp = c.getSharedPreferences("rasd", MODE_PRIVATE);
        if (sp.getLong("notified", 0) >= code) return;
        String notes = ar() ? j.optString("notes", "") : j.optString("notes_en", j.optString("notes", ""));
        if (show(c, j.optString("name", ""), notes)) sp.edit().putLong("notified", code).apply();
    }

    @SuppressWarnings("deprecation")
    static boolean show(Context c, String name, String notes) {
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return false;
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, ar() ? "تحديثات رصد" : "Rasd updates", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
        String title = ar() ? "تحديث جديد لرصد " + name : "Rasd " + name + " is out";
        String text = (ar() ? "دوس هنا وحدّث من جوه التطبيق." : "Tap to update inside the app.") + (notes.isEmpty() ? "" : "\n" + notes);
        Intent it = new Intent(c, MainActivity.class).putExtra("upd", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1, it, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        try {
            b.setLargeIcon(android.graphics.BitmapFactory.decodeResource(c.getResources(), R.drawable.noti_large));
        } catch (Exception e) { /* ignore */ }
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(0xFF2F6BFF)
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
        nm.notify(NOTI_ID, b.build());
        return true;
    }
}
