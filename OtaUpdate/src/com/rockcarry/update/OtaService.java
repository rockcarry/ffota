package com.rockcarry.update;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class OtaService extends Service {
    public  static final String OTAUPDATE_SHARED_PREFS = "OTAUPDATE_SHARED_PREFS";
    public  static final String OTAUPDATE_AUTOCHECK    = "OTAUPDATE_AUTOCHECK";
    public  static final String OTAUPDATE_STATUS       = "OTAUPDATE_STATUS";
    public  static final int    OTA_STATUS_NOUPDATE    = 0;
    public  static final int    OTA_STATUS_CHECKING    = 1;
    public  static final int    OTA_STATUS_HASUPDATE   = 2;
    public  static final int    OTA_STATUS_DOWNLOADING = 3;
    public  static final int    OTA_STATUS_READY       = 4;
    private static final String TAG = "OtaService";

    private Handler    mHandler    = new Handler();
    private OtaBinder  mBinder     = new OtaBinder();
    private Downloader mDownloader = null;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        mDownloader = new Downloader(this, mHandler);
        mDownloader.newTask(mDownloader.mAppDataPath + "/update.ini", "https://rockcarry.github.io/ffota/17303_tulip-p1_tulip_5.1.1_20180103_to_20180105.zip");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
//      return super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    public class OtaBinder extends Binder {
        public OtaService getService() {
            return OtaService.this;
        }
    }
}

