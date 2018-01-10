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
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final static String TAG = "MainActivity";

    private TextView    mTxtInfo     = null;
    private EditText    mTxtUpdate   = null;
    private TextView    mTxtDownload = null;
    private Button      mBtnReCheck  = null;
    private Button      mBtnDownload = null;
    private Button      mBtnApply    = null;
    private ProgressBar mBarChecking = null;
    private ProgressBar mBarDownload = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mTxtInfo     = (TextView   )findViewById(R.id.txt_info    );
        mTxtUpdate   = (EditText   )findViewById(R.id.txt_update  );
        mTxtDownload = (TextView   )findViewById(R.id.txt_download);
        mBtnReCheck  = (Button     )findViewById(R.id.btn_recheck );
        mBtnDownload = (Button     )findViewById(R.id.btn_download);
        mBtnApply    = (Button     )findViewById(R.id.btn_apply   );
        mBarChecking = (ProgressBar)findViewById(R.id.bar_checking);
        mBarDownload = (ProgressBar)findViewById(R.id.bar_download);

        /*
        String otaid     = SystemProperties.get("ro.product.otaid", "unknown");
        String buildnum  = SystemProperties.get("ro.build.version.incremental", "unknown").split("-")[0];
        String androidver= SystemProperties.get("ro.build.version.release", "unknown");
        mTxtInfo.setText(otaid + "-" + androidver + "-" + buildnum + ".ini");
        */

        // start record service
        Intent i = new Intent(MainActivity.this, OtaService.class);
        startService(i);

        // bind record service
        bindService(i, mOtaServConn, Context.BIND_AUTO_CREATE);

        updateUI(3);
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

    private void updateUI(int status) {
            mTxtInfo    .setVisibility(View.VISIBLE);
            mTxtUpdate  .setVisibility(View.GONE);
            mTxtDownload.setVisibility(View.GONE);
            mBtnReCheck .setVisibility(View.GONE);
            mBtnDownload.setVisibility(View.GONE);
            mBtnApply   .setVisibility(View.GONE);
            mBarChecking.setVisibility(View.GONE);
            mBarDownload.setVisibility(View.GONE);
            mBtnReCheck .setEnabled(true);
        switch (status) {
        case OtaService.OTA_STATUS_NOUPDATE:
            mTxtInfo    .setText(getString(R.string.txt_noupdate));
            mBtnReCheck .setText(getString(R.string.btn_docheck ));
            mBtnReCheck .setVisibility(View.VISIBLE);
            break;
        case OtaService.OTA_STATUS_CHECKING:
            mTxtInfo    .setText(getString(R.string.txt_checking));
            mBtnReCheck .setText(getString(R.string.btn_docheck ));
            mBtnReCheck .setEnabled(false);
            mBtnReCheck .setVisibility(View.VISIBLE);
            mBarChecking.setVisibility(View.VISIBLE);
            break;
        case OtaService.OTA_STATUS_HASUPDATE:
            mTxtInfo    .setText(getString(R.string.txt_findupdate));
            mTxtUpdate  .setVisibility(View.VISIBLE);
            mBtnDownload.setText(getString(R.string.btn_download));
            mBtnDownload.setVisibility(View.VISIBLE);
            mBtnReCheck .setText(getString(R.string.btn_recheck));
            mBtnReCheck .setVisibility(View.VISIBLE);
            break;
        case OtaService.OTA_STATUS_DOWNLOADING:
            mTxtInfo    .setText(getString(R.string.txt_findupdate));
            mTxtUpdate  .setVisibility(View.VISIBLE);
            mBarDownload.setVisibility(View.VISIBLE);
            mTxtDownload.setVisibility(View.VISIBLE);
            mBtnDownload.setText(getString(R.string.btn_pause_dl));
            mBtnDownload.setVisibility(View.VISIBLE);
            mBtnReCheck .setText(getString(R.string.btn_cancel_dl));
            mBtnReCheck .setVisibility(View.VISIBLE);
            break;
        case OtaService.OTA_STATUS_READY:
            mTxtInfo    .setText(getString(R.string.txt_ready));
            mTxtUpdate  .setVisibility(View.VISIBLE);
            mBtnApply   .setVisibility(View.VISIBLE);
            mBtnReCheck .setText(getString(R.string.btn_recheck));
            mBtnReCheck .setVisibility(View.VISIBLE);
            break;
        }
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

