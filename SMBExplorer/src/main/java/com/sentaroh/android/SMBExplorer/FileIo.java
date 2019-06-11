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

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import com.sentaroh.android.SMBExplorer.Log.LogUtil;
import com.sentaroh.android.Utilities.NotifyEvent;
import com.sentaroh.android.Utilities.SafFile;
import com.sentaroh.android.Utilities.ThreadCtrl;
import com.sentaroh.jcifs.JcifsAuth;
import com.sentaroh.jcifs.JcifsException;
import com.sentaroh.jcifs.JcifsFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.util.ArrayList;

import static com.sentaroh.android.SMBExplorer.Constants.APP_SPECIFIC_DIRECTORY;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_COPY_LOCAL_TO_LOCAL;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_COPY_LOCAL_TO_REMOTE;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_COPY_REMOTE_TO_LOCAL;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_COPY_REMOTE_TO_REMOTE;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_DOWLOAD_REMOTE_FILE;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_LOCAL_CREATE;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_LOCAL_DELETE;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_LOCAL_RENAME;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_MOVE_LOCAL_TO_LOCAL;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_MOVE_LOCAL_TO_REMOTE;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_MOVE_REMOTE_TO_LOCAL;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_MOVE_REMOTE_TO_REMOTE;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_REMOTE_CREATE;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_REMOTE_DELETE;
import static com.sentaroh.android.SMBExplorer.Constants.FILEIO_PARM_REMOTE_RENAME;

public class FileIo extends Thread {
	private final static String DEBUG_TAG = "SMBExplorer";
	private NotifyEvent notifyEvent ;
	private ThreadCtrl fileioThreadCtrl;
	private ArrayList<FileIoLinkParm> fileioLinkParm;
	private int file_op_cd;
	private Context mContext=null;
	private Handler uiHandler = new Handler() ;
	private GlobalParameters mGp=null;
	private final static String mAppPackageName="com.sentaroh.android.SMBExplorer";
	private LogUtil mLogUtil=null;
	
	// @Override
	public FileIo(GlobalParameters gp, int op_cd,
			ArrayList<FileIoLinkParm> alp, ThreadCtrl tc, NotifyEvent ne, Context cc, String lmp) {
		
		mGp=GlobalWorkArea.getGlobalParameters(cc);
		fileioThreadCtrl=tc;
		file_op_cd=op_cd;
		fileioLinkParm=alp;
		notifyEvent=ne;
		
		mContext=cc;

		mLogUtil=new LogUtil(mContext, "FILEIO", mGp);

	};
	
    private long taskBeginTime=0;
    
	@Override
	public void run() {
		sendLogMsg("I","Task has started.");
		
		final WakeLock wake_lock=((PowerManager)mContext.getSystemService(Context.POWER_SERVICE))
	    			.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
//	   	    				| PowerManager.ON_AFTER_RELEASE
	    				, "SMBExplorer-ScreenOn");
		final WifiLock wifi_lock=((WifiManager)mContext.getSystemService(Context.WIFI_SERVICE))
				.createWifiLock(WifiManager.WIFI_MODE_FULL, "SMBExplorer-wifi");
		
		if (mGp.fileIoWakeLockRequired) wake_lock.acquire();
		if (mGp.fileIoWifiLockRequired) wifi_lock.acquire();

