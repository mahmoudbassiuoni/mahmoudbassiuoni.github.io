package com.mahmoud.rasd;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** Instant "new update" push from GitHub (topic rasd-updates). */
public class Push extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage m) {
        Map<String, String> d = m.getData();
        String name = d.containsKey("name") ? d.get("name") : "";
        String notes = UpdJob.ar() ? d.get("notes") : d.get("notes_en");
        if (notes == null) notes = "";
        if ("1".equals(d.get("test"))) {
            UpdJob.show(this, UpdJob.ar() ? "(تجربة)" : "(test)", notes);
            return;
        }
        long code = 0;
        try { code = Long.parseLong(d.get("code")); } catch (Exception e) { /* bad */ }
        if (code > UpdJob.myCode(this)) {
            if (UpdJob.show(this, name, notes))
                getSharedPreferences("rasd", MODE_PRIVATE).edit().putLong("notified", code).apply();
        }
    }

    @Override
    public void onNewToken(String token) {
        MainActivity.subscribe();
    }
}
