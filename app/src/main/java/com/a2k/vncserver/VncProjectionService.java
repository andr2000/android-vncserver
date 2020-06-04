package com.a2k.vncserver;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.opengl.GLES20;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

public class VncProjectionService extends Service
        implements SurfaceTexture.OnFrameAvailableListener,
        VncJni.NotificationListener {
    public static final String TAG = MainActivity.TAG;

    public static final String PROJECTION_RESULT_CODE = "projection_result_code";
    public static final String PROJECTION_RESULT_DATA = "projection_result_data";

    private static final int NOTIFICATION_ID = 12345678;
    private static final String CHANNEL_ID = "channel_01";
    private static final String MESSAGE_KEY = "text";

    private final IBinder mBinder = new VncProjectionServiceBinder();

    private int mDisplayWidth = 800;
    private int mDisplayHeight = 480;
    private int mPixelFormat = GLES20.GL_RGB565;
    /* Heigt of the black rectangles on top/bottom in landscape mode */
    private double mHeightOffsetLandscape = -1.0;
    private int mCurrentRotation;
    private int mBrightness;

    private int mProjectionResultCode;
    private Parcelable mProjectionResultData;

    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    private Surface mSurface;
    private TextureRender mTextureRender;
    private SurfaceTexture mSurfaceTexture;

    private VncJni mVncJni;

    public VncProjectionService() {
    }

    private void setupAndStartForegroundService() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        CharSequence name = getString(R.string.app_name);
        NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name,
                NotificationManager.IMPORTANCE_LOW);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(mChannel);
        }
        startForegroundService(new Intent(VncProjectionService.this,
                VncProjectionService.class));
        Notification.Builder builder = new Notification.Builder(getApplicationContext(), CHANNEL_ID)
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
        calcHeightOffsetLandscape();
    }

    @Override
    public void onDestroy() {
        stopProjection();
        mVncJni.stopServer();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    private void calcHeightOffsetLandscape() {
        /*
         * This is used to remove black edges in landscape mode via scaling
         * the image to fit the virtual display's size.
         * Calculations depend on current screen rotation, e.g. currently
         * in landscape or portrait mode, but in portrait mode we do not
         * want any scaling, but in landscape only.
         */
        WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point realSize = new Point();
        display.getRealSize(realSize);

        int x, y;
        if (realSize.x > realSize.y) {
            x = realSize.x;
            y = realSize.y;
        } else {
            x = realSize.y;
            y = realSize.x;
        }
        /* Evaluates to negative in portrait mode */
        mHeightOffsetLandscape =  ((double)y / x * mDisplayHeight) / 2.0f;
    }

    private void startProjection() {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(
                mProjectionResultCode, (Intent) mProjectionResultData);

        if (mSurface == null) {
            mTextureRender = new TextureRender(mVncJni,
                    mDisplayWidth, mDisplayHeight, mPixelFormat);
            handleRotationChange(mCurrentRotation);
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

    private void stopProjection() {
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

    public void onNotification(int what, String message) {
        Message msg = new Message();
        msg.what = what;
        Bundle data = new Bundle();
        data.putString(MESSAGE_KEY, message);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    @Override
    public void onNotificationTouch(int buttonMask, int x, int y) {
    }

    @Override
    public void onNotificationKbd(int down, int key) {
    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            switch (msg.what) {
                case VncJni.SERVER_STARTED:
                case VncJni.SERVER_STOPPED: {
                    Log.i(TAG, bundle.getString(MESSAGE_KEY) + "\n");
                    break;
                }
                case VncJni.CLIENT_CONNECTED: {
                    startProjection();
                    String ip = bundle.getString(MESSAGE_KEY);
                    String text = "Client " + ip + " connected";
                    Log.i(TAG, text + "\n");
                    mBrightness = getBrightness();
                    setBrightness(0);
                    break;
                }
                case VncJni.CLIENT_DISCONNECTED: {
                    stopProjection();
                    String ip = bundle.getString(MESSAGE_KEY);
                    String text = "Client " + ip + " disconnected";
                    Log.i(TAG, text + "\n");
                    setBrightness(mBrightness);
                    break;
                }
                default: {
                    Log.d(TAG, "what = " + msg.what + " text = " +
                            bundle.getString(MESSAGE_KEY));
                    break;
                }
            }
        }
    };

    private void handleRotationChange(int rotation) {
        mCurrentRotation = rotation;

        if (mTextureRender == null)
            return;

        if (rotation == Surface.ROTATION_0 ||
                rotation == Surface.ROTATION_180)
            mTextureRender.setHeightOffset(-1.0);
        else
            mTextureRender.setHeightOffset(mHeightOffsetLandscape);
    }

    public void onScreenRotation(int rotation) {
        handleRotationChange(rotation);
    }

    private void setBrightness(int brightness) {
        if (Settings.System.canWrite(getApplicationContext())) {
            ContentResolver cResolver = this.getApplicationContext().getContentResolver();
            Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS, brightness);
        }
    }

    private int getBrightness() {
        int brightness = 512;

        if (Settings.System.canWrite(getApplicationContext())) {
            ContentResolver cResolver = this.getApplicationContext().getContentResolver();
            try {
                brightness =  Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS);
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
        }
        return brightness;
    }

    @Override
    public IBinder onBind(Intent intent) {
        mProjectionResultCode = intent.getIntExtra(PROJECTION_RESULT_CODE, 0);
        mProjectionResultData = intent.getParcelableExtra(PROJECTION_RESULT_DATA);
        return mBinder;
    }

    public class VncProjectionServiceBinder extends Binder {
        VncProjectionService getService() {
            return VncProjectionService.this;
        }
    }
}
