package com.sentaroh.android.SMBExplorer.Log;

import android.os.Bundle;

import com.sentaroh.android.Utilities2.LogUtil.CommonLogManagementFragment;


public class LogManagementFragment extends CommonLogManagementFragment {
	public static LogManagementFragment newInstance(boolean retainInstance, String title) {
		LogManagementFragment frag = new LogManagementFragment();
        Bundle bundle = new Bundle();
        bundle.putBoolean("retainInstance", retainInstance);
        bundle.putString("title", title);
        bundle.putString("msgtext", "send essage");
        bundle.putString("enableMsg", "Do you want to enable the log?");
        frag.setArguments(bundle);
        return frag;
    }

}
