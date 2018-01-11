package com.rockcarry.update;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.IBinder;
import android.os.RecoverySystem;
import android.os.SystemProperties;
import android.util.Log;

import java.io.*;
import java.util.*;

public class OtaService extends Service {
    public  static final String OTAUPDATE_SHARED_PREFS = "OTAUPDATE_SHARED_PREFS";
    public  static final String OTAUPDATE_STATUS       = "OTAUPDATE_STATUS";
    public  static final int    OTA_STATUS_INIT        = 0;
    public  static final int    OTA_STATUS_NOUPDATE    = 1;
    public  static final int    OTA_STATUS_CHECKING    = 2;
    public  static final int    OTA_STATUS_HASUPDATE   = 3;
    public  static final int    OTA_STATUS_DOWNLOADING = 4;
    public  static final int    OTA_STATUS_READY       = 5;
    public  static final int    OTA_STATUS_APPLY       = 6;
    public  static final int    OTA_STATUS_ERROR       = 7;

    public  String mDeviceInfo     = "";
    private String mUpdateIniUrl   = "";
    private String mUpdateZipUrl   = "";
    public  String mUpdateTarget   = "";
    public  String mUpdateCheckSum = "";
    public  String mUpdateDetail   = "";

    private static final String TAG = "OtaService";
    private static final String OTASERVICE_SHARED_PREFS = "OTASERVICE_SHARED_PREFS";
    private static final String OTA_HOST_URL = "https://rockcarry.github.io/ffota/files/";
    private static final String UPDATE_FILE_PATH = "/cache/update.zip";

    private OtaBinder         mBinder     = new OtaBinder();
    private Downloader        mDownloader = null;
    private String            mAppDataPath= "";
    private SharedPreferences mSharedPref = null;
    private int               mOtaStatus  = 0;
    private boolean           mActivityResume;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        mDownloader  = new Downloader(this, mServiceHandler);
        mAppDataPath = getDir("data", Context.MODE_PRIVATE).getAbsolutePath();
        mSharedPref  = getSharedPreferences(OTASERVICE_SHARED_PREFS, Context.MODE_PRIVATE);
        mOtaStatus   = mSharedPref.getInt("mOtaStatus", OTA_STATUS_NOUPDATE);

        mDeviceInfo  = String.format(getString(R.string.txt_devinfo_fmt),
            Build.MODEL, Build.VERSION.RELEASE, Build.DISPLAY,
            SystemProperties.get("ro.build.version.incremental", "unknown").split("-")[0],
            SystemProperties.get("ro.product.otaid", "unknown"));
        mUpdateIniUrl= OTA_HOST_URL + String.format("%s-%s-%s", SystemProperties.get("ro.product.otaid", "unknown"),
            Build.VERSION.RELEASE, SystemProperties.get("ro.build.version.incremental", "unknown").split("-")[0]) + ".ini";

        switch (mOtaStatus) {
        case OTA_STATUS_HASUPDATE:
        case OTA_STATUS_READY:
        case OTA_STATUS_DOWNLOADING:
            parseUpdateInfo();
            break;
        case OTA_STATUS_CHECKING:
            checkUpdate();
            break;
        }

