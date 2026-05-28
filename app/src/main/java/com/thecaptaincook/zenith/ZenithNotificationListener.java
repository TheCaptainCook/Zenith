package com.thecaptaincook.zenith;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class ZenithNotificationListener extends NotificationListenerService {

    public static final String ACTION_NOTIFICATION_RECEIVED = "com.thecaptaincook.zenith.NOTIFICATION_RECEIVED";
    private static ZenithNotificationListener instance;

    @Override
    public void onListenerConnected() {
        instance = this;
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
    }

    public static ZenithNotificationListener getInstance() {
        return instance;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        
        Intent intent = new Intent(ACTION_NOTIFICATION_RECEIVED);
        intent.putExtra("package", sbn.getPackageName());
        sendBroadcast(intent);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not used currently
    }
}
