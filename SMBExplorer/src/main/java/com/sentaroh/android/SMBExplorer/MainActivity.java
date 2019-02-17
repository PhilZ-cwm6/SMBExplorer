package com.sentaroh.android.SMBExplorer;

/*
The MIT License (MIT)
Copyright (c) 2011-2013 Sentaroh

Permission is hereby granted, free of charge, to any person obtaining a copy of 
this software and associated documentation files (the "Software"), to deal 
in the Software without restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
and to permit persons to whom the Software is furnished to do so, subject to 
the following conditions:

The above copyright notice and this permission notice shall be included in all copies or 
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR 
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE 
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, 
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

*/

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import com.sentaroh.android.SMBExplorer.Log.LogFileListDialogFragment;
import com.sentaroh.android.Utilities.ContextMenu.CustomContextMenu;
import com.sentaroh.android.Utilities.Dialog.CommonDialog;
import com.sentaroh.android.Utilities.NotifyEvent;
import com.sentaroh.android.Utilities.SafManager;
import com.sentaroh.android.Utilities.ThemeUtil;
import com.sentaroh.android.Utilities.Widget.CustomViewPager;
import com.sentaroh.android.Utilities.Widget.CustomViewPagerAdapter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

import static com.sentaroh.android.SMBExplorer.Constants.SMBEXPLORER_PROFILE_NAME;
import static com.sentaroh.android.SMBExplorer.Constants.SMBEXPLORER_TAB_LOCAL;
import static com.sentaroh.android.SMBExplorer.Constants.SMBEXPLORER_TAB_POS_LOCAL;
import static com.sentaroh.android.SMBExplorer.Constants.SMBEXPLORER_TAB_REMOTE;

public class MainActivity extends AppCompatActivity {
	private final static String DEBUG_TAG = "SMBExplorer";

	private GlobalParameters mGp=null;
    private CommonUtilities mUtil=null;
    private UsbReceiver mUsbReceiver=null;
	private boolean mIsApplicationTerminate = false;
	private int restartStatus=0;
	private Context mContext=null;
	private CustomContextMenu ccMenu = null;
	private MainActivity mActivity=null;
	private ActionBar mActionBar=null;

    private CustomViewPager mMainViewPager=null;
    private CustomViewPagerAdapter mMainViewPagerAdapter=null;

    private Handler mUiHandler=null;

    private FileManager mFileMgr=null;

//    @Override
//    protected void onSaveInstanceState(Bundle out) {
//        super.onSaveInstanceState(out);
//        mUtil.addDebugMsg(1, "I", CommonUtilities.getExecutedMethodName() + " entered.");
//    }
//
//    @Override
//    protected void onRestoreInstanceState(Bundle in) {
//        super.onRestoreInstanceState(in);
//        mUtil.addDebugMsg(1, "I", CommonUtilities.getExecutedMethodName() + " entered.");
//    }

    @Override
	public void onCreate(Bundle savedInstanceState) {
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        super.onCreate(savedInstanceState);

		mContext=this;
		mActivity=this;
        mGp=GlobalWorkArea.getGlobalParameters(mContext);

        mUtil=mGp.mUtil=new CommonUtilities(mContext, "Main", mGp);
		setContentView(R.layout.main);
        mUiHandler=new Handler();
		mActionBar = getSupportActionBar();
		mActionBar.setHomeButtonEnabled(false);
		mGp.localBase=mGp.internalRootDirectory;

		if (ccMenu ==null) ccMenu = new CustomContextMenu(getResources(),getSupportFragmentManager());
		mGp.commonDlg=new CommonDialog(mContext, getSupportFragmentManager());

		myUncaughtExceptionHandler.init();

        mFileMgr=new FileManager(mActivity, mGp, mUtil, ccMenu);

        createTabAndView() ;

		mUtil.addDebugMsg(1, "I", "onCreate entered");
		mIsApplicationTerminate = false;
		ContextCompat.getExternalFilesDirs(mContext, null);
        restartStatus=0;

        checkRequiredPermissions();

        Intent intmsg = new Intent(mContext, MainService.class);
        startService(intmsg);

        mUsbReceiver=new UsbReceiver();
        IntentFilter int_filter = new IntentFilter();
        int_filter = new IntentFilter();
        int_filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        int_filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, int_filter);

