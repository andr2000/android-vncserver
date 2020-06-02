package com.a2k.vncserver;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "vncserver";

    private ToggleButton mToggleButton;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("vncserver");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tv = findViewById(R.id.sample_text);
        tv.setText(stringFromJNI());

        mToggleButton = (ToggleButton) findViewById(R.id.toggle);
        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleScreenShare(v);
            }
        });
    }

    public void onToggleScreenShare(View view) {
        if (((ToggleButton) view).isChecked()) {
            startService(new Intent(this, VncProjectionService.class));
        } else {
            stopService(new Intent(this, VncProjectionService.class));
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
