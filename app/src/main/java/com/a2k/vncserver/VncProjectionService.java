package com.a2k.vncserver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class VncProjectionService extends Service {
    public static final String TAG = MainActivity.TAG;
    private static final int NOTIFICATION_ID = 12345678;
    private static final String CHANNEL_ID = "channel_01";

    public VncProjectionService() {
    }

    private void setupAndStartForegroundService()
    {
        NotificationManager notificationManager =
                (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        CharSequence name = getString(R.string.app_name);
        NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name,
                NotificationManager.IMPORTANCE_LOW);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(mChannel);
        }
        startForegroundService(new Intent(VncProjectionService.this,
                VncProjectionService.class));
        Notification.Builder builder = new Notification.Builder(getApplicationContext(),CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Projecting")
                .setAutoCancel(true);
        builder.setChannelId(CHANNEL_ID);
        Notification notification = builder.build();

        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setupAndStartForegroundService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