        mServiceHandler.postDelayed(
            new Runnable() {
                @Override
                public void run() {
                    if (mActivityHandler != null) {
                        return;
                    }
                    switch (mOtaStatus) {
                    case OTA_STATUS_INIT:
                    case OTA_STATUS_NOUPDATE:
                    case OTA_STATUS_HASUPDATE:
                        checkUpdate();
                        break;
                    case OTA_STATUS_DOWNLOADING:
                        showNotification(!mActivityResume, true, getResources().getString(R.string.txt_downloading) + " " + mDownloader.mDownloadProgress + "%");
                        break;
                    case OTA_STATUS_READY:
                        showNotification(!mActivityResume, true, getResources().getString(R.string.txt_ready));
                        break;
                    }
                }
            }, 10000);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        downloadPause(true);
        showNotification(false, false, null);
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
        return START_STICKY;
    }

    private void parseUpdateInfo() {
        FileInputStream is     = null;
        BufferedReader  reader = null;
        String          lang   = "[" + Locale.getDefault().getLanguage() + "]";

        mUpdateTarget   = "";
        mUpdateCheckSum = "";
        mUpdateDetail   = "";
        mUpdateZipUrl   = "";
        try {
            File file = new File(mAppDataPath + "/update.ini");
            is     = new FileInputStream(file);
            reader = new BufferedReader(new InputStreamReader(is, "utf-8"));
            boolean first  = true;
            String  buffer = null;
            while ((buffer = reader.readLine()) != null) {
                //++ remove utf-8 bom
                if (first) {
                    if (buffer.startsWith("\uFEFF")) {
                        buffer = buffer.replace("\uFEFF", "");
                    }
                    first = false;
                }
                //-- remove utf-8 bom
                String[] list = buffer.split("\\s*:\\s*");
                if (list.length >= 2) {
                    if (list[0].equals("target")) {
                        mUpdateTarget = list[1];
                        mUpdateZipUrl = mUpdateIniUrl.replace(".ini", "");
                        mUpdateZipUrl+= "-to-" + mUpdateTarget + ".zip";
                    }
                    if (list[0].equals("md5"   )) mUpdateCheckSum = list[1];
                } else {
                    if (list[0].equals(lang)) {
                        break;
                    }
                }
            }
            while ((buffer = reader.readLine()) != null) {
                if (buffer.equals("<<<")) continue;
                if (buffer.equals(">>>")) break;
                mUpdateDetail += buffer + "\n";
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) reader.close();
                if (is     != null) is    .close();
            } catch (Exception e) {}
        }
    }

    private Handler mActivityHandler = null;
    private Handler mServiceHandler  = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case Downloader.MSG_DOWNLOAD_CONNECTING:
            case Downloader.MSG_DOWNLOAD_CONNECTED:
            case Downloader.MSG_DOWNLOAD_RUNNING:
            case Downloader.MSG_DOWNLOAD_PAUSED:
                if (mOtaStatus == OTA_STATUS_DOWNLOADING) {
                    if (mActivityHandler != null) mActivityHandler.sendEmptyMessage(mOtaStatus);
                    showNotification(!mActivityResume, false, getResources().getString(R.string.txt_downloading) + " " + mDownloader.mDownloadProgress + "%");
                }
                break;
            case Downloader.MSG_DOWNLOAD_DONE:
                if (mOtaStatus == OTA_STATUS_DOWNLOADING) {
                    mOtaStatus = OTA_STATUS_READY;
                    if (mActivityHandler != null) mActivityHandler.sendEmptyMessage(mOtaStatus);
                    showNotification(!mActivityResume, true, getResources().getString(R.string.txt_ready));
                } else if (mOtaStatus == OTA_STATUS_CHECKING) {
                    parseUpdateInfo();
                    mOtaStatus = OTA_STATUS_HASUPDATE;
                    if (mActivityHandler != null) mActivityHandler.sendEmptyMessage(mOtaStatus);
                    showNotification(!mActivityResume, true, getResources().getString(R.string.txt_findupdate));
                }
                break;
            case Downloader.MSG_DOWNLOAD_FAILED:
                if (mOtaStatus == OTA_STATUS_CHECKING) {
                    mOtaStatus = OTA_STATUS_NOUPDATE;
                }
                if (mActivityHandler != null) mActivityHandler.sendEmptyMessage(mOtaStatus);
                if (mOtaStatus == OTA_STATUS_DOWNLOADING) {
                    showNotification(!mActivityResume, true, getResources().getString(R.string.dl_failed));
                }
                break;
            }
        }
    };

    private static final int NOTIFICATION_ID = 1;
    private Notification        mNotification = new Notification();
    private NotificationManager mNotifyManager= null;
    private void showNotification(boolean show, boolean sound, String msg) {
        if (mNotifyManager == null) mNotifyManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if (show) {
            PendingIntent pi  = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
            mNotification.flags    = Notification.FLAG_ONGOING_EVENT;
            mNotification.icon     = R.drawable.ic_launcher;
            mNotification.defaults = sound ? Notification.DEFAULT_SOUND : 0;
            mNotification.setLatestEventInfo(this, getResources().getString(R.string.app_name), msg, pi);
            mNotifyManager.notify(NOTIFICATION_ID, mNotification);
        }
        else {
            mNotifyManager.cancel(NOTIFICATION_ID);
        }
    }

    public class OtaBinder extends Binder {
        public OtaService getService(Handler h) {
            showNotification(false, false, null);
            mActivityResume  = true;
            mActivityHandler = h;
            if (mActivityHandler != null) mActivityHandler.sendEmptyMessage(mOtaStatus);
            return OtaService.this;
        }
    }

    public int getStatus() {
        return mOtaStatus;
    }

    public int getDownloadStatus() {
        return mDownloader.mDownloadStatus;
    }

    public int getDownloadProgress() {
        return mDownloader.mDownloadFileName.contains("update.zip") ? mDownloader.mDownloadProgress : 0;
    }

    public int getUpdateSize() {
        return mDownloader.mDownloadFileName.contains("update.zip") ? mDownloader.mDownloadFileSize : -1;
    }

    public void reset() {
        mDownloader.pauseTask();
        mOtaStatus = OTA_STATUS_INIT;
        if (mActivityHandler != null) mActivityHandler.sendEmptyMessage(mOtaStatus);
    }

    public void checkUpdate() {
        mOtaStatus = OTA_STATUS_CHECKING;
        if (mActivityHandler != null) mActivityHandler.sendEmptyMessage(mOtaStatus);
        mDownloader.newTask(mAppDataPath + "/update.ini", mUpdateIniUrl);
    }

    public void downloadUpdate() {
        mOtaStatus = OTA_STATUS_DOWNLOADING;
        if (mActivityHandler != null) mActivityHandler.sendEmptyMessage(mOtaStatus);
        if (mUpdateZipUrl.equals(mDownloader.mDownloadUrlName)) {
            mDownloader.resumeTask();
        } else {
            mDownloader.newTask(UPDATE_FILE_PATH, mUpdateZipUrl);
        }
    }

    public void downloadPause(boolean pause) {
        if (pause) {
            mDownloader.pauseTask();
        } else {
            mDownloader.resumeTask();
        }
    }

    public void applyUpdate() {
        if (mActivityHandler != null) mActivityHandler.sendEmptyMessage(OTA_STATUS_APPLY);
        try {
            File file = new File(UPDATE_FILE_PATH);
            RecoverySystem.verifyPackage(
                file,
                new RecoverySystem.ProgressListener() {
                    @Override
                    public void onProgress(int progress) {}
                },
                null);

            mOtaStatus = OTA_STATUS_INIT;
            SharedPreferences.Editor editor = mSharedPref.edit();
            editor.putInt("mOtaStatus", mOtaStatus);
            editor.commit();

            RecoverySystem.installPackage(this, file);
        } catch (Exception e) {
            e.printStackTrace();
            if (mActivityHandler != null) mActivityHandler.sendEmptyMessage(OTA_STATUS_ERROR);
        }
    }

    public void onResume() {
        mActivityResume = true;
        showNotification(false, false, null);
    }

    public void onPause() {
        mActivityResume = false;
    }
}

