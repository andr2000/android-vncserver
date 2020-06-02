package com.a2k.vncserver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.os.Parcelable;

public class VncProjectionService extends Service {
    public static final String TAG = MainActivity.TAG;
    public static final String PROJECTION_RESULT_KEY = "projection_result";
    public static final String PROJECTION_RESULT_CODE = "projection_result_code";
    public static final String PROJECTION_RESULT_DATA = "projection_result_data";
    private static final int NOTIFICATION_ID = 12345678;
    private static final String CHANNEL_ID = "channel_01";

    private int mProjectionResultCode;
    private Parcelable mProjectionResultData;

    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;

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
        mMediaProjectionManager = (MediaProjectionManager)getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mProjectionResultCode = intent.getIntExtra(PROJECTION_RESULT_CODE,0);
        mProjectionResultData = intent.getParcelableExtra(PROJECTION_RESULT_DATA);
        mMediaProjection = mMediaProjectionManager.getMediaProjection(
                mProjectionResultCode, (Intent)mProjectionResultData);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
