package com.a2k.vncserver;

public class VncJni {
    public static final int SERVER_STARTED = 0;
    public static final int SERVER_STOPPED = 1;
    public static final int CLIENT_CONNECTED = 2;
    public static final int CLIENT_DISCONNECTED = 3;

    public static final int ROTATION_0 = 0;
    public static final int ROTATION_90 = 1;
    public static final int ROTATION_180 = 2;
    public static final int ROTATION_270 = 3;

    private NotificationListener mListener;

    public static interface NotificationListener {
        public void onNotification(int what, String message);
    }

    public void setNotificationListener(NotificationListener listener) {
        mListener = listener;
    }

    public void onNotification(int what, String message) {
        if (mListener != null) {
            mListener.onNotification(what, message);
        }
    }

    public void setRotation(int rotation) {
        onRotation(rotation);
    }

    public native void init();

    public native String protoGetVersion();

    public native void bindNextGraphicBuffer();

    public native void frameAvailable();

    public native int startServer(int width, int height,
                                  int pixelFormat, boolean fullFrameUpdate);

    public native int stopServer();

    public native void onRotation(int rotation);

    static {
        System.loadLibrary("vncserver");
    }
}
