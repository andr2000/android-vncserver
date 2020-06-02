package com.a2k.vncserver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.opengl.GLES20;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

public class VncProjectionService extends Service
        implements SurfaceTexture.OnFrameAvailableListener {
    public static final String TAG = MainActivity.TAG;
    public static final String PROJECTION_RESULT_KEY = "projection_result";
    public static final String PROJECTION_RESULT_CODE = "projection_result_code";
    public static final String PROJECTION_RESULT_DATA = "projection_result_data";
    private static final int NOTIFICATION_ID = 12345678;
    private static final String CHANNEL_ID = "channel_01";

    private int mDisplayWidth = 800;
    private int mDisplayHeight = 480;
    private int mPixelFormat = GLES20.GL_RGB565;

    private int mProjectionResultCode;
    private Parcelable mProjectionResultData;

    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    private Surface mSurface;
    private TextureRender mTextureRender;
    private SurfaceTexture mSurfaceTexture;

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
        stopProjection();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mProjectionResultCode = intent.getIntExtra(PROJECTION_RESULT_CODE,0);
        mProjectionResultData = intent.getParcelableExtra(PROJECTION_RESULT_DATA);
        startProjection();
        return START_NOT_STICKY;
    }

    private void startProjection()
    {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(
                mProjectionResultCode, (Intent)mProjectionResultData);

        if (mSurface == null)
        {
            mTextureRender = new TextureRender(mDisplayWidth, mDisplayHeight,
                    mPixelFormat);
            mTextureRender.start();
            mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
            mSurfaceTexture.setDefaultBufferSize(mDisplayWidth, mDisplayHeight);
            mSurfaceTexture.setOnFrameAvailableListener(this);
            mSurface = new Surface(mSurfaceTexture);
        }
        if (mVirtualDisplay == null) {
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager window = (WindowManager)
                    getSystemService(Context.WINDOW_SERVICE);
            window.getDefaultDisplay().getMetrics(metrics);
            mVirtualDisplay = mMediaProjection.createVirtualDisplay("vncserver",
                    mDisplayWidth, mDisplayHeight, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mSurface, null /*Callbacks*/, null /*Handler*/);
        }
    }

    private void stopProjection()
    {
        if (mSurfaceTexture != null) {
            mSurfaceTexture.setOnFrameAvailableListener(null);
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaProjection != null) {
            /* this causes issue filed here:
             * https://code.google.com/p/android/issues/detail?id=81152 */
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        if (mTextureRender != null) {
            mTextureRender.stop();
            mTextureRender = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (mSurfaceTexture != null) {
            mSurfaceTexture.updateTexImage();
            mTextureRender.drawFrame();
            mTextureRender.swapBuffers();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
