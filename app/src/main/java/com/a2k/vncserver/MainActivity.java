package com.a2k.vncserver;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "vncserver";
    private static final int PERMISSION_CODE = 1;

    private ToggleButton mToggleButton;
    private MediaProjectionManager mMediaProjectionManager;

    private VncProjectionService mVncProjectionService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tv = findViewById(R.id.sample_text);
        tv.setText("Hello");

        mToggleButton = (ToggleButton) findViewById(R.id.toggle);
        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleScreenShare(v);
            }
        });

        mMediaProjectionManager = (MediaProjectionManager) getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Log.e(TAG, "User denied screen sharing permission");
            return;
        }

        Intent intent = new Intent(this,
                VncProjectionService.class);
        intent.putExtra(VncProjectionService.PROJECTION_RESULT_CODE, resultCode);
        intent.putExtra(VncProjectionService.PROJECTION_RESULT_DATA, data);
        bindService(intent, mVncConnection, Context.BIND_AUTO_CREATE);
    }

    public void onToggleScreenShare(View view) {
        if (((ToggleButton) view).isChecked()) {
            startActivityForResult(
                    mMediaProjectionManager.createScreenCaptureIntent(),
                    PERMISSION_CODE);
        } else {
            if (mVncProjectionService != null)
                unbindService(mVncConnection);
            Intent intent = new Intent(this,VncProjectionService.class);
            stopService(intent);
            mVncProjectionService = null;
        }
    }

    private ServiceConnection mVncConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            VncProjectionService.VncProjectionServiceBinder binder =
                    (VncProjectionService.VncProjectionServiceBinder)service;
            mVncProjectionService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mVncProjectionService = null;
        }
    };
}
