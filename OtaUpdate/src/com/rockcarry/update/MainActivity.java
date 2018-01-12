package com.rockcarry.update;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.EditText;
import android.widget.TextView;
import android.util.Log;

public class MainActivity extends Activity implements View.OnClickListener {
    private final static String TAG = "MainActivity";

    private TextView    mTxtInfo       = null;
    private EditText    mTxtUpdate     = null;
    private TextView    mTxtDownload   = null;
    private Button      mBtnReCheck    = null;
    private Button      mBtnDownload   = null;
    private Button      mBtnApply      = null;
    private ProgressBar mBarChecking   = null;
    private ProgressBar mBarDownload   = null;

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

        mTxtUpdate  .setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        mBtnReCheck .setOnClickListener(this);
        mBtnDownload.setOnClickListener(this);
        mBtnApply   .setOnClickListener(this);

        // init ui
        updateUI(-1);

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
        if (mOtaServ != null) mOtaServ.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOtaServ != null) mOtaServ.onPause();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.btn_recheck:
            if (mOtaServ.mOtaStatus == OtaService.OTA_STATUS_DOWNLOADING) {
                mOtaServ.reset();
            } else {
                mOtaServ.checkUpdate();
            }
            break;
        case R.id.btn_download:
            mBtnDownload.setEnabled(false);
            if (mOtaServ.mOtaStatus == OtaService.OTA_STATUS_DOWNLOADING) {
                if (mOtaServ.getDownloadStatus() == Downloader.MSG_DOWNLOAD_RUNNING) {
                    mOtaServ.downloadPause();
                } else {
                    mOtaServ.downloadResume();
                }
            } else {
                mBtnDownload.setText(getString(R.string.btn_pause_dl));
                mBarDownload.setProgress(0);
                mOtaServ.downloadOtaPackage();
            }
            break;
        case R.id.btn_apply:
            mOtaServ.applyOtaPackage();
            break;
        }
    }

    private void updateUI(int status) {
        mTxtUpdate  .setVisibility(View.GONE);
        mTxtDownload.setVisibility(View.GONE);
        mBtnReCheck .setVisibility(View.GONE);
        mBtnDownload.setVisibility(View.GONE);
        mBtnApply   .setVisibility(View.GONE);
        mBarChecking.setVisibility(View.GONE);
        mBarDownload.setVisibility(View.GONE);
        mBtnReCheck .setEnabled(true);
        switch (status) {
        case OtaService.OTA_STATUS_INIT:
        case OtaService.OTA_STATUS_NOUPDATE:
            mTxtInfo    .setText(status == OtaService.OTA_STATUS_INIT ? "" : getString(R.string.txt_noupdate));
            mTxtUpdate  .setText(mOtaServ.mDeviceInfo);
            mTxtUpdate  .setVisibility(View.VISIBLE);
            mBtnReCheck .setText(getString(R.string.btn_docheck));
            mBtnReCheck .setVisibility(View.VISIBLE);
            break;
        case OtaService.OTA_STATUS_CHECKING:
            mTxtInfo    .setText(getString(R.string.txt_checking));
            mBarChecking.setVisibility(View.VISIBLE);
            mBtnReCheck .setText(getString(R.string.btn_docheck ));
            mBtnReCheck .setEnabled(false);
            mBtnReCheck .setVisibility(View.VISIBLE);
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
            mTxtInfo    .setText(getString(R.string.txt_downloading));
            mTxtUpdate  .setVisibility(View.VISIBLE);
            mBarDownload.setVisibility(View.VISIBLE);
            mTxtDownload.setVisibility(View.VISIBLE);
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
        case OtaService.OTA_STATUS_APPLY:
            mTxtInfo    .setText(getString(R.string.txt_apply));
            mBarChecking.setVisibility(View.VISIBLE);
            break;
        case OtaService.OTA_STATUS_ERROR:
            mTxtInfo    .setText(getString(R.string.txt_error));
            mBarChecking.setVisibility(View.VISIBLE);
            mBtnReCheck .setText(getString(R.string.btn_recheck));
            mBtnReCheck .setVisibility(View.VISIBLE);
            break;
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            updateUI(msg.what);

            switch (msg.what) {
            case OtaService.OTA_STATUS_HASUPDATE:
            case OtaService.OTA_STATUS_DOWNLOADING:
            case OtaService.OTA_STATUS_READY:
                if (mOtaServ.getDownloadStatus() != Downloader.MSG_DOWNLOAD_RUNNING) {
                    long   size = mOtaServ.getOtaPackageSize();
                    String str  = "";
                    if (size > 1024L*1024*1024) {
                        str = String.format("%.2f GB", (double)size / 1024L*1024*1024);
                    } else if (size > 1024L*1024) {
                        str = String.format("%.2f MB", (double)size / 1024L*1024);
                    } else if (size > 1024L) {
                        str = String.format("%.2f KB", (double)size / 1024L);
                    } else if (size > 0) {
                        str = String.format("%d Bytes", size);
                    }
                    if (size <= 0) {
                        str = String.format(getString(R.string.txt_update_fmt0), mOtaServ.mUpdateTarget, mOtaServ.mUpdateCheckSum, mOtaServ.mUpdateDetail);
                    } else {
                        str = String.format(getString(R.string.txt_update_fmt1), mOtaServ.mUpdateTarget, str, mOtaServ.mUpdateCheckSum, mOtaServ.mUpdateDetail);
                    }
                    mTxtUpdate.setText(str);
                }
                switch (mOtaServ.getDownloadStatus()) {
                case Downloader.MSG_DOWNLOAD_CONNECTING:
                    mTxtDownload.setText(getString(R.string.dl_connecting));
                    break;
                case Downloader.MSG_DOWNLOAD_CONNECTED:
                    mTxtDownload.setText(getString(R.string.dl_connected));
                    break;
                case Downloader.MSG_DOWNLOAD_RUNNING:
                    mTxtDownload.setText("" + mOtaServ.getDownloadProgress() + "%");
                    mBtnDownload.setText(getString(R.string.btn_pause_dl));
                    mBtnDownload.setEnabled(true);
                    mBarDownload.setProgress(mOtaServ.getDownloadProgress());
                    break;
                case Downloader.MSG_DOWNLOAD_PAUSED:
                    mTxtDownload.setText(getString(R.string.dl_paused));
                    mBtnDownload.setText(getString(R.string.btn_resume_dl));
                    mBtnDownload.setEnabled(true);
                    mBarDownload.setProgress(mOtaServ.getDownloadProgress());
                    break;
                case Downloader.MSG_DOWNLOAD_DONE:
                    mTxtDownload.setText(getString(R.string.dl_done));
                    mBarDownload.setProgress(100);
                    break;
                case Downloader.MSG_DOWNLOAD_FAILED:
                    mTxtDownload.setText(getString(R.string.dl_failed));
                    mBtnDownload.setText(getString(R.string.btn_resume_dl));
                    mBtnDownload.setEnabled(true);
                    break;
                }
                break;
            }
        }
    };

    private OtaService mOtaServ = null;
    private ServiceConnection mOtaServConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder serv) {
            mOtaServ = ((OtaService.OtaBinder)serv).getService(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mOtaServ = null;
        }
    };
}

