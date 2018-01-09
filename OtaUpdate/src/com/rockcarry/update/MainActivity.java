package com.rockcarry.update;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemProperties;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final static String TAG = "MainActivity";

    private TextView mTxtInfo = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mTxtInfo = (TextView)findViewById(R.id.txt_info);
        String otaid     = SystemProperties.get("ro.product.otaid", "unknown");
        String buildnum  = SystemProperties.get("ro.build.version.incremental", "unknown").split("-")[0];
        String androidver= SystemProperties.get("ro.build.version.release", "unknown");
        mTxtInfo.setText(otaid + "-" + androidver + "-" + buildnum + ".ini");

        // start record service
        Intent i = new Intent(MainActivity.this, OtaService.class);
        startService(i);

        // bind record service
        bindService(i, mOtaServConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // unbind record service
        unbindService(mOtaServConn);

        // stop record service
        Intent i = new Intent(MainActivity.this, OtaService.class);
        stopService(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private OtaService.OtaBinder mOtaServ = null;
    private ServiceConnection mOtaServConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder serv) {
            mOtaServ = (OtaService.OtaBinder)serv;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mOtaServ = null;
        }
    };
}

