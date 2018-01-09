package com.rockcarry.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d(TAG, "boot completed !");
        try {
            SharedPreferences sp = context.getSharedPreferences(OtaService.OTAUPDATE_SHARED_PREFS, Context.MODE_PRIVATE);;
            if (sp.getInt(OtaService.OTAUPDATE_AUTOCHECK, 0) == 1) {
                Intent service = new Intent(context, com.rockcarry.update.OtaService.class);
                context.startService(service);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}

