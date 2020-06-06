package com.a2k.vncserver;

import java.util.ArrayList;
import java.util.List;

public class VncJni {
    public static final int SERVER_STARTED = 0;
    public static final int SERVER_STOPPED = 1;
    public static final int CLIENT_CONNECTED = 2;
    public static final int CLIENT_DISCONNECTED = 3;

    private List<NotificationListener> mListener =
            new ArrayList<>();

    public interface NotificationListener {
        void onNotification(int what, String message);
        void onNotificationTouch(int buttonMask, int x, int y);
        void onNotificationKbd(int down, int key);
    }

    private static final VncJni mInstance = new VncJni();

    private VncJni() {}

    public static VncJni getInstance(){
        return mInstance;
    }

    public synchronized void setNotificationListener(NotificationListener listener) {
        mListener.add(listener);
    }

    public synchronized void removeNotificationListeners() {
        mListener.clear();
    }

    public synchronized void onNotification(int what, String message) {
        for (int i = 0; i < mListener.size(); i++) {
            mListener.get(i).onNotification(what, message);
        }
    }

    public synchronized void onNotificationTouch(int buttonMask, int x, int y) {
        for (int i = 0; i < mListener.size(); i++) {
            mListener.get(i).onNotificationTouch(buttonMask, x, y);
        }
    }

    public synchronized void onNotificationKbd(int down, int key) {
        for (int i = 0; i < mListener.size(); i++) {
            mListener.get(i).onNotificationKbd(down, key);
        }
    }

    public native void init();

    public native String protoGetVersion();

    public native void bindNextGraphicBuffer();

    public native void frameAvailable();

    public native int startServer(int width, int height,
                                  int pixelFormat, boolean fullFrameUpdate);

    public native int stopServer();

    static {
        System.loadLibrary("vncserver");
    }
}