		try {
			taskBeginTime=System.currentTimeMillis();
			
			boolean fileioTaskResultOk=false;
			for (int i=0;i<fileioLinkParm.size();i++) {
				fileioTaskResultOk=fileOperation(fileioLinkParm.get(i));
				if (!fileioTaskResultOk) 
					break;
			}
			sendLogMsg("I","Task was ended. fileioTaskResultOk=",fileioTaskResultOk+ ", fileioThreadCtrl:",fileioThreadCtrl.toString());
			sendLogMsg("I","Task elapsed time="+(System.currentTimeMillis()-taskBeginTime));
			if (fileioTaskResultOk) {
				fileioThreadCtrl.setThreadResultSuccess();
				sendDebugLogMsg(1,"I","Task was endeded without error.");			
			} else if (fileioThreadCtrl.isEnabled()) {
				fileioThreadCtrl.setThreadResultError();
				sendLogMsg("W","Task was ended with error.");
			} else {
				fileioThreadCtrl.setThreadResultCancelled();
				sendLogMsg("W","Task was cancelled.");
			}
			fileioThreadCtrl.setDisabled();

			uiHandler.post(new Runnable() {// UI thread
				@Override
				public void run() {
					notifyEvent.notifyToListener(true, null);
				}
			});		
		} finally {
			if (wake_lock.isHeld()) wake_lock.release();
			if (wifi_lock.isHeld()) wifi_lock.release();
		}
	}

	private JcifsAuth createJcifsAuth(String smb_level, String domain, String user, String pass, boolean ipc_sign_enforce, boolean use_smb2_nego) {
	    JcifsAuth auth=null;
        if (Integer.parseInt(smb_level)==JcifsAuth.JCIFS_FILE_SMB1) auth = new JcifsAuth(Integer.parseInt(smb_level), domain, user, pass);
        else auth=new JcifsAuth(Integer.parseInt(smb_level), domain, user, pass, ipc_sign_enforce, use_smb2_nego);
        return auth;
    }

	private boolean fileOperation(FileIoLinkParm fiop) {
		sendDebugLogMsg(1,"I","FILEIO task invoked.",
                " fromUrl=",fiop.getFromDirectory(), ", fromName=",fiop.getFromName(),", fromBaseUrl=",fiop.getFromBaseDirectory(),", fromSmbLebel=",fiop.getFromSmbLevel(),", fromUser=",fiop.getFromUser(),
                ", FromIPC="+fiop.isFromSmbOptionIpcSignEnforce()+", FromSMB2Nego="+fiop.isFromSmbOptionUseSMB2Negotiation()+
                ", toUrl=",fiop.getToDirectory(), ", toName=",fiop.getToName(), ", toBaseUrl=",fiop.getToBaseDirectory(),", toSmbLebel=",fiop.getToSmbLevel(),", toUser=",fiop.getToUser()+
                ", ToIPC="+fiop.isToSmbOptionIpcSignEnforce()+", ToSMB2Nego="+fiop.isToSmbOptionUseSMB2Negotiation());

		boolean result=false;
        JcifsAuth smb_auth_from =null, smb_auth_to =null;
		switch (file_op_cd) {
			case FILEIO_PARM_LOCAL_CREATE:
				result=createLocalDir(fiop.getToDirectory()+"/"+fiop.getToName());
				break;
			case FILEIO_PARM_REMOTE_CREATE:
                smb_auth_to =createJcifsAuth(fiop.getToSmbLevel(), fiop.getToDomain(), fiop.getToUser(), fiop.getToPass(), fiop.isToSmbOptionIpcSignEnforce(), fiop.isToSmbOptionUseSMB2Negotiation());
                result=createRemoteDir(smb_auth_to, fiop.getToDirectory()+"/"+fiop.getToName());
				break;
			case FILEIO_PARM_LOCAL_RENAME:
				result=renameLocalItem(fiop.getFromDirectory()+"/"+fiop.getFromName(),  fiop.getToDirectory()+"/"+fiop.getToName());
				break;
			case FILEIO_PARM_REMOTE_RENAME:
                smb_auth_to =createJcifsAuth(fiop.getToSmbLevel(), fiop.getToDomain(), fiop.getToUser(), fiop.getToPass(), fiop.isToSmbOptionIpcSignEnforce(), fiop.isToSmbOptionUseSMB2Negotiation());
				result=renameRemoteItem(smb_auth_to, fiop.getFromDirectory()+"/"+fiop.getFromName()+"/", fiop.getToDirectory()+"/"+fiop.getToName()+"/");
				break;
			case FILEIO_PARM_LOCAL_DELETE:
				result=deleteLocalItem(fiop.getToDirectory()+"/"+fiop.getToName());
				break;
			case FILEIO_PARM_REMOTE_DELETE:
                smb_auth_to =createJcifsAuth(fiop.getToSmbLevel(), fiop.getToDomain(), fiop.getToUser(), fiop.getToPass(), fiop.isToSmbOptionIpcSignEnforce(), fiop.isToSmbOptionUseSMB2Negotiation());
				result=deleteRemoteItem(smb_auth_to, fiop.getToDirectory()+"/"+fiop.getToName()+"/");
				break;
			case FILEIO_PARM_COPY_REMOTE_TO_LOCAL:
                smb_auth_from =createJcifsAuth(fiop.getFromSmbLevel(), fiop.getFromDomain(), fiop.getFromUser(), fiop.getFromPass(), fiop.isFromSmbOptionIpcSignEnforce(), fiop.isFromSmbOptionUseSMB2Negotiation());
				result=copyRemoteToLocal(smb_auth_from, fiop.getFromDirectory()+"/"+fiop.getFromName(), fiop.getToDirectory()+"/"+fiop.getToName());
				break;
			case FILEIO_PARM_COPY_REMOTE_TO_REMOTE:
                smb_auth_from =createJcifsAuth(fiop.getFromSmbLevel(), fiop.getFromDomain(), fiop.getFromUser(), fiop.getFromPass(), fiop.isFromSmbOptionIpcSignEnforce(), fiop.isFromSmbOptionUseSMB2Negotiation());
                smb_auth_to =createJcifsAuth(fiop.getToSmbLevel(), fiop.getToDomain(), fiop.getToUser(), fiop.getToPass(), fiop.isToSmbOptionIpcSignEnforce(), fiop.isToSmbOptionUseSMB2Negotiation());
				result=copyRemoteToRemote(smb_auth_from, smb_auth_to, fiop.getFromDirectory()+"/"+fiop.getFromName(), fiop.getToDirectory()+"/"+fiop.getToName());
				break;
			case FILEIO_PARM_COPY_LOCAL_TO_LOCAL:
				result=copyLocalToLocal(fiop.getFromDirectory()+"/"+fiop.getFromName(), fiop.getToDirectory()+"/"+fiop.getToName());
				break;
			case FILEIO_PARM_COPY_LOCAL_TO_REMOTE:
                smb_auth_to =createJcifsAuth(fiop.getToSmbLevel(), fiop.getToDomain(), fiop.getToUser(), fiop.getToPass(), fiop.isToSmbOptionIpcSignEnforce(), fiop.isToSmbOptionUseSMB2Negotiation());
				result=copyLocalToRemote(smb_auth_to, fiop.getFromDirectory()+"/"+fiop.getFromName(), fiop.getToDirectory()+"/"+fiop.getToName());
				break;
			case FILEIO_PARM_MOVE_REMOTE_TO_LOCAL:
                smb_auth_from =createJcifsAuth(fiop.getFromSmbLevel(), fiop.getFromDomain(), fiop.getFromUser(), fiop.getFromPass(), fiop.isFromSmbOptionIpcSignEnforce(), fiop.isFromSmbOptionUseSMB2Negotiation());
				result=copyRemoteToLocal(smb_auth_from, fiop.getFromDirectory()+"/"+fiop.getFromName(), fiop.getToDirectory()+"/"+fiop.getToName());
				if (result) result=deleteRemoteItem(smb_auth_from, fiop.getFromDirectory()+"/"+fiop.getToName()+"/");
				break;
			case FILEIO_PARM_MOVE_REMOTE_TO_REMOTE:
                smb_auth_from =createJcifsAuth(fiop.getFromSmbLevel(), fiop.getFromDomain(), fiop.getFromUser(), fiop.getFromPass(), fiop.isFromSmbOptionIpcSignEnforce(), fiop.isFromSmbOptionUseSMB2Negotiation());
                smb_auth_to =createJcifsAuth(fiop.getToSmbLevel(), fiop.getToDomain(), fiop.getToUser(), fiop.getToPass(), fiop.isToSmbOptionIpcSignEnforce(), fiop.isToSmbOptionUseSMB2Negotiation());
				if (fiop.getFromBaseDirectory().equals(fiop.getToBaseDirectory())) {
					result= moveRemoteToRemoteByRename(smb_auth_from, smb_auth_to, fiop.getFromDirectory()+"/"+fiop.getFromName()+"/", fiop.getToDirectory()+"/"+fiop.getToName());
				} else {
					result=copyRemoteToRemote(smb_auth_from, smb_auth_to, fiop.getFromDirectory()+"/"+fiop.getFromName()+"/", fiop.getToDirectory()+"/"+fiop.getToName());
					if (result) result=deleteRemoteItem(smb_auth_from, fiop.getFromDirectory()+"/"+fiop.getFromName()+"/");
				}
				break;
			case FILEIO_PARM_MOVE_LOCAL_TO_LOCAL:
				result=moveLocalToLocal(fiop.getFromDirectory()+"/"+fiop.getFromName(), fiop.getToDirectory()+"/"+fiop.getToName());
				break;
			case FILEIO_PARM_MOVE_LOCAL_TO_REMOTE:
                smb_auth_to =createJcifsAuth(fiop.getToSmbLevel(), fiop.getToDomain(), fiop.getToUser(), fiop.getToPass(), fiop.isToSmbOptionIpcSignEnforce(), fiop.isToSmbOptionUseSMB2Negotiation());
				result=copyLocalToRemote(smb_auth_to, fiop.getFromDirectory()+"/"+fiop.getFromName(), fiop.getToDirectory()+"/"+fiop.getToName());
				if (result) result=deleteLocalItem(fiop.getFromDirectory()+"/"+fiop.getFromName());
				break;
			case FILEIO_PARM_DOWLOAD_REMOTE_FILE:
                smb_auth_from =createJcifsAuth(fiop.getFromSmbLevel(), fiop.getFromDomain(), fiop.getFromUser(), fiop.getFromPass(), fiop.isFromSmbOptionIpcSignEnforce(), fiop.isFromSmbOptionUseSMB2Negotiation());
                result=downloadRemoteFile(smb_auth_from, fiop.getFromDirectory()+"/"+fiop.getFromName(), fiop.getToDirectory()+"/"+fiop.getToName());
				break;
	
			default:
				break;
		};
		return result;
	}
	
	private static String mPrevProgMsg="";
	private void sendMsgToProgDlg(final String log_msg) {
//		if (settingDebugLevel>0) Log.v(DEBUG_TAG,"P "+msg);
		if (!mPrevProgMsg.equals(log_msg)) {
			mPrevProgMsg=log_msg;
			uiHandler.post(new Runnable() {// UI thread
				@Override
				public void run() {
					mGp.progressMsgView.setText(log_msg);
//					Log.v("","pop="+log_msg);
				}
			});
            try {
                mGp.svcClient.aidlUpdateNotificationMessage(log_msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
	}

	private void sendLogMsg(final String log_cat, final String ...log_msg) {
        mLogUtil.addLogMsg(log_cat, log_msg);
	}

	private void sendDebugLogMsg(int lvl, final String log_cat, final String ...log_msg) {

		if (mGp.settingDebugLevel>0) {
            mLogUtil.addDebugMsg(lvl, log_cat, log_msg);
		}
	}
	
	private boolean createLocalDir(String newUrl) {
    	File lf;
    	boolean result = false;
    	
    	if (!fileioThreadCtrl.isEnabled()) return false;
    	
    	sendDebugLogMsg(1,"I","Create local dir item=",newUrl);
    	
    	try {
    		result=true;
    		lf = new File( newUrl );
    		
    		if (lf.exists()) return false;

    		String saf_msg="";
            if (newUrl.startsWith(mGp.safMgr.getUsbRootPath())) {
                SafFile csf=mGp.safMgr.createUsbDirectory(newUrl);
                saf_msg=csf==null?"":csf.getLastErrorMessage();
                result=csf==null?false:true;
            } else if (Build.VERSION.SDK_INT>=21 && newUrl.startsWith(mGp.safMgr.getSdcardRootPath())) {
                SafFile csf=mGp.safMgr.createSdcardDirectory(newUrl);
                saf_msg=csf==null?"":csf.getLastErrorMessage();
                result=csf==null?false:true;
            } else result=lf.mkdir();
    		if (!result) {
    			sendLogMsg("E","Create error msg=",saf_msg);
    			fileioThreadCtrl.setThreadMessage("Create error msg="+saf_msg);
    		} else {
    			sendLogMsg("I",newUrl," was created");
    		}
		} catch (Exception e) {
			e.printStackTrace();
			sendLogMsg("E","Create error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Create error:"+e.toString());
			result=false;
			return false;
		}
    	return result;
    }

    private boolean createRemoteDir(JcifsAuth smb_auth, String newUrl) {
    	JcifsFile sf;
    	boolean result = false;
    	
    	if (!fileioThreadCtrl.isEnabled()) return false;
    	
    	sendDebugLogMsg(1,"I","Create remote dir item=",newUrl);
    	
    	try {
    		result=true;
    		sf = new JcifsFile( newUrl,smb_auth);
    		
    		if (sf.exists()) return false;
    		
    		sf.mkdir();
    		sendLogMsg("I",newUrl," was created");
		} catch (Exception e) {
			e.printStackTrace();
			sendLogMsg("E","Create error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Create error:"+e.toString());
			result=false;
			return false;
		}
    	return result;
    }
	
    private boolean renameRemoteItem(JcifsAuth smb_auth, String oldUrl, String newUrl) {
    	JcifsFile sf,sfd;
    	boolean result = false;
    	
    	if (!fileioThreadCtrl.isEnabled()) return false;
    	
    	sendDebugLogMsg(1,"I","Rename remote item=",oldUrl);
    	
    	try {
    		result=true;
    		sf = new JcifsFile( oldUrl,smb_auth );
    		sfd = new JcifsFile( newUrl,smb_auth );
    		
    		sf.renameTo(sfd);
    		sendLogMsg("I",oldUrl," was renamed to ",newUrl);
		} catch (Exception e) {
			e.printStackTrace();
			sendLogMsg("E","Rename error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Rename error:"+e.toString());
			result=false;
			return false;
		}
    	return result;
    }
    
    private boolean renameLocalItem(String oldUrl, String newUrl) {
    	File sf,sfd;
    	boolean result = false;
    	
    	if (!fileioThreadCtrl.isEnabled()) return false;
    	
    	sendDebugLogMsg(1,"I","Rename local item=",oldUrl);

        if (newUrl.startsWith(mGp.safMgr.getUsbRootPath())) {
            SafFile document=mGp.safMgr.createUsbFile(oldUrl);
            String[] new_name_array=newUrl.split("/");
            result=document.renameTo(new_name_array[new_name_array.length-1]);
            if (result) {
                sendLogMsg("I",oldUrl," was renamed to ",newUrl);
            } else {
                sendLogMsg("I","Rename was failed, from=",oldUrl," to=",newUrl,"\n",document.getLastErrorMessage());
                fileioThreadCtrl.setThreadMessage("Rename was failed, from="+oldUrl+" to="+newUrl+"\n"+document.getLastErrorMessage());
            }
        } else if (Build.VERSION.SDK_INT>=21 && newUrl.startsWith(mGp.safMgr.getSdcardRootPath())) {
            boolean isDirectory=(new File(oldUrl)).isDirectory();
            SafFile document=null;
            if (isDirectory) document=mGp.safMgr.createSdcardDirectory(oldUrl);
            else document=mGp.safMgr.createSdcardFile(oldUrl);

            String[] new_name_array=newUrl.split("/");
            result=document.renameTo(new_name_array[new_name_array.length-1]);
            if (result) {
                sendLogMsg("I",oldUrl," was renamed to ",newUrl);
            } else {
                sendLogMsg("I","Rename was failed, from=",oldUrl," to=",newUrl);
                fileioThreadCtrl.setThreadMessage("Rename was failed, from="+oldUrl+" to="+newUrl);
            }
        } else {
        	try {
        		sf = new File( oldUrl );
        		if (sf.isDirectory()) sfd = new File( newUrl+"/" );
        		else sfd = new File( newUrl );
    			if (sf.renameTo(sfd)) {
    				result=true;
    				sendLogMsg("I",oldUrl," was renamed to ",newUrl);
    			} else {
    				sendLogMsg("I","Rename was failed, from=",oldUrl," to=",newUrl);
    				fileioThreadCtrl.setThreadMessage("Rename was failed, from="+oldUrl+" to="+newUrl);
    			}
    		} catch (Exception e) {
    			e.printStackTrace();
    			sendLogMsg("E","Rename error:",e.toString());
    			fileioThreadCtrl.setThreadMessage("Rename error:"+e.toString());
    			result=false;
    			return false;
    		}
    	}

    	return result;
    }

    private boolean deleteLocalItem(String url) {
    	boolean result = false;
    	
    	if (!fileioThreadCtrl.isEnabled()) return false;
    	
    	//url="/sdcard/NEW";
    	sendDebugLogMsg(1,"I","Delete local file entered, File=",url);

    	if (url.startsWith(mGp.safMgr.getUsbRootPath())) {
            SafFile usf = mGp.safMgr.findUsbItem(url);
            result = deleteSafFile(usf, mGp.safMgr.getUsbRootPath());
        } else if (url.startsWith(mGp.safMgr.getSdcardRootPath())) {
            SafFile sf = mGp.safMgr.findSdcardItem(url);
            result = deleteSafFile(sf, mGp.safMgr.getSdcardRootPath());
        } else {
            File sf = new File( url );
            result=deleteLocalFile(sf);
        }
    	return result;
    }

    private boolean deleteSafFile(SafFile lf, String prefix) {
    	boolean result=false;
        if (lf.isDirectory()) {//ディレクトリの場合  
            SafFile[] children = lf.listFiles();//ディレクトリにあるすべてのファイルを処理する
            for (int i=0; i<children.length; i++) {  
            	if (!fileioThreadCtrl.isEnabled()) return false;
            	result=deleteSafFile(children[i], prefix);
                if (!result) {
                    return false;  
                }  
            }
        }
        if (!fileioThreadCtrl.isEnabled()) return false;
        result=lf.delete();
	    if (result) {
    	    sendMsgToProgDlg(lf.getName()+" was deleted");
    	    sendLogMsg("I","File was Deleted. File=",prefix,lf.getPath());
	    } else {
    	    sendLogMsg("I","Delete was failed, File=",prefix,lf.getPath());
    	    fileioThreadCtrl.setThreadMessage("Delete was failed, File="+prefix+"/"+lf.getPath());
	    }
	    return result;
        
    }

    private boolean deleteLocalFile(File lf) {
        boolean result=false;
        if (lf.isDirectory()) {//ディレクトリの場合
            String[] children = lf.list();//ディレクトリにあるすべてのファイルを処理する
            for (int i=0; i<children.length; i++) {
                if (!fileioThreadCtrl.isEnabled()) return false;
                boolean success = deleteLocalFile(new File(lf, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        // ディレクトリまたはファイルを削除
        if (!fileioThreadCtrl.isEnabled()) return false;
        result=lf.delete();
        if (result) {
            MediaScannerConnection.scanFile(mContext, new String[]{lf.getPath()}, null, null);
            sendMsgToProgDlg(lf.getName()+" was deleted");
            sendLogMsg("I","File was Deleted. File=",lf.getPath());
        } else {
            sendLogMsg("I","Delete was failed, File=",lf.getPath());
            fileioThreadCtrl.setThreadMessage("Delete was failed, File="+lf.getPath());
        }
        return result;

    }

    private boolean deleteRemoteItem(JcifsAuth smb_auth, String url) {
    	JcifsFile sf;
    	boolean result = false;
    	
    	if (!fileioThreadCtrl.isEnabled()) return false;
    	
    	sendDebugLogMsg(1,"I","Delete remote file entered, File=",url);
    	
    	try {
    		result=true;
			sf = new JcifsFile( url+"/",smb_auth );
			result=deleteRemoteFile(sf);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			sendLogMsg("E","Remote file delete error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Remote file delete error:"+e.toString());
			result=false;
			return false;
		} catch (JcifsException e) {
            e.printStackTrace();
            sendLogMsg("E","Remote file delete error:",e.toString());
            fileioThreadCtrl.setThreadMessage("Remote file delete error:"+e.toString());
            result=false;
            return false;
        }
        return result;
    }
 
    private boolean deleteRemoteFile(JcifsFile rf) {
        //ファイルやフォルダを削除  
        //フォルダの場合、中にあるすべてのファイルやサブフォルダも削除されます  
    	try {
			if (rf.isDirectory()) {//ディレクトリの場合  
	            JcifsFile[] children = rf.listFiles();//ディレクトリにあるすべてのファイルを処理する
	            for (int i=0; i<children.length; i++) {  
	            	if (!fileioThreadCtrl.isEnabled()) return false;
                    boolean success = deleteRemoteFile(children[i]);
	                if (!success) {  
	                    return false;  
	                }  
	            }
	        }  
		    // 削除  
	        if (!fileioThreadCtrl.isEnabled()) return false;
		    rf.delete();
		    sendMsgToProgDlg(rf.getName().replace("/", "")+" was deleted");
		    sendLogMsg("I","File was Deleted. File=",rf.getPath());
		} catch (JcifsException e) {
			e.printStackTrace();
			sendLogMsg("E","Remote file delete error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Remote file delete error:"+e.toString());
			return false;
		}
	    return true;
        
    }
    
    private boolean copyLocalToLocal(String fromUrl, String toUrl)  {
        File iLf=null ;
        SafFile from_saf=null;

        boolean result = true;
        
        if (!fileioThreadCtrl.isEnabled()) return false;
        
        sendDebugLogMsg(1,"I","Copy Local to Local from=",fromUrl,", to=",toUrl);
                
		try {
		    boolean isDirectory=false;
		    if (fromUrl.startsWith(mGp.safMgr.getUsbRootPath())) {
		        from_saf=mGp.safMgr.findUsbItem(fromUrl);
		        isDirectory=from_saf.isDirectory();
            } else {
                iLf = new File(fromUrl );
                isDirectory=iLf.isDirectory();
            }
//			Log.v("","name="+iLf.getName()+", d="+iLf.isDirectory()+", r="+iLf.canRead());
			if (isDirectory) { // Directory copy
                makeLocalDirsByDirectoryPath(toUrl);
                if (fromUrl.startsWith(mGp.safMgr.getUsbRootPath())) {
                    SafFile[] children = from_saf.listFiles();
                    for (SafFile element : children) {
                        if (!fileioThreadCtrl.isEnabled()) return false;
                        if (!copyLocalToLocal(fromUrl+"/"+element.getName(), toUrl+"/"+element.getName() ))
                            return false;
                    }
                } else {
                    String[] children = iLf.list();
                    for (String element : children) {
                        if (!fileioThreadCtrl.isEnabled()) return false;
                        if (!copyLocalToLocal(fromUrl+"/"+element, toUrl+"/"+element ))
                            return false;
                    }
                }
			} else { // file copy
                makeLocalDirsByFilePath(toUrl);
                result=copyFileLocalToLocal(iLf,fromUrl,toUrl,"Copying");
			}
		} catch (IOException e) {
			e.printStackTrace();
			sendLogMsg("E","Copy from=",fromUrl,", to=",toUrl);
			sendLogMsg("E","Copy error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Copy error:"+e.toString());
			result=false;
			return false;
		} catch (JcifsException e) {
            e.printStackTrace();
            sendLogMsg("E","Copy from=",fromUrl,", to=",toUrl);
            sendLogMsg("E","Copy error:",e.toString());
            fileioThreadCtrl.setThreadMessage("Copy error:"+e.toString());
            result=false;
            return false;
        }
        return result;
    }
    
	private String makeRemoteTempFilePath(String  targetUrl) {
		String tmp_wu="";
		String last_sep="";
		if (targetUrl.endsWith("/")) {
			tmp_wu=targetUrl.substring(0,(targetUrl.length()-1));
			last_sep="/";
		} else tmp_wu=targetUrl;
		String target_dir1=tmp_wu.substring(0,tmp_wu.lastIndexOf("/"));
		String target_fn=tmp_wu.replace(target_dir1, "").substring(1);
		String tmp_target=target_dir1+"/SMBExplorer.work.tmp"+last_sep;
		return tmp_target;
	}


    private boolean copyRemoteToRemote(JcifsAuth smb_auth_from, JcifsAuth smb_auth_to, String fromUrl, String toUrl)  {
        JcifsFile ihf, ohf = null;
        boolean result = false;
        
        if (!fileioThreadCtrl.isEnabled()) return false;
        
        sendDebugLogMsg(1,"I","copy Remote to Remote from item=",fromUrl,", to item=",toUrl);
                
		String tmp_toUrl="";
		
		try {
			ihf = new JcifsFile(fromUrl,smb_auth_from );
			if (ihf.isDirectory()) { // Directory copy
				result=true;
				ihf = new JcifsFile(fromUrl+"/",smb_auth_from);
				ohf = new JcifsFile(toUrl,smb_auth_to);
                makeRemoteDirsByDirectoryPath(smb_auth_to, toUrl+"/");

				String[] children = ihf.list();
				for (String element : children) {
					if (!fileioThreadCtrl.isEnabled()) return false;
	            	boolean success=copyRemoteToRemote(smb_auth_from, smb_auth_to, fromUrl+"/"+element, toUrl+"/"+element );
	            	if (!success) return false;
	            }
			} else { // file copy
				makeRemoteDirsByFilePath(smb_auth_to, toUrl);
				tmp_toUrl=makeRemoteTempFilePath(toUrl);
				
				ohf = new JcifsFile(tmp_toUrl,smb_auth_to);
				if (ohf.exists()) ohf.delete();
				result=true;
				if (!fileioThreadCtrl.isEnabled()) return false;
				if (ihf.getAttributes()<16384) { //no EA, copy was done
					result=copyFileRemoteToRemote(ihf,ohf,fromUrl,toUrl,"Copying");
					if (result) {
						JcifsFile hfd=new JcifsFile(toUrl,smb_auth_to);
						if (hfd.exists()) hfd.delete();
						ohf.renameTo(hfd);
					}

				} else {
					result=false;
					sendLogMsg("E","EA founded, copy canceled. path=",fromUrl);
					fileioThreadCtrl.setThreadMessage("Copy error:"+"EA founded, copy canceled");
				}
			}
		} catch (JcifsException e) {
			e.printStackTrace();
			sendLogMsg("E","Copy from=",fromUrl,", to=",toUrl);
			sendLogMsg("E","Copy error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Copy error:"+e.toString());
			result=false;
			if (!tmp_toUrl.equals("")) {
				try {
					if (ohf.exists()) ohf.delete();
				} catch (JcifsException e1) {
				}
			}
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			sendLogMsg("E","Copy from=",fromUrl,", to=",toUrl);
			sendLogMsg("E","Copy error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Copy error:"+e.toString());
			result=false;
			return false;
		}
		return result;
    }

    private boolean copyRemoteToLocal(JcifsAuth smb_auth, String fromUrl, String toUrl)  {
        JcifsFile hf,hfd;
        File lf ;
        boolean result = false;
        
        if (!fileioThreadCtrl.isEnabled()) return false;
        
        sendDebugLogMsg(1,"I","Copy Remote to Local from item=",fromUrl,", to item=",toUrl);
                
		try {
			hf = new JcifsFile(fromUrl ,smb_auth);
			if (hf.isDirectory()) { // Directory copy
				result=true;
                makeLocalDirsByDirectoryPath(toUrl);
                hfd = new JcifsFile(fromUrl+"/",smb_auth);
				String[] children = hfd.list();
				for (String element : children) {
					if (!fileioThreadCtrl.isEnabled()) return false;
	            	result=copyRemoteToLocal(smb_auth, fromUrl+"/"+element, toUrl+"/"+element );
	            	if (!result) return false;
	            }
					
			} else { // file copy
				if (hf.getAttributes()<16384) { //no EA, copy was done
					lf=new File(toUrl);
					result=copyFileRemoteToLocal(hf, lf,toUrl, fromUrl,"Copying");
				} else {
					result=false;
					sendLogMsg("E","EA founded, copy canceled. path=",fromUrl);
					fileioThreadCtrl.setThreadMessage("Copy error:"+"EA founded, copy canceled");
				}
			}
		} catch (JcifsException e) {
			e.printStackTrace();
			sendLogMsg("E","Copy from=",fromUrl,", to=",toUrl);
			sendLogMsg("E","Copy error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Copy error:"+e.toString());
			result=false;
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			sendLogMsg("E","Copy from=",fromUrl,", to=",toUrl);
			sendLogMsg("E","Copy error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Copy error:"+e.toString());
			result=false;
			return false;
		}
		return result;
    }
    
    private boolean copyLocalToRemote(JcifsAuth smb_auth, String fromUrl, String toUrl)  {
        JcifsFile ohf=null ;
        File ilf=null,lfd ;
        SafFile from_saf=null;
        boolean result = false;
        
        if (!fileioThreadCtrl.isEnabled()) return false;
        
        sendDebugLogMsg(1,"I","Copy Local to Remote from item=",fromUrl,", to item=",toUrl);

		String tmp_toUrl="";

		try {
            boolean isDirectory=false;
            if (fromUrl.startsWith(mGp.safMgr.getUsbRootPath())) {
                from_saf=mGp.safMgr.findUsbItem(fromUrl);
                isDirectory=from_saf.isDirectory();
            } else {
                ilf = new File(fromUrl );
                isDirectory=ilf.isDirectory();
            }

			if (isDirectory) { // Directory copy
				result=true;
                makeRemoteDirsByDirectoryPath(smb_auth, toUrl);
                if (fromUrl.startsWith(mGp.safMgr.getUsbRootPath())) {
                    SafFile[] children = from_saf.listFiles();
                    for (SafFile element : children) {
                        if (!fileioThreadCtrl.isEnabled()) return false;
                        String sep=element.isDirectory()?"/":"";
                        if (!copyLocalToRemote(smb_auth, fromUrl+"/"+element, toUrl+"/"+element+sep))
                            return false;
                    }
                } else {
                    File[] children = ilf.listFiles();
                    for (File element : children) {
                        if (!fileioThreadCtrl.isEnabled()) return false;
                        String sep=element.isDirectory()?"/":"";
                        if (!copyLocalToRemote(smb_auth, fromUrl+"/"+element.getName(), toUrl+"/"+element.getName()+sep ))
                            return false;
                    }
                }

            } else { // file copy
				makeRemoteDirsByFilePath(smb_auth, toUrl);
				tmp_toUrl=makeRemoteTempFilePath(toUrl);
				ohf = new JcifsFile(tmp_toUrl,smb_auth);
				if (ohf.exists()) ohf.delete();
				result=copyFileLocalToRemote(ohf,ilf,fromUrl,toUrl,"Copying");
				if (result) {
					JcifsFile hfd=new JcifsFile(toUrl,smb_auth);
					if (hfd.exists()) hfd.delete();
					ohf.renameTo(hfd);
				}
			}
		} catch (JcifsException e) {
			e.printStackTrace();
			sendLogMsg("E","Copy from=",fromUrl,", to=",toUrl);
			sendLogMsg("E","Copy error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Copy error:"+e.toString());
			result=false;
			if (!tmp_toUrl.equals("")) {
				try {
					if (ohf.exists()) ohf.delete();
				} catch (JcifsException e1) {
				}
			}
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			sendLogMsg("E","Copy from=",fromUrl,", to=",toUrl);
			sendDebugLogMsg(1,"E","Copy error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Copy error:"+e.toString());
			result=false;
			return false;
		}
		return result;
    }

    private boolean moveLocalToLocal(String fromUrl, String toUrl)  {
        File iLf=null;
        boolean result = false;
        
        if (!fileioThreadCtrl.isEnabled()) return false;
        
        sendDebugLogMsg(1,"I","Move Local to Local from item=",fromUrl,",to item=",toUrl);

        boolean isDirectory=false;
        SafFile from_saf=null;
        if (fromUrl.startsWith(mGp.safMgr.getUsbRootPath())) {
            from_saf=mGp.safMgr.findUsbItem(fromUrl);
            isDirectory=from_saf.isDirectory();
        } else {
            iLf = new File(fromUrl );
            isDirectory=iLf.isDirectory();
        }

		if (isDirectory) { // Directory copy
			result=true;

            if (fromUrl.startsWith(mGp.safMgr.getUsbRootPath())) {
                SafFile[] children = from_saf.listFiles();
                for (SafFile element : children) {
                    if (!fileioThreadCtrl.isEnabled()) return false;
                    if (!moveLocalToLocal(fromUrl+"/"+element.getName(), toUrl+"/"+element.getName() ))
                        return false;
               }
                makeLocalDirsByFilePath(toUrl+"/");
                from_saf.delete();
                sendLogMsg("I",fromUrl," was moved.");
            } else {
                String[] children = iLf.list();
                for (String element : children) {
                    if (!fileioThreadCtrl.isEnabled()) return false;
                    if (!moveLocalToLocal(fromUrl+"/"+element, toUrl+"/"+element ))
                        return false;
                }
                makeLocalDirsByFilePath(toUrl+"/");
                if (fromUrl.startsWith(mGp.safMgr.getSdcardRootPath())) {
                    SafFile dsf=mGp.safMgr.createSdcardItem(fromUrl,true);
                    dsf.delete();
                } else {
                    iLf.delete();
                }
                sendLogMsg("I",fromUrl," was moved.");
            }

		} else { // file rename
			if (!fileioThreadCtrl.isEnabled()) return false;
			makeLocalDirsByFilePath(toUrl);

			if (isSameMountPoint(fromUrl,toUrl) && !fromUrl.startsWith(mGp.safMgr.getSdcardRootPath())
                    && !fromUrl.startsWith(mGp.safMgr.getUsbRootPath())) { // renameでMoveする
                File oLf = new File(toUrl);
                oLf.delete();
                result = iLf.renameTo(oLf);
            } else {
                if (Build.VERSION.SDK_INT>=24 && isSameMountPoint(fromUrl,toUrl) && fromUrl.startsWith(mGp.safMgr.getSdcardRootPath())) {
                    from_saf = mGp.safMgr.findSdcardItem(fromUrl);
                    SafFile to_saf = mGp.safMgr.createSdcardItem(toUrl, true);
                    to_saf.deleteIfExists();
                    from_saf.moveTo(to_saf);
                    sendMsgToProgDlg(from_saf.getName() + " was moved.");
                    sendLogMsg("I", fromUrl , " was moved.");
                    result = true;
                } else if (Build.VERSION.SDK_INT>=24 && isSameMountPoint(fromUrl,toUrl) && fromUrl.startsWith(mGp.safMgr.getUsbRootPath())) {
                    from_saf = mGp.safMgr.findUsbItem(fromUrl);
                    SafFile to_saf = mGp.safMgr.createUsbItem(toUrl, true);
                    to_saf.deleteIfExists();
                    from_saf.moveTo(to_saf);
                    sendMsgToProgDlg(from_saf.getName() + " was moved.");
                    sendLogMsg("I", fromUrl , " was moved.");
                    result = true;
                } else {
                    try {
                        result=copyFileLocalToLocal(iLf,fromUrl,toUrl,"Copying");
                        if (result) {
                            deleteLocalItem(fromUrl);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        sendLogMsg("E","Copy error:",e.toString());
                        fileioThreadCtrl.setThreadMessage("Copy error:"+e.toString());
                        result=false;
                        return false;
                    } catch (JcifsException e) {
                        e.printStackTrace();
                        sendLogMsg("E","Copy error:",e.toString());
                        fileioThreadCtrl.setThreadMessage("Copy error:"+e.toString());
                        result=false;
                        return false;
                    }
                }
			}
			scanMediaStoreLibraryFile(toUrl);
			if (result) sendLogMsg("I",fromUrl," was moved to ",toUrl);
			else sendLogMsg("I","Move was failed. fromUrl=", fromUrl,", toUrl=",toUrl);
		}
		return result;
    }

    private boolean isSameMountPoint(String f_fp, String t_fp) {
    	boolean result=false;
    	if (f_fp.startsWith(mGp.internalRootDirectory) && t_fp.startsWith(mGp.internalRootDirectory)) result=true;
    	else if (f_fp.startsWith(mGp.safMgr.getSdcardRootPath()) && t_fp.startsWith(mGp.safMgr.getSdcardRootPath())) result=true;
        else if (f_fp.startsWith(mGp.safMgr.getUsbRootPath()) && t_fp.startsWith(mGp.safMgr.getUsbRootPath())) result=true;
    	sendDebugLogMsg(1,"I","isSameMountPoint result="+result,", f_fp=",f_fp,", t_fp=",t_fp);
    	return result;
    }
    
    private boolean moveRemoteToRemoteByRename(JcifsAuth smb_auth_from, JcifsAuth smb_auth_to, String fromUrl, String toUrl)  {
        JcifsFile ihf,hfd, ohf = null;
        boolean result = false;
        
        if (!fileioThreadCtrl.isEnabled()) return false;
        
        sendDebugLogMsg(1,"I","Move Remote to Remote from item=",fromUrl,", to item=",toUrl);
                
		try {
			ihf = new JcifsFile(fromUrl ,smb_auth_from);
			if (ihf.isDirectory()) { // Directory copy
				result=true;
				hfd = new JcifsFile(fromUrl+"/",smb_auth_from);
				ohf = new JcifsFile(toUrl,smb_auth_from);
				
				String[] children = hfd.list();
				for (String element : children) {
					if (!fileioThreadCtrl.isEnabled()) return false;
	            	boolean success= moveRemoteToRemoteByRename(smb_auth_from, smb_auth_to, fromUrl+"/"+element, toUrl+"/"+element );
	            	if (!success) return false;
	            }
				makeRemoteDirsByFilePath(smb_auth_to, toUrl+"/");
				ihf.delete();
				sendLogMsg("I",fromUrl," was deleted.");
			} else { // file move
				if (!fileioThreadCtrl.isEnabled()) return false;
				makeRemoteDirsByFilePath(smb_auth_to, toUrl);

                ohf=new JcifsFile(toUrl+"/",smb_auth_to);
                if (ohf.exists()) ohf.delete();
                ihf.renameTo(ohf);
                result=ohf.exists();
				if (result) sendLogMsg("I",fromUrl," was moved to ",toUrl);
				else sendLogMsg("I","Move was failed. fromUrl=", fromUrl,", toUrl=",toUrl);
			}
		} catch (JcifsException e) {
			e.printStackTrace();
			sendLogMsg("E","Move error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Move error:"+e.toString());
			result=false;
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			sendLogMsg("E","Move error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Move error:"+e.toString());
			result=false;
			return false;
		}
		return result;
    }
    
    private boolean downloadRemoteFile(JcifsAuth smb_auth, String fromUrl, String toUrl)  {
        JcifsFile hf,hfd;
        File lf ;
        boolean result = false;
        
        if (!fileioThreadCtrl.isEnabled()) return false;
        
        sendDebugLogMsg(1,"I","Download Remote file, from item=",fromUrl,", to item=",toUrl);
                
		try {
			hf = new JcifsFile(fromUrl ,smb_auth);
			if (hf.isDirectory()) { // Directory copy
				result=true;
				hfd = new JcifsFile(fromUrl+"/",smb_auth);
				String[] children = hfd.list();
				for (String element : children) {
					if (!fileioThreadCtrl.isEnabled()) return false;
	            	result=copyRemoteToLocal(smb_auth, fromUrl+"/"+element, toUrl+"/"+element );
	            	if (!result) return false;
	            }
					
			} else { // file copy
				if (hf.getAttributes()<16384) { //no EA, copy was done
					makeLocalDirsByFilePath(toUrl);
					result=true;
					lf=new File(toUrl);
					if (!isFileDifferent(hf.getLastModified(),hf.length(),lf.lastModified(),lf.length())) {
						sendDebugLogMsg(1,"I","Download was cancelled because file does not changed.");
					} else {
						result=copyFileRemoteToLocal(hf, lf,toUrl, fromUrl,"Downloading");
					}
				} else {
					result=false;
					sendLogMsg("E","EA founded, copy canceled. path=",fromUrl);
					fileioThreadCtrl.setThreadMessage("Download error:"+"EA founded, copy canceled");
				}
			}
		} catch (JcifsException e) {
			e.printStackTrace();
			sendLogMsg("E","Download error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Download error:"+e.toString());
			result=false;
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			sendLogMsg("E","Download error:",e.toString());
			fileioThreadCtrl.setThreadMessage("Download error:"+e.toString());
			result=false;
			return false;
		}
		return result;
    }

    private boolean copyFileLocalToLocal(File iLf, String fromUrl, String toUrl, String title_header)
            throws IOException, JcifsException {
    	boolean result=false;
    	if (toUrl.startsWith(mGp.safMgr.getSdcardRootPath())) {
    	    if (Build.VERSION.SDK_INT>=24) {
                result= copyFileLocalToSdcard_API24(iLf, fromUrl, toUrl, title_header);
            } else {
                result= copyFileLocalToSdcard_API21(iLf, fromUrl, toUrl, title_header);
            }
        } else if (toUrl.startsWith(mGp.safMgr.getUsbRootPath())) {
            if (Build.VERSION.SDK_INT>=24) {
                result=copyFileLocalToUsb_API21(iLf, fromUrl, toUrl, title_header);
            } else {
                result= copyFileLocalToUsb_API21(iLf, fromUrl, toUrl, title_header);
            }
        } else {
            result= copyFileLocalToInternal(iLf, fromUrl, toUrl, title_header);
        }
    	return result;
    }
    
	private boolean copyFileLocalToInternal(File iLf, String fromUrl, String toUrl, String title_header) throws IOException, JcifsException {
	    long b_time=System.currentTimeMillis();
    	
	    File oLf = new File(toUrl);
	    boolean result=false;
		String tmp_file=mGp.internalAppSpecificDirectory+"/temp.tmp";
	    File t_lf=new File(tmp_file);
	    t_lf.delete();

		long t0 = System.currentTimeMillis();
	    FileOutputStream bos = new FileOutputStream(t_lf);

        FileAttributes ifa= getInputFileAttribute(fromUrl);

        result=copyFile(ifa.is, bos, t_lf, ifa.fileBytes, title_header, ifa.fileName, fromUrl, toUrl);
        if (result) {
            t_lf.setLastModified(ifa.lastMod);
            if (oLf.exists()) result=oLf.delete();
            if (result) {
                boolean rc_r=t_lf.renameTo(oLf);
                if (rc_r) {
                    scanMediaStoreLibraryFile(toUrl);
                    result=true;
                } else {
                    t_lf.delete();
                    sendLogMsg("I","Copy was failed, can not be renamed. Rename from=",fromUrl," to=",toUrl);
                    fileioThreadCtrl.setThreadMessage("Copy was failed, can not be renamed. Rename from="+fromUrl+" to="+toUrl);
                }
            } else {
                t_lf.delete();
                sendLogMsg("I","Copy was failed, Target file not deleted, Target file=",toUrl);
                fileioThreadCtrl.setThreadMessage("Copy was failed, Target file not deleted, Target file="+toUrl);
            }
        }
        sendLogMsg("I",fromUrl," was copied to ",toUrl,", "+ifa.fileBytes , " bytes transfered in " ,
                (System.currentTimeMillis()-b_time)+" mili seconds at " + calTransferRate(ifa.fileBytes,(System.currentTimeMillis()-b_time)));
        return result;
	}

	private static class FileAttributes {
	    public InputStream is=null;
        public OutputStream os=null;
	    public long fileBytes =0l;
        public long lastMod =0l;
        public String fileName ="";
        public SafFile safFile =null;
        public String filePath ="";
        public Object input_file=null;
    }

    private FileAttributes getInputFileAttribute(String input_path) throws FileNotFoundException {
	    FileAttributes ifa=new FileAttributes();

        ifa.filePath=input_path;
        if (input_path.startsWith(mGp.safMgr.getUsbRootPath())) {
            SafFile sf=mGp.safMgr.findUsbItem(input_path);
            ifa.is=mGp.context.getContentResolver().openInputStream(sf.getUri());
            ifa.fileBytes =sf.length();
            ifa.fileName =sf.getName();
            ifa.lastMod =sf.lastModified();
            ifa.safFile =sf;
            ifa.input_file=sf;
        } else if (input_path.startsWith(mGp.safMgr.getSdcardRootPath())) {
            SafFile sf=mGp.safMgr.findSdcardItem(input_path);
            ifa.is=mGp.context.getContentResolver().openInputStream(sf.getUri());
            ifa.fileBytes =sf.length();
            ifa.fileName =sf.getName();
            ifa.lastMod =sf.lastModified();
            ifa.safFile =sf;
            ifa.input_file=sf;
        } else {
            File iLf=new File(input_path);
            ifa.is=new FileInputStream(input_path);
            ifa.fileBytes =iLf.length();
            ifa.fileName =iLf.getName();
            ifa.lastMod =iLf.lastModified();
            ifa.input_file=iLf;
        }
	    return ifa;
    }

    private FileAttributes getOutputFileAttribute(String to_path) throws FileNotFoundException {
        FileAttributes ofa=new FileAttributes();

        ofa.filePath=to_path;
        if (to_path.startsWith(mGp.safMgr.getUsbRootPath())) {
            SafFile sf=mGp.safMgr.createUsbFile(to_path);
            ofa.is=mGp.context.getContentResolver().openInputStream(sf.getUri());
            ofa.fileBytes =sf.length();
            ofa.fileName =sf.getName();
            ofa.lastMod =sf.lastModified();
            ofa.safFile =sf;
        } else if (to_path.startsWith(mGp.safMgr.getSdcardRootPath())) {
            SafFile sf=mGp.safMgr.createSdcardFile(to_path);
            ofa.is=mGp.context.getContentResolver().openInputStream(sf.getUri());
            ofa.fileBytes =sf.length();
            ofa.fileName =sf.getName();
            ofa.lastMod =sf.lastModified();
            ofa.safFile =sf;
        } else {
            File iLf=new File(to_path);
            ofa.is=new FileInputStream(to_path);
            ofa.fileBytes =iLf.length();
            ofa.fileName =iLf.getName();
            ofa.lastMod =iLf.lastModified();
        }
        return ofa;
    }

    private boolean copyFile(InputStream bis, OutputStream bos, Object file_delete, long fileBytes,
                                    String title_header, String file_name, String fromUrl, String toUrl) throws IOException, JcifsException {
        int n=0;
        long tot = 0;
        sendMsgToProgDlg(String.format(title_header+" %s %s%% completed.", file_name,0));
        byte[] io_buff=new byte[1024*1024*2];
        while(( n = bis.read( io_buff)) > 0 ) {
            if (!fileioThreadCtrl.isEnabled()) {
                bis.close();
                bos.close();
                if (file_delete instanceof File) ((File)file_delete).delete();
                else if (file_delete instanceof SafFile) ((SafFile)file_delete).delete();
                else if (file_delete instanceof JcifsFile) ((JcifsFile)file_delete).delete();
                return false;
            }
            bos.write(io_buff, 0, n );
            tot += n;
            if (n<fileBytes)
                sendMsgToProgDlg(String.format(title_header+" %s %s%% completed.", file_name, (tot*100)/fileBytes));
        }
        sendMsgToProgDlg(String.format(title_header+" %s,  %s%% completed.",file_name, 100));
        bis.close();
        bos.flush();
        bos.close();

        return true;
    }

    private boolean copyFileLocalToUsb_API21(File iLf, String fromUrl, String toUrl, String title_header) throws IOException, JcifsException {
        long b_time=System.currentTimeMillis();
        boolean result=false;
        SafFile oLf = mGp.safMgr.createUsbItem(toUrl, false);
        OutputStream bos = mContext.getContentResolver().openOutputStream(oLf.getUri());

        FileAttributes ifa= getInputFileAttribute(fromUrl);

        boolean copy_success=copyFile(ifa.is, bos, oLf, ifa.fileBytes, title_header, ifa.fileName, fromUrl, toUrl);
//        if (copy_success) {
//            scanMediaStoreLibraryFile(toUrl);
//        }
        sendLogMsg("I",fromUrl," was copied to ",toUrl,", "+ifa.fileBytes + " bytes transfered in " +
                (System.currentTimeMillis()-b_time)+" mili seconds at " + calTransferRate(ifa.fileBytes,(System.currentTimeMillis()-b_time)));

        return copy_success;
    }

    private boolean copyFileLocalToSdcard_API21(File iLf, String fromUrl, String toUrl, String title_header) throws IOException, JcifsException {
        long b_time=System.currentTimeMillis();
	    SafFile oLf = mGp.safMgr.createSdcardFile(toUrl);
	    boolean result=false;
		long t0 = System.currentTimeMillis();
		OutputStream bos = mContext.getContentResolver().openOutputStream(oLf.getUri());
//        BufferedOutputStream bos=new BufferedOutputStream(os, 1024*1024*8);

        FileAttributes ifa= getInputFileAttribute(fromUrl);

        boolean copy_success=copyFile(ifa.is, bos, oLf, ifa.fileBytes, title_header, ifa.fileName, fromUrl, toUrl);
        if (copy_success) {
            scanMediaStoreLibraryFile(toUrl);
        }
        sendLogMsg("I",fromUrl," was copied to ",toUrl+", "+ifa.fileBytes + " bytes transfered in " +
                (System.currentTimeMillis()-b_time)+" mili seconds at " + calTransferRate(ifa.fileBytes,(System.currentTimeMillis()-b_time)));

	    return copy_success;
	}

    private boolean copyFileLocalToSdcard_API24(File iLf, String fromUrl, String toUrl, String title_header) throws IOException, JcifsException {
        long b_time=System.currentTimeMillis();
        boolean result=false;
        String fn=toUrl.substring(toUrl.lastIndexOf("/")+1);
        File tmp_file=new File(mGp.safMgr.getSdcardRootPath()+"/"+APP_SPECIFIC_DIRECTORY+"/files/"+fn);
        OutputStream bos = new FileOutputStream(tmp_file);
        FileAttributes ifa= getInputFileAttribute(fromUrl);

        result=copyFile(ifa.is, bos, tmp_file, ifa.fileBytes, title_header, ifa.fileName, fromUrl, toUrl);
        if (result) {
            boolean slm=tmp_file.setLastModified(ifa.lastMod);
            SafFile temp_saf = mGp.safMgr.createSdcardItem(tmp_file.getPath(), false);
            SafFile dest_saf = mGp.safMgr.createSdcardItem(toUrl, false);
            dest_saf.deleteIfExists();
            result=temp_saf.moveTo(dest_saf);
            if (result) {
                scanMediaStoreLibraryFile(toUrl);
            } else {
                temp_saf.delete();
                sendLogMsg("I","Copy was failed because can not be moved. Move from=",fromUrl," to=",toUrl);
                fileioThreadCtrl.setThreadMessage("Copy was failed because can not be moved. Move from="+fromUrl+" to="+toUrl);
            }
        }
        sendLogMsg("I",fromUrl," was copied to ",toUrl,", "+ifa.fileBytes + " bytes transfered in " +
                (System.currentTimeMillis()-b_time)+" mili seconds at " + calTransferRate(ifa.fileBytes,(System.currentTimeMillis()-b_time)));
        return result;
    }

    private boolean copyFileRemoteToRemote(JcifsFile ihf, JcifsFile ohf,String fromUrl, String toUrl, String title_header) throws IOException, JcifsException {
        long b_time=System.currentTimeMillis();
	    InputStream bis = ihf.getInputStream();
	    OutputStream bos = ohf.getOutputStream();

        boolean result=copyFile(bis, bos, ohf, ihf.length(), title_header, ihf.getName(), fromUrl, toUrl);
        if (result) {
            try {
                ohf.setLastModified(ihf.getLastModified());
            } catch (JcifsException e) {
                sendLogMsg("I", "JcifsFile#setLastModified() was failed, reason=" , e.getMessage());
            }
        }
        sendLogMsg("I",fromUrl," was copied to ",toUrl,", "+ihf.length() + " bytes transfered in " +
                (System.currentTimeMillis()-b_time)+" mili seconds at " + calTransferRate(ihf.length(),(System.currentTimeMillis()-b_time)));
		return result;
	}

	private boolean copyFileLocalToRemote(JcifsFile tf, File mf, String fromUrl, String toUrl, String title_header) throws IOException, JcifsException {
        long b_time=System.currentTimeMillis();

	    OutputStream bos = tf.getOutputStream();
//	    BufferedInputStream bis=new BufferedInputStream(in,SMB_BUFF_SIZE);
//	    BufferedOutputStream bos=new BufferedOutputStream(out,SMB_BUFF_SIZE);

        FileAttributes ifa= getInputFileAttribute(fromUrl);

        boolean result=copyFile(ifa.is, bos, tf, ifa.fileBytes, title_header, mf.getName(), fromUrl, toUrl);
        if (result) {
            try {
                tf.setLastModified(ifa.lastMod);
            } catch(JcifsException e) {
                sendLogMsg("I","JcifsFile#setLastModified() was failed, reason=",e.getMessage());
            }
        }
        sendLogMsg("I",fromUrl," was copied to ",toUrl,", "+ifa.fileBytes + " bytes transfered in " +
                (System.currentTimeMillis()-b_time)+" mili seconds at " + calTransferRate(ifa.fileBytes,(System.currentTimeMillis()-b_time)));
        return result;
	}

    private boolean copyFileRemoteToLocal(JcifsFile hf, File lf, String toUrl, String fromUrl, String title_header) throws IOException, JcifsException {
//    	return copyFileLocalToLocalByChannel(iLf, fromUrl, toUrl, title_header);
    	boolean result=false;
        if (toUrl.startsWith(mGp.safMgr.getUsbRootPath())) {
            result=copyFileRemoteToUsb(hf, lf, toUrl, fromUrl, title_header);
        } else if (toUrl.startsWith(mGp.safMgr.getSdcardRootPath())) {
    	    if (Build.VERSION.SDK_INT>=24) {
                result=copyFileRemoteToSdcard_API24(hf, lf, toUrl, fromUrl, title_header);
            } else {
                result=copyFileRemoteToSdcard_API21(hf, lf, toUrl, fromUrl, title_header);
            }
        } else result=copyFileRemoteToInternal(hf, lf, toUrl, fromUrl, title_header);
    	return result;
    }

	private boolean copyFileRemoteToInternal(JcifsFile hf, File lf, String toUrl, String fromUrl, String title_header)
            throws IOException, JcifsException {
        long b_time=System.currentTimeMillis();
	    File oLf = new File(toUrl);
	    boolean result=false;
        String tmp_file=mGp.internalAppSpecificDirectory+"/temp.tmp";
	    File t_lf=new File(tmp_file);
	    t_lf.delete();

	    InputStream bis = hf.getInputStream();
	    FileOutputStream bos = new FileOutputStream(t_lf);

        result=copyFile(bis, bos, tmp_file, hf.length(), title_header, hf.getName(), fromUrl, toUrl);
        if (result) {
            t_lf.setLastModified(hf.getLastModified());
            if (oLf.exists()) result=oLf.delete();
            if (result) {
                result=t_lf.renameTo(oLf);
                if (result) {
                    scanMediaStoreLibraryFile(toUrl);
                    result=true;
                    sendLogMsg("I",fromUrl+" was copied to "+toUrl+", "+hf.length() + " bytes transfered in " +
                            (System.currentTimeMillis()-b_time)+" mili seconds at " + calTransferRate(hf.length(),(System.currentTimeMillis()-b_time)));
                } else {
                    t_lf.delete();
                    sendLogMsg("I","Copy was failed, can not be renamed. Rename from="+fromUrl+" to="+toUrl);
                    fileioThreadCtrl.setThreadMessage("Copy was failed, can not be renamed. Rename from="+fromUrl+" to="+toUrl);
                }
            } else {
                t_lf.delete();
                sendLogMsg("I","Copy was failed, because target file not deleted, Target file="+toUrl);
                fileioThreadCtrl.setThreadMessage("Copy was failed, because target file not deleted, Target file="+toUrl);
            }
        }
	    return result;
    };

    private boolean copyFileRemoteToUsb(JcifsFile hf, File lf, String toUrl, String fromUrl, String title_header)
            throws IOException, JcifsException {
        long b_time=System.currentTimeMillis();

        SafFile oLf = mGp.safMgr.createUsbFile(toUrl);
        boolean result=false;
        long t0 = System.currentTimeMillis();

        InputStream bis = hf.getInputStream();
        OutputStream bos = mContext.getContentResolver().openOutputStream(oLf.getUri());

        result=copyFile(bis, bos, oLf, hf.length(), title_header, hf.getName(), fromUrl, toUrl);
        sendLogMsg("I",fromUrl+" was copied to "+toUrl+", "+hf.length() + " bytes transfered in " +
                (System.currentTimeMillis()-b_time)+" mili seconds at " + calTransferRate(hf.length(),(System.currentTimeMillis()-b_time)));
        return result;
    };

    private boolean copyFileRemoteToSdcard_API21(JcifsFile hf, File lf, String toUrl, String fromUrl, String title_header)
            throws IOException, JcifsException {
        long b_time=System.currentTimeMillis();
	    boolean result=false;
        SafFile oLf = mGp.safMgr.createSdcardFile(toUrl);

	    InputStream bis = hf.getInputStream();
	    OutputStream bos = mContext.getContentResolver().openOutputStream(oLf.getUri());
//	    BufferedOutputStream bos=new BufferedOutputStream(fout,SMB_BUFF_SIZE);
        result=copyFile(bis, bos, oLf, hf.length(), title_header, hf.getName(), fromUrl, toUrl);
        sendLogMsg("I",fromUrl+" was copied to "+toUrl+", "+hf.length() + " bytes transfered in " +
                (System.currentTimeMillis()-b_time)+" mili seconds at " + calTransferRate(hf.length(),(System.currentTimeMillis()-b_time)));
        return result;
    };

    private boolean copyFileRemoteToSdcard_API24(JcifsFile mf, File tf, String toUrl, String fromUrl, String title_header) throws IOException, JcifsException {
        long b_time=System.currentTimeMillis();

        boolean result=false;
        File tmp_file=new File(mGp.safMgr.getSdcardRootPath()+"/"+APP_SPECIFIC_DIRECTORY+"/files/temp.file");
        SafFile t_df = mGp.safMgr.createSdcardFile(tmp_file.getPath());

        InputStream bis = mf.getInputStream();
        OutputStream bos = new FileOutputStream(tmp_file);

        result=copyFile(bis, bos, t_df, mf.length(), title_header, mf.getName(), fromUrl, toUrl);
        if (result) {
            tmp_file.setLastModified(mf.getLastModified());
            SafFile oLf = mGp.safMgr.createSdcardItem(toUrl, false);
            if (oLf.exists()) oLf.delete();

            result=t_df.moveTo(oLf);
            if (result) {
                scanMediaStoreLibraryFile(toUrl);
                sendLogMsg("I",fromUrl+" was copied to "+toUrl+", "+mf.length() + " bytes transfered in " +
                        (System.currentTimeMillis()-b_time)+" mili seconds at " + calTransferRate(mf.length(),(System.currentTimeMillis()-b_time)));
                result=true;
            } else {
                t_df.delete();
                sendLogMsg("I","Copy was failed, can not be moved. Move from="+fromUrl+" to="+toUrl);
                fileioThreadCtrl.setThreadMessage("Copy was failed, can not be moved. Move from="+fromUrl+" to="+toUrl);
            }
        }
        return result;
    };

    private void scanMediaStoreLibraryFile(String fp) {
        MediaScannerConnection.scanFile(mContext, new String[]{fp}, null, null);
	};

    private String isMediaFile(String fp) {
        String mt=null;
        String fid="";
        if (fp.lastIndexOf(".")>0) {
            fid=fp.substring(fp.lastIndexOf(".")+1,fp.length());
            fid=fid.toLowerCase();
        }
        mt=MimeTypeMap.getSingleton().getMimeTypeFromExtension(fid);
        if (mt==null) return "";
        else return mt;
    };

    private boolean isNoMediaPath(String path) {
        if (path == null) return false;

        if (path.indexOf("/.") >= 0) return true;

        int offset = 1;
        while (offset >= 0 && offset<path.lastIndexOf("/")) {
            int slashIndex = path.indexOf('/', offset);
            if (slashIndex > offset) {
                slashIndex++; // move past slash
                File file = new File(path.substring(0, slashIndex) + ".nomedia");
//                Log.v("","off="+offset+", si="+slashIndex+", p="+file.getPath());
                if (file.exists()) {
                    return true;
                }
            }
            offset = slashIndex;
        }
        return false;
    }

    private boolean isFileDifferent(long f1_lm, long f1_fl,long f2_lm, long f2_fl) {
    	boolean result=false;
    	if (f1_fl==f2_fl) {
    		long td=Math.abs(f1_lm-f2_lm);
    		if (td>=3000) result=true;//Allowance time is 3Seconds
    	} else result=true;
    	return result;
    };
    
	private boolean makeRemoteDirsByFilePath(JcifsAuth smb_auth, String targetPath)
					throws MalformedURLException, JcifsException {
		boolean result=false;
		String target_dir1="";
		String target_dir2="";
		if (targetPath.lastIndexOf("/")<=0) return false;
		else {
			if (targetPath.endsWith("/")) {//path is dir
				target_dir1=targetPath.substring(0,targetPath.lastIndexOf("/"));
			} else {
				target_dir1=targetPath;
			}
			target_dir2=target_dir1.substring(0,target_dir1.lastIndexOf("/"));
		}

        result=makeRemoteDirsByDirectoryPath(smb_auth, target_dir2 + "/");
		return result;
	}

    private boolean makeRemoteDirsByDirectoryPath(JcifsAuth smb_auth, String targetPath)
            throws MalformedURLException, JcifsException {
        boolean result=false;
        JcifsFile hf = new JcifsFile(targetPath + "/",smb_auth);
        if (!hf.exists()) {
            hf.mkdirs();
        }
        return result;
    };


    private boolean makeLocalDirsByFilePath(String targetPath) {
		boolean result=false;
		String target_dir="";
		if (targetPath.lastIndexOf("/")<=0) return false;
		else target_dir=targetPath.substring(0,targetPath.lastIndexOf("/"));
		result=makeLocalDirsByDirectoryPath(target_dir);
		return result;
	};

    private boolean makeLocalDirsByDirectoryPath(String targetPath) {
        boolean result=false;
        if (targetPath.startsWith(mGp.safMgr.getUsbRootPath())) {
            SafFile sf=mGp.safMgr.createUsbDirectory(targetPath);
        } else {
            File lf = new File(targetPath);
            if (!lf.exists()) {
                if (Build.VERSION.SDK_INT>=21 && targetPath.startsWith(mGp.safMgr.getSdcardRootPath())) {
                    SafFile t_sf=mGp.safMgr.createSdcardDirectory(targetPath);
                    result=t_sf==null?false:true;
                } else {
                    lf.mkdirs();
                    result=true;
                }
            }
        }
        return result;
    }

    private String calTransferRate(long tb, long tt) {
	    long et=tt;
	    if (et==0L) et=1;
	    String tfs = null;
	    BigDecimal bd_tr;
	    if (tb>(1024)) {//KB
		    BigDecimal dfs1 = new BigDecimal(tb*1.000);
		    BigDecimal dfs2 = new BigDecimal(1024*1.000);
		    BigDecimal dfs3 = new BigDecimal("0.000000");
		    dfs3=dfs1.divide(dfs2);
			BigDecimal dft1 = new BigDecimal(et*1.000);
		    BigDecimal dft2 = new BigDecimal(1000.000);
		    BigDecimal dft3 = new BigDecimal("0.000000");
		    dft3=dft1.divide(dft2);
			bd_tr=dfs3.divide(dft3,2,BigDecimal.ROUND_HALF_UP);
			tfs=bd_tr+"KBytes/sec";
		} else {
		    BigDecimal dfs1 = new BigDecimal(tb*1.000);
		    BigDecimal dfs2 = new BigDecimal(1024*1.000);
		    BigDecimal dfs3 = new BigDecimal("0.000000");
		    dfs3=dfs1.divide(dfs2);
			BigDecimal dft1 = new BigDecimal(et*1.000);
		    BigDecimal dft2 = new BigDecimal(1000.000);
		    BigDecimal dft3 = new BigDecimal("0.000000");
		    dft3=dft1.divide(dft2);
		    if (dft3.toString().equals("0")) bd_tr=new BigDecimal("0");
			else bd_tr=dfs3.divide(dft3,2,BigDecimal.ROUND_HALF_UP);
			tfs=bd_tr+"Bytes/sec";
		}
		
		return tfs;
	};
}
