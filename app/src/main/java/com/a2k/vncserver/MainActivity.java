package com.a2k.vncserver;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "vncserver";
    private static final int PERMISSION_CODE = 1;

    private ToggleButton mToggleButton;
    private MediaProjectionManager mMediaProjectionManager;

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

        mMediaProjectionManager = (MediaProjectionManager)getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode != PERMISSION_CODE)
        {
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
        startService(intent);
    }

    private void startProjectionService()
    {
        startActivityForResult(
                mMediaProjectionManager.createScreenCaptureIntent(),
                PERMISSION_CODE);
    }

    private void stopProjectionService()
    {
        stopService(new Intent(this, VncProjectionService.class));
    }

    public void onToggleScreenShare(View view) {
        if (((ToggleButton) view).isChecked()) {
            startProjectionService();
        } else {
            stopProjectionService();
        }
    }
}