        mFileMgr.loadLocalFilelist(mGp.localBase,mGp.localDir, null);
        mFileMgr.setEmptyFolderView();

//        startLogCat("/storage/emulated/0","logcat.txt", mTcLogCat);

//        long target=1534738805000L, master=1534738463738L;
//        Log.v("","taget="+ StringUtil.convDateTimeTo_YearMonthDayHourMinSecMili(target));
//        Log.v("","master="+ StringUtil.convDateTimeTo_YearMonthDayHourMinSecMili(master));

    }

	@Override
	protected void onStart() {
		super.onStart();
		mUtil.addDebugMsg(1, "I", "onStart entered");
	}

	@Override
	protected void onRestart() {
		super.onRestart();
		mUtil.addDebugMsg(1, "I", "onRestart entered");
	}

    @Override
	protected void onResume() {
		super.onResume();
		mUtil.addDebugMsg(1, "I","onResume entered"+ " restartStatus="+restartStatus);

        if (restartStatus==1) {
            setActivityInForeground();
            if (mGp.currentTabName.equals(SMBEXPLORER_TAB_LOCAL)) {
                if (!isUiEnabled()) {
                    mGp.localFileListView.setVisibility(ListView.INVISIBLE);
                } else {
                    mFileMgr.refreshFileListView();
                }
            }
        } else {
            NotifyEvent ntfy=new NotifyEvent(mContext);
            ntfy.setListener(new NotifyEvent.NotifyEventListener() {
                @Override
                public void positiveResponse(Context context, Object[] objects) {
                    setCallbackListener();
                    if (restartStatus==0) {
                        mGp.smbConfigList = SmbServerUtil.createSmbServerConfigList(mGp,false,"");
                        mFileMgr.setMainListener();
                        refreshOptionMenu();
                    }
                    restartStatus=1;
                }
                @Override
                public void negativeResponse(Context context, Object[] objects) {
                }
            });
            openService(ntfy);
        }
	}

    private void openService(NotifyEvent p_ntfy) {
        mUtil.addDebugMsg(1,"I",CommonUtilities.getExecutedMethodName()+" entered");
        mGp.svcConnection = new ServiceConnection(){
            public void onServiceConnected(ComponentName arg0, IBinder service) {
                mUtil.addDebugMsg(1,"I",CommonUtilities.getExecutedMethodName()+" entered");
                mGp.svcClient =ISvcClient.Stub.asInterface(service);
                p_ntfy.notifyToListener(true, null);
            }
            public void onServiceDisconnected(ComponentName name) {
                mGp.svcConnection = null;
                mUtil.addDebugMsg(1,"I",CommonUtilities.getExecutedMethodName()+" entered");
//    	    	}
            }
        };

        Intent intmsg = new Intent(mContext, MainService.class);
        intmsg.setAction("Bind");
        bindService(intmsg, mGp.svcConnection, BIND_AUTO_CREATE);
    }

    final private void setCallbackListener() {
        mUtil.addDebugMsg(1, "I", CommonUtilities.getExecutedMethodName() + " entered");
        try {
            mGp.svcClient.setCallBack(mSvcCallbackStub);
        } catch (RemoteException e) {
            e.printStackTrace();
            mUtil.addDebugMsg(1, "E", "setCallbackListener error :" + e.toString());
        }
    }

    final private void unsetCallbackListener() {
        mUtil.addDebugMsg(1, "I", CommonUtilities.getExecutedMethodName() + " entered");
        if (mGp.svcClient != null) {
            try {
                mGp.svcClient.removeCallBack(mSvcCallbackStub);
            } catch (RemoteException e) {
                e.printStackTrace();
                mUtil.addDebugMsg(1, "E", "unsetCallbackListener error :" + e.toString());
            }
        }
    }

    private ISvcCallback mSvcCallbackStub = new ISvcCallback.Stub() {
        @Override
        public void cbWifiStatusChanged() throws RemoteException {
            mUtil.addDebugMsg(1, "I","cbWifiStatusChanged entered");
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    FileManager.setRemoteTabEnabled(mGp);
                }
            });
        }
    };

    @Override
	protected void onPause() {
		super.onPause();
		mUtil.addDebugMsg(1, "I","onPause entered, enableKill="+mIsApplicationTerminate+
				", getChangingConfigurations="+String.format("0x%08x", getChangingConfigurations()));
	}

	@Override
	protected void onStop() {
		super.onStop();
		mUtil.addDebugMsg(1, "I","onStop entered, enableKill="+mIsApplicationTerminate);
		if (!isUiEnabled()) setActivityInBackground();
    }
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mUtil.addDebugMsg(1, "I","onDestroy entered, enableKill="+mIsApplicationTerminate+
				", getChangingConfigurations="+String.format("0x%08x", getChangingConfigurations()));
        if (!isUiEnabled()) stopService();
        closeService();
        unsetCallbackListener();
        unregisterReceiver(mUsbReceiver);
	}

    private void closeService() {
        mUtil.addDebugMsg(1,"I",CommonUtilities.getExecutedMethodName()+" entered, conn="+mGp.svcConnection);
        if (mGp.svcConnection !=null) {
            unbindService(mGp.svcConnection);
        }
    };

    private void setActivityInBackground() {
        try {
            mGp.svcClient.aidlSetActivityInBackground();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void setActivityInForeground() {
        try {
            mGp.svcClient.aidlSetActivityInForeground();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void stopService() {
        try {
            mGp.svcClient.aidlStopService();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
	public void onConfigurationChanged(final Configuration newConfig) {
	    // Ignore orientation change to keep activity from restarting
	    super.onConfigurationChanged(newConfig);
	    mUtil.addDebugMsg(1,"I","onConfigurationChanged Entered, "+"orientation="+newConfig.orientation);

        mFileMgr.setSpinnerSelectionEnabled(false);

        ViewSaveArea vsa=new ViewSaveArea();
	    saveViewStatus(vsa);
		setContentView(R.layout.main);
		createTabAndView() ;

		restoreViewStatus(vsa);

        mFileMgr.setPasteItemList();
		switchTab(mGp.currentTabName);

        mFileMgr.setLocalDirBtnListener();
        mFileMgr.setRemoteDirBtnListener();
        mGp.localFileListDirSpinner.setSelection(vsa.local_spinner_pos, false);
        mGp.remoteFileListDirSpinner.setSelection(vsa.remote_spinner_pos, false);
        mFileMgr.setLocalFilelistItemClickListener();
        mFileMgr.setLocalFilelistLongClickListener();
        mFileMgr.setRemoteFilelistItemClickListener();
        mFileMgr.setRemoteFilelistLongClickListener();
        mFileMgr.setLocalContextButtonListener();
        mFileMgr.setEmptyFolderView();
		
		refreshOptionMenu();

		Handler hndl=new Handler();
		hndl.postDelayed(new Runnable(){
		    @Override
            public void run() {
                mFileMgr.setSpinnerSelectionEnabled(true);
            }
        },100);
	}
	
	private void saveViewStatus(ViewSaveArea vsa) {
		if (mGp.currentTabName.equals(SMBEXPLORER_TAB_LOCAL)) {
			if (mGp.localProgressView.getVisibility()!=LinearLayout.GONE) {
				if (mGp.localProgressView !=null) vsa.progressVisible=mGp.localProgressView.getVisibility();
				if (mGp.progressCancelBtn!=null) vsa.progressCancelBtnText=mGp.progressCancelBtn.getText().toString();
				if (mGp.progressMsgView!=null) vsa.progressMsgText=mGp.progressMsgView.getText().toString();
			}
			if (mGp.localDialogView.getVisibility()!=LinearLayout.GONE) {
				if (mGp.localDialogView !=null) vsa.dialogVisible=mGp.localDialogView.getVisibility();
				if (mGp.dialogMsgView!=null) vsa.dialogMsgText=mGp.dialogMsgView.getText().toString();
			}
		} else if (mGp.currentTabName.equals(SMBEXPLORER_TAB_REMOTE)) {
			if (mGp.remoteProgressView.getVisibility()!=LinearLayout.GONE) {
				if (mGp.remoteProgressView !=null) vsa.progressVisible=mGp.remoteProgressView.getVisibility();
				if (mGp.progressCancelBtn!=null) vsa.progressCancelBtnText=mGp.progressCancelBtn.getText().toString();
				if (mGp.progressMsgView!=null) vsa.progressMsgText=mGp.progressMsgView.getText().toString();
			}
			if (mGp.remoteDialogView.getVisibility()!=LinearLayout.GONE) {
				if (mGp.remoteDialogView !=null) vsa.dialogVisible=mGp.remoteDialogView.getVisibility();
				if (mGp.dialogMsgView!=null) vsa.dialogMsgText=mGp.dialogMsgView.getText().toString();
			}
		}
		vsa.lclPos=mGp.localFileListView.getFirstVisiblePosition();
		if (mGp.localFileListView.getChildAt(0)!=null) vsa.lclPosTop=mGp.localFileListView.getChildAt(0).getTop();
		vsa.remPos=mGp.remoteFileListView.getFirstVisiblePosition();
		if (mGp.remoteFileListView.getChildAt(0)!=null) vsa.remPosTop=mGp.remoteFileListView.getChildAt(0).getTop();

		if (mGp.localFileListAdapter!=null) vsa.local_file_list=mGp.localFileListAdapter.getDataList();
        if (mGp.remoteFileListAdapter!=null) vsa.remote_file_list=mGp.remoteFileListAdapter.getDataList();

        vsa.local_spinner_pos=mGp.localFileListDirSpinner.getSelectedItemPosition();
        vsa.remote_spinner_pos=mGp.remoteFileListDirSpinner.getSelectedItemPosition();

        vsa.local_file_path=mGp.localFileListPath.getText().toString();
        vsa.remote_file_path=mGp.remoteFileListPath.getText().toString();
	}
	
	private void restoreViewStatus(ViewSaveArea vsa) {
		if (mGp.currentTabName.equals(SMBEXPLORER_TAB_LOCAL)) {
			if (vsa.progressVisible!=LinearLayout.GONE) {
                mFileMgr.showLocalProgressView();
			}
			mGp.progressMsgView=mGp.localProgressMsg;
			mGp.progressCancelBtn=mGp.localProgressCancel;
			mGp.progressCancelBtn.setEnabled(true);
			mGp.progressCancelBtn.setOnClickListener(mGp.progressOnClickListener);
			mGp.progressCancelBtn.setText(vsa.progressCancelBtnText);
			mGp.progressMsgView.setText(vsa.progressMsgText);
			if (vsa.dialogVisible!=LinearLayout.GONE) {
                mFileMgr.showLocalDialogView();
                mFileMgr.showDialogMsg(mGp.dialogMsgCat,vsa.dialogMsgText,"");
			}
		} else if (mGp.currentTabName.equals(SMBEXPLORER_TAB_REMOTE)) {
			if (vsa.progressVisible!=LinearLayout.GONE) {
                mFileMgr.showRemoteProgressView();
				mGp.progressMsgView=mGp.remoteProgressMsg;
				mGp.progressCancelBtn=mGp.remoteProgressCancel;
				mGp.progressCancelBtn.setEnabled(true);
				mGp.progressCancelBtn.setOnClickListener(mGp.progressOnClickListener);
				mGp.progressCancelBtn.setText(vsa.progressCancelBtnText);
				mGp.progressMsgView.setText(vsa.progressMsgText);
			}
			if (vsa.dialogVisible!=LinearLayout.GONE) {
                mFileMgr.showRemoteDialogView();
                mFileMgr.showDialogMsg(mGp.dialogMsgCat,vsa.dialogMsgText,"");
			}
		}
		if (mGp.progressCancelBtn!=null) mGp.progressCancelBtn.setOnClickListener(mGp.progressOnClickListener);

        mGp.localFileListAdapter.setDataList(vsa.local_file_list);
        mGp.localFileListView.setAdapter(mGp.localFileListAdapter);
        mGp.localFileListView.setSelectionFromTop(vsa.lclPos, vsa.lclPosTop);

        mGp.remoteFileListAdapter.setDataList(vsa.remote_file_list);
        mGp.remoteFileListView.setAdapter(mGp.remoteFileListAdapter);
        mGp.remoteFileListView.setSelectionFromTop(vsa.remPos, vsa.remPosTop);

        mGp.localFileListPath.setText(vsa.local_file_path);
        mGp.remoteFileListPath.setText(vsa.remote_file_path);

        mFileMgr.setLocalContextButtonListener();
        mFileMgr.setLocalContextButtonStatus();

        mFileMgr.setRemoteContextButtonListener();
        mFileMgr.setRemoteContextButtonStatus();
    }
	
	private void refreshOptionMenu() {
		if (Build.VERSION.SDK_INT>=11)
			mActivity.invalidateOptionsMenu();
	}

	private void switchTab(String tab_name) {
        if (tab_name.equals(SMBEXPLORER_TAB_REMOTE)) {
            mGp.tabHost.setCurrentTabByTag(tab_name);
        } else if (tab_name.equals(SMBEXPLORER_TAB_LOCAL)) {
            mGp.tabHost.setCurrentTabByTag(tab_name);
        }
    }

	private void createTabAndView() {
		mGp.themeColorList = ThemeUtil.getThemeColorList(mActivity);
//        getWindow().setNavigationBarColor(mGp.themeColorList.window_background_color_content);

        mGp.tabHost =(TabHost)findViewById(android.R.id.tabhost);
        mGp.tabHost.setup();
        mGp.tabWidget = (TabWidget) findViewById(android.R.id.tabs);
        mGp.tabWidget.setStripEnabled(false);
        mGp.tabWidget.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE);

        LinearLayout main_view=(LinearLayout)findViewById(R.id.main_screen_view);
        main_view.setBackgroundColor(Color.BLACK);//mGp.themeColorList.window_background_color_content);

        CustomTabContentView tabLocal = new CustomTabContentView(this, SMBEXPLORER_TAB_LOCAL);
        mGp.tabHost.addTab(mGp.tabHost.newTabSpec(SMBEXPLORER_TAB_LOCAL).setIndicator(tabLocal).setContent(android.R.id.tabcontent));

        CustomTabContentView tabRemote = new CustomTabContentView(this, SMBEXPLORER_TAB_REMOTE);
        mGp.tabHost.addTab(mGp.tabHost.newTabSpec(SMBEXPLORER_TAB_REMOTE).setIndicator(tabRemote).setContent(android.R.id.tabcontent));

        mGp.tabHost.setOnTabChangedListener(new OnTabChange());

        LayoutInflater vi = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mGp.mLocalView=(LinearLayout)vi.inflate(R.layout.main_local_tab, null);
        mGp.mRemoteView=(LinearLayout)vi.inflate(R.layout.main_remote_tab, null);

        mFileMgr.createView();

        mMainViewPager=(CustomViewPager)findViewById(R.id.main_screen_pager);
        mMainViewPagerAdapter=new CustomViewPagerAdapter(this, new View[]{mGp.mLocalView, mGp.mRemoteView});

        mMainViewPager.setBackgroundColor(mGp.themeColorList.window_background_color_content);
        mMainViewPager.setAdapter(mMainViewPagerAdapter);
        mMainViewPager.setOnPageChangeListener(new MainPageChangeListener());
        if (restartStatus==0) {
            mGp.tabHost.setCurrentTabByTag(SMBEXPLORER_TAB_LOCAL);
            mMainViewPager.setCurrentItem(SMBEXPLORER_TAB_POS_LOCAL);
        }

        mGp.mainPasteListClearBtn=(Button)findViewById(R.id.explorer_filelist_paste_clear);
        mGp.mainPasteListClearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mFileMgr.clearPasteItemList();
            }
        });
	}

	class OnTabChange implements TabHost.OnTabChangeListener {
		@Override
		public void onTabChanged(String tabId){
			mUtil.addDebugMsg(1, "I","onTabchanged entered. tab="+tabId);
            mGp.currentTabName=tabId;
            mMainViewPager.setCurrentItem(mGp.tabHost.getCurrentTab());
//			if (mGp.currentTabName.equals(SMBEXPLORER_TAB_LOCAL)) refreshFileListView();
            mFileMgr.setFileListPathName(mGp.localFileListPath,mGp.localBase,mGp.localDir);
            mFileMgr.setFileListPathName(mGp.remoteFileListPath,mGp.remoteBase,mGp.remoteDir);
            mFileMgr.setPasteButtonEnabled();
		};
	}

    private class MainPageChangeListener implements ViewPager.OnPageChangeListener {
        @Override
        public void onPageSelected(int position) {
            mUtil.addDebugMsg(1,"I","onPageSelected entered, pos="+position);
            mGp.tabWidget.setCurrentTab(position);
            mGp.tabHost.setCurrentTab(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {
//	    	mUtil.addDebugMsg(1,"I","onPageScrollStateChanged entered, state="+state);
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
//	    	mUtil.addDebugMsg(1, "I","onPageScrolled entered, pos="+position);
        }
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
//		mUtil.addDebugMsg(1, "I","onCreateOptionsMenu entered");
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_top, menu);
		return true;
	}

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
//    	mUtil.addDebugMsg(1, "I","onPrepareOptionsMenu entered");
    	super.onPrepareOptionsMenu(menu);
        File lf=new File(mGp.getLogDirName()+"/"+mGp.getLogFileName()+".txt");
    	if (lf.exists()) menu.findItem(R.id.menu_top_view_log_file).setVisible(true);
        else menu.findItem(R.id.menu_top_view_log_file).setVisible(false);
    	if (isUiEnabled()) {
    		menu.findItem(R.id.menu_top_export).setEnabled(true);
    		menu.findItem(R.id.menu_top_import).setEnabled(true);
    		menu.findItem(R.id.menu_top_settings).setEnabled(true);
            menu.findItem(R.id.menu_top_edit_smb_server).setEnabled(true);
            menu.findItem(R.id.menu_top_refresh).setEnabled(true);
    	} else {
    		menu.findItem(R.id.menu_top_export).setEnabled(false);
    		menu.findItem(R.id.menu_top_import).setEnabled(false);
    		menu.findItem(R.id.menu_top_settings).setEnabled(false);
            menu.findItem(R.id.menu_top_edit_smb_server).setEnabled(false);
            menu.findItem(R.id.menu_top_refresh).setEnabled(false);
    	}
        return true;
    }

    private final int REQUEST_PERMISSIONS_WRITE_EXTERNAL_STORAGE = 1;
    private void checkRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            mUtil.addDebugMsg(1, "I", "Prermission WriteExternalStorage=" + checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) +
                    ", WakeLock=" + checkSelfPermission(Manifest.permission.WAKE_LOCK)
            );
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                NotifyEvent ntfy = new NotifyEvent(mContext);
                ntfy.setListener(new NotifyEvent.NotifyEventListener() {
                    @Override
                    public void positiveResponse(Context c, Object[] o) {
                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                REQUEST_PERMISSIONS_WRITE_EXTERNAL_STORAGE);
                    }

                    @Override
                    public void negativeResponse(Context c, Object[] o) {
                        NotifyEvent ntfy_term = new NotifyEvent(mContext);
                        ntfy_term.setListener(new NotifyEvent.NotifyEventListener() {
                            @Override
                            public void positiveResponse(Context c, Object[] o) {
//                                isTaskTermination = true;
                                finish();
                            }

                            @Override
                            public void negativeResponse(Context c, Object[] o) {
                            }
                        });
                        mGp.commonDlg.showCommonDialog(false, "W",
                                mContext.getString(R.string.msgs_main_permission_external_storage_title),
                                mContext.getString(R.string.msgs_main_permission_external_storage_denied_msg), ntfy_term);
                    }
                });
                mGp.commonDlg.showCommonDialog(false, "W",
                        mContext.getString(R.string.msgs_main_permission_external_storage_title),
                        mContext.getString(R.string.msgs_main_permission_external_storage_request_msg), ntfy);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (REQUEST_PERMISSIONS_WRITE_EXTERNAL_STORAGE == requestCode) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                NotifyEvent ntfy_term = new NotifyEvent(mContext);
                ntfy_term.setListener(new NotifyEvent.NotifyEventListener() {
                    @Override
                    public void positiveResponse(Context c, Object[] o) {
//                        isTaskTermination = true;
                        finish();
                    }

                    @Override
                    public void negativeResponse(Context c, Object[] o) {
                    }
                });
                mGp.commonDlg.showCommonDialog(false, "W",
                        mContext.getString(R.string.msgs_main_permission_external_storage_title),
                        mContext.getString(R.string.msgs_main_permission_external_storage_denied_msg), ntfy_term);
            }
        }
    }

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		mUtil.addDebugMsg(1, "I","onOptionsItemSelected entered");
		switch (item.getItemId()) {
			case R.id.menu_top_export:
                SmbServerUtil.exportSmbServerConfigListDlg(mGp, mGp.internalRootDirectory, SMBEXPLORER_PROFILE_NAME);
				return true;
			case R.id.menu_top_import:
                SmbServerUtil.importSmbServerConfigDlg(mGp, mGp.internalRootDirectory, SMBEXPLORER_PROFILE_NAME);
				return true;
            case R.id.menu_top_show_storage_picker:
                openStorageSelector(REQUEST_CODE_STORAGE_ACCESS);
                return true;
			case R.id.menu_top_settings:
				invokeSettingsActivity();
				return true;
			case R.id.menu_top_quit:
				confirmTerminateApplication();
				return true;
            case R.id.menu_top_edit_smb_server:
                SmbServerListEditor sm=new SmbServerListEditor(mActivity, mGp);
                return true;
            case R.id.menu_top_view_log_file:
                mUtil.flushLog();
                FileListItem fi=new FileListItem(mGp.getLogFileName()+".txt",false,0,0,false,mGp.getLogDirName());
                mFileMgr.startLocalFileViewerIntent(fi,"text/plain");
                return true;
            case R.id.menu_top_refresh:
//                sendMagicPacket("08:bd:43:f6:48:2a", "255.255.255.255");
                mFileMgr.refreshFileListView();
                return true;
            case R.id.menu_top_log_management:
                invokeLogManagement();
                return true;

		}
		return false;
	}

    private void invokeLogManagement() {
        NotifyEvent ntfy=new NotifyEvent(mContext);
        ntfy.setListener(new NotifyEvent.NotifyEventListener(){
            @Override
            public void positiveResponse(Context c, Object[] o) {
                mGp.setSettingOptionLogEnabled(mContext, (Boolean)o[0]);
                mUtil.resetLogReceiver();
                if (!(Boolean)o[0]) mUtil.rotateLogFile();
                Handler hndl=new Handler();
                hndl.post(new Runnable() {
                    @Override
                    public void run() {
                        mFileMgr.refreshFileListView();
                        refreshOptionMenu();
                    }
                });
            }
            @Override
            public void negativeResponse(Context c, Object[] o) {
            }
        });
        mUtil.flushLog();
        LogFileListDialogFragment lfm=
                LogFileListDialogFragment.newInstance(false, getString(R.string.msgs_log_management_title));
        lfm.showDialog(getSupportFragmentManager(), lfm, mGp, ntfy);
    };

    private void sendMagicPacket(final String target_mac, final String if_network) {
//                sendMagicPacket("08:bd:43:f6:48:2a", "192.168.200.128");
        Thread th=new Thread(){
            @Override
            public void run() {
                // Total 102byte = 6byte 0xffffffffffff + (6 byte target dev mac address)を16回繰り返す
                byte[] magicPacket=new byte[102];
                try {
                    int j=if_network.lastIndexOf(".");
                    String if_ba=if_network.substring(0,if_network.lastIndexOf("."))+".255";
                    InetAddress broadcastIpAddress = InetAddress.getByName(if_ba);//.getByAddress(new byte[]{-1,-1,-1,-1});

                    byte[] targetMacAddress=new byte[6];
                    String[] m_array=target_mac.split(":");
                    for(int i=0;i<6;i++) targetMacAddress[i]=Integer.decode("0x"+m_array[i]).byteValue();

                    Arrays.fill(magicPacket, 0, 6, (byte)0xff);
                    for (int i=0;i<16;i++) System.arraycopy(targetMacAddress,0, magicPacket,(i*6)+6, 6);

                    DatagramPacket packet = new DatagramPacket(magicPacket, magicPacket.length, broadcastIpAddress, 9);
                    DatagramSocket socket = new DatagramSocket();
                    socket.send(packet);
                    socket.close();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        th.start();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (!mFileMgr.processBackKey()) switchToHome();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
		}
	}

	private void confirmTerminateApplication() {
		NotifyEvent ne=new NotifyEvent(this);
		ne.setListener(new NotifyEvent.NotifyEventListener() {
			@Override
			public void positiveResponse(Context c,Object[] o) {
				terminateApplication();
			}
			@Override
			public void negativeResponse(Context c,Object[] o) {}
		});
		mGp.commonDlg.showCommonDialog(true,"W",getString(R.string.msgs_terminate_confirm),"",ne);
		return;
	}

	private void switchToHome() {
		Intent in=new Intent();
		in.setAction(Intent.ACTION_MAIN);
		in.addCategory(Intent.CATEGORY_HOME);
		in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(in);
	}
	
	private void terminateApplication() {
        mIsApplicationTerminate = true; // exit cleanly
//		moveTaskToBack(true);
        finish();
    }

	private void applySettingParms() {
        boolean p_log_enabled=mGp.settingLogOption;
		mGp.loadSettingsParm(mContext);
		if (p_log_enabled!=mGp.settingLogOption) {
//		    if (mGp.settingLogOption) mUtil.resetLogReceiver();
            mUtil.resetLogReceiver();
        }
		refreshOptionMenu();
	}
	
	private boolean mUiEnabled=true;

	public boolean isUiEnabled() {
		return mUiEnabled;
	}

	public void setUiEnabled(boolean enabled) {
		if ((enabled && mUiEnabled) || (!enabled && !mUiEnabled)) return;
		mUiEnabled=enabled;

		mGp.tabWidget.setEnabled(enabled);
        mGp.localFileListDirSpinner.setEnabled(enabled);
        mGp.remoteFileListDirSpinner.setEnabled(enabled);
        mGp.remoteFileListView.setEnabled(enabled);
        mGp.localFileListView.setEnabled(enabled);
		if (enabled) {
            mGp.remoteFileListView.setVisibility(ListView.VISIBLE);
            mGp.localFileListView.setVisibility(ListView.VISIBLE);
            mFileMgr.setPasteButtonEnabled();
		} else {
            mGp.remoteFileListView.setVisibility(ListView.INVISIBLE);
            mGp.localFileListView.setVisibility(ListView.INVISIBLE);
		}
        mGp.localFileListUpBtn.setClickable(enabled);
        mGp.localFileListTopBtn.setClickable(enabled);
        mGp.remoteFileListUpBtn.setClickable(enabled);
        mGp.remoteFileListTopBtn.setClickable(enabled);

		refreshOptionMenu();
	}

	private void invokeSettingsActivity() {
		Intent intent = new Intent(this, MainSetting.class);
		startActivityForResult(intent,0);
	}

	private final int REQUEST_CODE_STORAGE_ACCESS=40;

	private void openStorageSelector(int request_code) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, request_code);
    }
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE_STORAGE_ACCESS) {
	        if (resultCode == Activity.RESULT_OK) {
                mUtil.addDebugMsg(1,"I","Storage picker action="+data.getAction());
	            if (mGp.safMgr.isUsbUuid(SafManager.getUuidFromUri(data.getData().toString()))) {
                    if (mGp.safMgr.isRootTreeUri(data.getData())) {
                        mGp.safMgr.addUsbUuid(data.getData());
                        mGp.safMgr.loadSafFile();
                        mFileMgr.updateLocalDirSpinner();
                    } else {
                        NotifyEvent ntfy=new NotifyEvent(mContext);
                        ntfy.setListener(new NotifyEvent.NotifyEventListener() {
                            @Override
                            public void positiveResponse(Context context, Object[] objects) {
                                openStorageSelector(REQUEST_CODE_STORAGE_ACCESS);
                            }
                            @Override
                            public void negativeResponse(Context context, Object[] objects) {}
                        });
                        mGp.commonDlg.showCommonDialog(true, "W", "USBのルートディレクトリーが選択されていません、選択しなおしますか?","",ntfy);
                    }
                } else {
                    if (mGp.safMgr.isRootTreeUri(data.getData())) {
                        mGp.safMgr.addSdcardUuid(data.getData());
                        mGp.safMgr.loadSafFile();
                        mFileMgr.updateLocalDirSpinner();
                    } else {
                        NotifyEvent ntfy=new NotifyEvent(mContext);
                        ntfy.setListener(new NotifyEvent.NotifyEventListener() {
                            @Override
                            public void positiveResponse(Context context, Object[] objects) {
                                openStorageSelector(REQUEST_CODE_STORAGE_ACCESS);
                            }
                            @Override
                            public void negativeResponse(Context context, Object[] objects) {}
                        });
                        mGp.commonDlg.showCommonDialog(true, "W", "SDCARDのルートディレクトリーが選択されていません、選択しなおしますか?","",ntfy);
                    }
                }
	        }
	    } else if (requestCode == 0) {
	    	applySettingParms();
	    }
	}

    public class CustomTabContentView extends FrameLayout {
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        public CustomTabContentView(Context context) {
            super(context);
        }
        public CustomTabContentView(Context context, String title) {
            this(context);
            View childview1 = inflater.inflate(R.layout.tab_widget1, null);
            TextView tv1 = (TextView) childview1.findViewById(R.id.tab_widget1_textview);
            tv1.setText(title);
            addView(childview1);
        }
    }

	private MyUncaughtExceptionHandler myUncaughtExceptionHandler = new MyUncaughtExceptionHandler();
	private class MyUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
		private boolean mCrashing=false;
	    private Thread.UncaughtExceptionHandler defaultUEH;
		public void init() {
			defaultUEH = Thread.currentThread().getUncaughtExceptionHandler();
	        Thread.currentThread().setUncaughtExceptionHandler(myUncaughtExceptionHandler);
		}
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
        	try {
                if (!mCrashing) {
                    mCrashing = true;
                    
                	StackTraceElement[] st=ex.getStackTrace();
                	String st_msg="";
                	for (int i=0;i<st.length;i++) {
                		st_msg+="\n at "+st[i].getClassName()+"."+
                				st[i].getMethodName()+"("+st[i].getFileName()+
                				":"+st[i].getLineNumber()+")";
                	}
        			String end_msg=ex.toString()+st_msg;
        			
        			String end_msg2="";
        			st_msg="";
        			Throwable cause=ex.getCause();
        			if (cause!=null) {
            			st=cause.getStackTrace();
            			if (st!=null) {
                        	for (int i=0;i<st.length;i++) {
                        		st_msg+="\n at "+st[i].getClassName()+"."+
                        				st[i].getMethodName()+"("+st[i].getFileName()+
                        				":"+st[i].getLineNumber()+")";
                        	}
                			end_msg2="Caused by:"+cause.toString()+st_msg;
            			}
        			}

        			mUtil.addLogMsg("E", end_msg);
        			if (!end_msg2.equals("")) mUtil.addLogMsg("E", end_msg2);
        			
                }
            } finally {
                defaultUEH.uncaughtException(thread, ex);
            }
        }
    };

    final private class UsbReceiver extends BroadcastReceiver {
        @SuppressLint({"Wakelock", "NewApi"})
        @Override
        final public void onReceive(Context c, final Intent in) {
            String action = in.getAction();
            mUtil.addDebugMsg(1,"I","Usb device action="+in.getAction());
            int delay=0;
            Handler hndl=new Handler();
            hndl.postDelayed(new Runnable(){
                @Override
                public void run() {
                    if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
//                        UsbManager um=(UsbManager)mContext.getSystemService(UsbManager.class);
//                        HashMap<String, UsbDevice> dl=um.getDeviceList();
//                        String kl="";
//                        for(String name : dl.keySet()){
//                            kl += name + "\n";
//                            Log.v("","key="+name+", v="+dl.get(name));
//                        }
//                        Log.v("","kl="+kl);
                        UsbDevice device = (UsbDevice) in.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        mUtil.addDebugMsg(1,"I",device.toString());
                        boolean success=false;
                        for(int i=0;i<10;i++) {
                            mGp.safMgr.loadSafFile();
                            if (mGp.safMgr.isUsbMounted()) {
                                mFileMgr.updateLocalDirSpinner();
                                Toast.makeText(c,"USB flash memory attach process was successfull", Toast.LENGTH_SHORT).show();
                                success=true;
                                break;
                            }
                            SystemClock.sleep(500);
                        }
                        if (!success) {
                            NotifyEvent ntfy=new NotifyEvent(mContext);
                            ntfy.setListener(new NotifyEvent.NotifyEventListener() {
                                @Override
                                public void positiveResponse(Context context, Object[] objects) {
                                    openStorageSelector(REQUEST_CODE_STORAGE_ACCESS);
                                }
                                @Override
                                public void negativeResponse(Context context, Object[] objects) {}
                            });
                            mGp.commonDlg.showCommonDialog(true,"I",
                                    "USB Flashメモリーが取り付けられましたが認識できませんでした。ストレージ設定を表示しますか?","",ntfy);
                        }
                    } else {
                        mGp.safMgr.loadSafFile();
                        mFileMgr.updateLocalDirSpinner();
                        Toast.makeText(c,"USB flash memory detach process was successfull", Toast.LENGTH_SHORT).show();
                    }
                    mUtil.addDebugMsg(1,"I","Usb list="+getRemovableStoragePaths(c,true));
                }
            },500);
        }
    }

    private String getRemovableStoragePaths(Context context, boolean debug) {
        String mpi="";
        ArrayList<String> paths = new ArrayList<String>();
        try {
            StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            Method getVolumeList = sm.getClass().getDeclaredMethod("getVolumeList");
            Object[] volumeList = (Object[]) getVolumeList.invoke(sm);
            for (Object volume : volumeList) {
                Method getPath = volume.getClass().getDeclaredMethod("getPath");
//	            Method isRemovable = volume.getClass().getDeclaredMethod("isRemovable");
                Method getUuid = volume.getClass().getDeclaredMethod("getUuid");
                Method toString = volume.getClass().getDeclaredMethod("toString");
                Method getId = volume.getClass().getDeclaredMethod("getId");
                String path = (String) getPath.invoke(volume);
//	            boolean removable = (Boolean)isRemovable.invoke(volume);
                Method getLabel = volume.getClass().getDeclaredMethod("getUserLabel");
                String label=(String) getLabel.invoke(volume)+"\n";
                String aa=(String) toString.invoke(volume)+"\n";
                mpi+="getId="+((String) getId.invoke(volume))+"\n";
                mpi+=(String) toString.invoke(volume)+"\n";
//	            if ((String)getUuid.invoke(volume)!=null) {
//	            	paths.add(path);
//					if (debug) {
////						Log.v(APPLICATION_TAG, "RemovableStorages Uuid="+(String)getUuid.invoke(volume)+", removable="+removable+", path="+path);
//						mUtil.addLogMsg("I", (String)toString.invoke(volume));
//					}
//	            }
            }
        } catch (ClassCastException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return mpi;
    }

    private class ViewSaveArea {
        private static final long serialVersionUID = 1L;
        public int progressVisible= LinearLayout.GONE;
        public String progressCancelBtnText="";
        public String progressMsgText="";
        public int profPos,profPosTop=0,lclPos,lclPosTop=0,remPos=0,remPosTop=0;

        public int local_spinner_pos=0;
        public int remote_spinner_pos=0;

        public ArrayList<FileListItem>local_file_list=null;
        public String local_file_path="";
        public ArrayList<FileListItem>remote_file_list=null;
        public String remote_file_path="";

        public int dialogVisible=LinearLayout.GONE;
        public String dialogMsgText="";

    }
}

