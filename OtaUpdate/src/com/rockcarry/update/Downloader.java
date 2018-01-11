package com.rockcarry.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.apache.http.HttpStatus;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.*;

public class Downloader {
    private static final String TAG = "Downloader";
    private static final String DOWNLOADER_SHARED_PREFS = "DOWNLOADER_SHARED_PREFS";
    private Context           mContext;
    private Handler           mHandler;
    private SharedPreferences mSharedPref;
    private Thread            mThread;
    private boolean           mPaused;

    public static final int MSG_DOWNLOAD_CONNECTING= 0;
    public static final int MSG_DOWNLOAD_CONNECTED = 1;
    public static final int MSG_DOWNLOAD_RUNNING   = 2;
    public static final int MSG_DOWNLOAD_PAUSED    = 3;
    public static final int MSG_DOWNLOAD_DONE      = 4;
    public static final int MSG_DOWNLOAD_FAILED    = 5;

    public String mDownloadFileName;
    public String mDownloadUrlName;
    public int    mDownloadFileSize;
    public int    mDownloadFileOffset;
    public int    mDownloadStatus;
    public int    mDownloadProgress;

    public Downloader(Context context, Handler handler) {
        mContext    = context;
        mHandler    = handler;
        mSharedPref = mContext.getSharedPreferences(DOWNLOADER_SHARED_PREFS, Context.MODE_PRIVATE);
        mDownloadFileName   = mSharedPref.getString("mDownloadFileName"  , "");
        mDownloadUrlName    = mSharedPref.getString("mDownloadUrlName"   , "");
        mDownloadFileSize   = mSharedPref.getInt   ("mDownloadFileSize"  , 0 );
        mDownloadFileOffset = mSharedPref.getInt   ("mDownloadFileOffset", 0 );
        mDownloadStatus     = mSharedPref.getInt   ("mDownloadStatus"    , 0 );
        if (mDownloadFileSize > 0) {
            mDownloadProgress = 100 * mDownloadFileOffset / mDownloadFileSize;
        }
    }

    public void newTask(final String filename, final String urlname) {
        if (mThread != null) return;
        mPaused = false;
        mThread = new Thread() {
            @Override
            public void run() {
                download(filename, urlname, 0);
                mThread = null;
            }
        };
        mThread.start();
    }

    public void resumeTask() {
        if (mThread != null) return;
        mPaused = false;
        mThread = new Thread() {
            @Override
            public void run() {
                download(mDownloadFileName, mDownloadUrlName, mDownloadFileOffset);
                mThread = null;
            }
        };
        mThread.start();
    }

    public void pauseTask() {
        try {
            mPaused = true;
            if (mThread != null) mThread.join();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void download(String filename, String urlname, int offset) {
        URL               url  = null;
        HttpURLConnection conn = null;
        InputStream       is   = null;
        File              file = null;
        RandomAccessFile  rf   = null;
        byte[]            buf  = new byte[1024];
        int               len  = 0;

        mDownloadFileName   = filename;
        mDownloadUrlName    = urlname;
        mDownloadFileSize   = 0;
        mDownloadFileOffset = offset;
        mDownloadProgress   = 0;

        try {
            url  = new URL (mDownloadUrlName );
            file = new File(mDownloadFileName);
            rf   = new RandomAccessFile(file, "rwd");

            // get download file size
            mDownloadStatus = MSG_DOWNLOAD_CONNECTING;
            if (mHandler != null) mHandler.sendEmptyMessage(mDownloadStatus);
            conn = (HttpURLConnection) url.openConnection();
            if (url.getProtocol().toLowerCase().equals("https")) {
                trustAllHosts();
                ((HttpsURLConnection)conn).setHostnameVerifier(DO_NOT_VERIFY);
            }
            conn.setConnectTimeout(5000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept-Encoding", "identity");
            if (conn.getResponseCode() == HttpStatus.SC_OK) {
                mDownloadFileSize = conn.getContentLength();
            } else {
                Log.w(TAG, "failed to get http response code !");
                mDownloadStatus = MSG_DOWNLOAD_FAILED;
                if (mHandler != null) mHandler.sendEmptyMessage(mDownloadStatus);
                return;
            }
            mDownloadStatus = MSG_DOWNLOAD_CONNECTED;
            if (mHandler != null) mHandler.sendEmptyMessage(mDownloadStatus);

            // try partial download
            conn = (HttpURLConnection) url.openConnection();
            if (url.getProtocol().toLowerCase().equals("https")) {
                trustAllHosts();
                ((HttpsURLConnection)conn).setHostnameVerifier(DO_NOT_VERIFY);
            }
            conn.setConnectTimeout(5000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.setRequestProperty("Range", "bytes=" + mDownloadFileOffset + "-" + mDownloadFileSize);
            if (conn.getResponseCode() != HttpStatus.SC_PARTIAL_CONTENT) {
                Log.d(TAG, "unable to start partial download !");
                mDownloadFileOffset = 0;
            }

            Log.d(TAG, "download file name  : " + mDownloadFileName  );
            Log.d(TAG, "download file url   : " + mDownloadUrlName   );
            Log.d(TAG, "download file size  : " + mDownloadFileSize  );
            Log.d(TAG, "download file offset: " + mDownloadFileOffset);

            rf.setLength(mDownloadFileSize);
            rf.seek(mDownloadFileOffset);
            is = conn.getInputStream();
            while (!mPaused && (len = is.read(buf)) != -1) {
                rf.write(buf, 0, len);
                mDownloadFileOffset += len;
                int progress = 100 * mDownloadFileOffset / mDownloadFileSize;
                if (mDownloadProgress != progress) {
                    mDownloadStatus   = MSG_DOWNLOAD_RUNNING;
                    mDownloadProgress = progress;
                    if (mHandler != null) mHandler.sendEmptyMessage(mDownloadStatus);
                }
//              Log.d(TAG, "download progress: " + mDownloadProgress);
            }
            mDownloadStatus = mDownloadFileOffset == mDownloadFileSize ? MSG_DOWNLOAD_DONE : MSG_DOWNLOAD_PAUSED;
            if (mHandler != null) mHandler.sendEmptyMessage(mDownloadStatus);
        } catch (Exception e) {
            Log.w(TAG, "download failed !");
            e.printStackTrace();
            mDownloadStatus = MSG_DOWNLOAD_FAILED;
            if (mHandler != null) mHandler.sendEmptyMessage(mDownloadStatus);
        } finally {
            try {
                if (rf != null) rf.close();
                if (is != null) is.close();
            } catch (Exception e) { e.printStackTrace(); }

            SharedPreferences.Editor editor = mSharedPref.edit();
            editor.putString("mDownloadFileName"  , mDownloadFileName  );
            editor.putString("mDownloadUrlName"   , mDownloadUrlName   );
            editor.putInt   ("mDownloadFileSize"  , mDownloadFileSize  );
            editor.putInt   ("mDownloadFileOffset", mDownloadFileOffset);
            editor.putInt   ("mDownloadStatus"    , mDownloadStatus    );
            editor.commit();
        }
    }

    /**
     * Trust every server - dont check for any certificate
     */
    private static void trustAllHosts() {
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[] {};
                }
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    Log.i(TAG, "checkClientTrusted");
                }
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    Log.i(TAG, "checkServerTrusted");
                }
            }
        };

        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };
}


