package com.a2k.vncserver;

import android.annotation.SuppressLint;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

public class VncProjectionService extends Service
        implements SurfaceTexture.OnFrameAvailableListener,
        VncJni.NotificationListener {
    public static final String TAG = MainActivity.TAG;
    public static final String PROJECTION_RESULT_KEY = "projection_result";
    public static final String PROJECTION_RESULT_CODE = "projection_result_code";
    public static final String PROJECTION_RESULT_DATA = "projection_result_data";
    private static final int NOTIFICATION_ID = 12345678;
    private static final String CHANNEL_ID = "channel_01";
    private static final String MESSAGE_KEY = "text";

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

    private VncJni mVncJni = null;

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
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);
        mVncJni = new VncJni();
        mVncJni.setNotificationListener(VncProjectionService.this);
        mVncJni.init();
        Log.d(TAG, mVncJni.protoGetVersion());
        mVncJni.startServer(mDisplayWidth, mDisplayHeight,
                mPixelFormat, false);
    }

    @Override
    public void onDestroy() {
        mVncJni.stopServer();
        stopProjection();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /* FIXME: called twice */
        if (mProjectionResultData == null) {
            mProjectionResultCode = intent.getIntExtra(PROJECTION_RESULT_CODE, 0);
            mProjectionResultData = intent.getParcelableExtra(PROJECTION_RESULT_DATA);
        }
        return START_NOT_STICKY;
    }

    private void startProjection()
    {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(
                mProjectionResultCode, (Intent)mProjectionResultData);

        if (mSurface == null)
        {
            mTextureRender = new TextureRender(mVncJni,
                    mDisplayWidth, mDisplayHeight, mPixelFormat);
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

    public void onNotification(int what, String message)
    {
        Message msg = new Message();
        msg.what = what;
        Bundle data = new Bundle();
        data.putString(MESSAGE_KEY, message);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            Bundle bundle = msg.getData();
            switch (msg.what) {
                case VncJni.SERVER_STARTED:
                {
                    Log.i(TAG, bundle.getString(MESSAGE_KEY) + "\n");
                    break;
                }
                case VncJni.SERVER_STOPPED:
                {
                    /* as we might stop vnc server before receiving disconnect
                     * notifications, so clean up here, so projection can be started */
                    Log.i(TAG, bundle.getString(MESSAGE_KEY) + "\n");
                    break;
                }
                case VncJni.CLIENT_CONNECTED:
                {
                    startProjection();
                    String ip = bundle.getString(MESSAGE_KEY);
                    String text = "Client " + ip + " connected";
                    Log.i(TAG, text + "\n");
                    break;
                }
                case VncJni.CLIENT_DISCONNECTED:
                {
                    stopProjection();
                    String ip = bundle.getString(MESSAGE_KEY);
                    String text = "Client " + ip + " disconnected";
                    Log.i(TAG, text + "\n");
                    break;
                }
                default:
                {
                    Log.d(TAG, "what = " + msg.what + " text = " + bundle.getString(MESSAGE_KEY));
                    break;
                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
