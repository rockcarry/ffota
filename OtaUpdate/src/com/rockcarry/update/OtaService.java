package com.rockcarry.update;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
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
    private static final String OTASERVICE_SHARED_PREFS = "OTASERVICE_SHARED_PREFS";
    private static final String OTA_HOST_URL = "https://github.com/rockcarry/ffota/files/";

    private OtaBinder         mBinder     = new OtaBinder();
    private Downloader        mDownloader = null;
    private String            mAppDataPath= "";
    private SharedPreferences mSharedPref = null;
    private int               mOtaStatus  = 0;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        mDownloader = new Downloader(this, mServiceHandler);
        mAppDataPath= getDir("data", Context.MODE_PRIVATE).getAbsolutePath();
        mSharedPref = getSharedPreferences(OTASERVICE_SHARED_PREFS, Context.MODE_PRIVATE);
        mOtaStatus  = mSharedPref.getInt("mOtaStatus", OTA_STATUS_NOUPDATE);
        switch (mOtaStatus) {
        case OTA_STATUS_NOUPDATE:
        case OTA_STATUS_CHECKING:
            break;
        case OTA_STATUS_HASUPDATE:
            break;
        case OTA_STATUS_DOWNLOADING:
            break;
        case OTA_STATUS_READY:
            break;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        SharedPreferences.Editor editor = mSharedPref.edit();
        editor.putInt("mOtaStatus", mOtaStatus);
        editor.commit();
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

    private Handler mActivityHandler = null;
    private Handler mServiceHandler  = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case Downloader.MSG_DOWNLOAD_CONNECT:
                if (mOtaStatus == OTA_STATUS_DOWNLOADING) {
                }
                break;
            case Downloader.MSG_DOWNLOAD_RUNNING:
                if (mOtaStatus == OTA_STATUS_DOWNLOADING) {
                    int progress = msg.arg1;
                }
                break;
            case Downloader.MSG_DOWNLOAD_DONE:
                if (mOtaStatus == OTA_STATUS_DOWNLOADING) {
                } else if (mOtaStatus == OTA_STATUS_CHECKING) {
                    mOtaStatus = OTA_STATUS_HASUPDATE;
                }
                break;
            case Downloader.MSG_DOWNLOAD_FAILED:
                if (mOtaStatus == OTA_STATUS_DOWNLOADING) {
                } else if (mOtaStatus == OTA_STATUS_CHECKING) {
                    mOtaStatus = OTA_STATUS_NOUPDATE;
                }
                break;
            }
        }
    };

    public class OtaBinder extends Binder {
        public OtaService getService(Handler h) {
            mActivityHandler = h;
            return OtaService.this;
        }

        public int getStatus() {
            return mOtaStatus;
        }

        public int getDownloadStatus() {
            return mDownloader.mDownloadStatus;
        }

        public int getDownloadProgress() {
            return mDownloader.mDownloadProgress;
        }

        public void checkUpdate(String name) {
            String url = OTA_HOST_URL + name + ".ini";
            mOtaStatus = OTA_STATUS_CHECKING;
            mDownloader.newTask(mAppDataPath + "update.ini", url);
        }

        public void downloadUpdate(String name) {
            String url = OTA_HOST_URL + name + ".zip";
            mOtaStatus = OTA_STATUS_DOWNLOADING;
            if (url.equals(mDownloader.mDownloadUrlName)) {
                mDownloader.resumeTask();
            } else {
                mDownloader.newTask(mAppDataPath + "update.zip", url);
            }
        }

        public void applyUpdate() {
        }
    }
}

