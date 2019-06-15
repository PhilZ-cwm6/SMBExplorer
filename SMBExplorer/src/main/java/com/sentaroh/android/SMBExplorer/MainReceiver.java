package com.sentaroh.android.SMBExplorer;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.PowerManager;

import com.sentaroh.android.SMBExplorer.Log.LogUtil;
import com.sentaroh.android.Utilities.MiscUtil;
import com.sentaroh.android.Utilities.StringUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;

public class MainReceiver extends BroadcastReceiver {
    private static Logger slf4jLog = LoggerFactory.getLogger(MainReceiver.class);

    private static Context mContext = null;

    private static GlobalParameters mGp = null;

    private static LogUtil mLog = null;

    @Override
    final public void onReceive(Context c, Intent received_intent) {
        mContext = c;
        if (mGp == null) {
            mGp = GlobalWorkArea.getGlobalParameters(c);
        }
        String action = received_intent.getAction();
        slf4jLog.info("Receiver action="+action);
        if (action != null) {
            if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                mGp.setUsbMediaPath("");
            } else if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
            } else if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                mGp.setUsbMediaPath("");
            } else {
            }
        }
    }
}
