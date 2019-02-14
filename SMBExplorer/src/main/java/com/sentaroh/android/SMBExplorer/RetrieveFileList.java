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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import android.os.Handler;
import android.util.Log;

import com.sentaroh.android.Utilities.NotifyEvent;
import com.sentaroh.android.Utilities.ThreadCtrl;
import com.sentaroh.jcifs.JcifsAuth;
import com.sentaroh.jcifs.JcifsException;
import com.sentaroh.jcifs.JcifsFile;
import com.sentaroh.jcifs.JcifsUtil;

public class RetrieveFileList extends Thread  {
	private final static String DEBUG_TAG = "SMBExplorer";
	
	private ThreadCtrl getFLCtrl=null;
	
	private ArrayList<FileListItem> remoteFileList=null;
	private String remoteUrl;//, currDir;
	private List<String> dir_list;
	
	private String opCode="FL";
	
	private NotifyEvent notifyEvent ;
	
	private Handler uiHandler=null;
	
	private JcifsAuth ntlmPaswordAuth;
	
	private GlobalParameters mGp=null;

	private int mSmbLevel =JcifsAuth.JCIFS_FILE_SMB211;
	public RetrieveFileList(GlobalParameters gp, ThreadCtrl ac, String smb_level, String ru, List<String> d_list,
			String user, String pass, NotifyEvent ne) {
//		currContext=c;
		mGp=gp;
		
		getFLCtrl=ac; //new SMBExplorerThreadCtrl();
		notifyEvent=ne;
		remoteUrl=ru;
		
		uiHandler=new Handler();
		
		dir_list=d_list;
		
		opCode=OPCD_EXISTS_CHECK; //check item is exists

		if (!user.equals("")) tuser=user;
		if (!pass.equals("")) tpass=pass;
        mSmbLevel =Integer.parseInt(smb_level);
	}

	public final static String OPCD_FILE_LIST="FL";
    public final static String OPCD_SHARE_LIST="SL";
	public final static String OPCD_EXISTS_CHECK="EC";

    private String tuser=null,tpass="";

    public RetrieveFileList(GlobalParameters gp,
			ThreadCtrl ac, String opcd, String smb_level, String ru, ArrayList<FileListItem> fl,
			String user, String pass, NotifyEvent ne) {
		mGp=gp;
		remoteFileList=fl;
		
		uiHandler=new Handler();

		getFLCtrl=ac; //new SMBExplorerThreadCtrl();
		notifyEvent=ne;
		remoteUrl=ru;
		
		opCode=opcd;
		
		if (!user.equals("")) tuser=user;
		if (!pass.equals("")) tpass=pass;
        mSmbLevel =Integer.parseInt(smb_level);
	}
	
	
	@Override
	public void run() {
		getFLCtrl.setThreadResultSuccess();
		
		sendDebugLogMsg(1,"I","getFileList started");

		defaultUEH = Thread.currentThread().getUncaughtExceptionHandler();
        Thread.currentThread().setUncaughtExceptionHandler(unCaughtExceptionHandler);

        ntlmPaswordAuth = new JcifsAuth(mSmbLevel, "", tuser, tpass);

//        Log.v("","url="+remoteUrl);
		String host_t1=remoteUrl.replace("smb://","").replaceAll("//", "/");
		String host_t2=host_t1.substring(0,host_t1.indexOf("/"));
		String host_t3=host_t2;
		if (host_t2.indexOf(":")>=0) host_t3=host_t2.substring(0,host_t2.indexOf(":"));
		boolean error=false;
		String err_msg="";
		if (JcifsUtil.isValidIpAddress(host_t3)) {
			if (!isSmbHostAddressConnected(host_t3)) {
				error=true;
				err_msg="Can not be connected to specified IP address, IP address="+host_t3;
			}
		} else {
			if (JcifsUtil.getSmbHostNameByAddress(mSmbLevel, host_t3)==null) {
				error=true;
				err_msg="Specified hostname is not found, Name="+host_t3;
			}
		}
		
        Thread.currentThread().setUncaughtExceptionHandler(defaultUEH);

		if (error) {
			getFLCtrl.setThreadResultError();
			getFLCtrl.setDisabled();
			getFLCtrl.setThreadMessage(err_msg);
		} else {
			if (opCode.equals(OPCD_FILE_LIST)) {
				remoteFileList.clear();
				ArrayList<FileListItem> tl=readFileList(remoteUrl);
				for (int i=0;i<tl.size();i++) remoteFileList.add(tl.get(i));
            } else if (opCode.equals(OPCD_SHARE_LIST)) {
                remoteFileList.clear();
                ArrayList<FileListItem> tl=readShareList(remoteUrl);
                for (int i=0;i<tl.size();i++) remoteFileList.add(tl.get(i));
            } else if (opCode.equals(OPCD_EXISTS_CHECK)) {
                checkItemExists(remoteUrl);
			}
			
		}
		sendDebugLogMsg(1,"I","getFileList terminated.");
		uiHandler.post(new Runnable(){
			@Override
			public void run() {
				notifyEvent.notifyToListener(true, null);
			}
		});
		getFLCtrl.setDisabled();
		
	};
	
	public static boolean isSmbHostAddressConnected(String addr) {
		boolean result=false;
		if (JcifsUtil.isIpAddressAndPortConnected(addr,139,3500) ||
				JcifsUtil.isIpAddressAndPortConnected(addr,445,3500)) result=true;
		return result;
	}

// Default uncaught exception handler variable
    private UncaughtExceptionHandler defaultUEH;
    
// handler listener
    private Thread.UncaughtExceptionHandler unCaughtExceptionHandler =
        new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
            	Thread.currentThread().setUncaughtExceptionHandler(defaultUEH);
            	ex.printStackTrace();
            	StackTraceElement[] st=ex.getStackTrace();
            	String st_msg="";
            	for (int i=0;i<st.length;i++) {
            		st_msg+="\n at "+st[i].getClassName()+"."+
            				st[i].getMethodName()+"("+st[i].getFileName()+
            				":"+st[i].getLineNumber()+")";
            	}
            	getFLCtrl.setThreadResultError();
    			String end_msg=ex.toString()+st_msg;
    			getFLCtrl.setThreadMessage(end_msg);
    			getFLCtrl.setDisabled();
    			notifyEvent.notifyToListener(true, null);
                // re-throw critical exception further to the os (important)
//                defaultUEH.uncaughtException(thread, ex);
            }
    };
    
	private ArrayList<FileListItem> readShareList(String url) {
		ArrayList<FileListItem> rem_list=new ArrayList<FileListItem>();
		sendDebugLogMsg(2,"I","Filelist directory: "+url);
		try {		
			JcifsFile remoteFile = new JcifsFile(url,ntlmPaswordAuth);
			JcifsFile[] fl = remoteFile.listFiles();

			for (int i=0;i<fl.length;i++){
				if (getFLCtrl.isEnabled()) {
					String fn=fl[i].getName();
					if (fn.endsWith("/")) fn=fn.substring(0,fn.length()-1);
					if (!fn.endsWith("$") &&
                            fl[i].canRead() &&
//							fl[i].isDirectory() && 
							!fn.equals("System Volume Information") ) {
						boolean has_ea=fl[i].getAttributes()<16384?false:true;
						String fp=fl[i].getParent();
						if (fp.endsWith("/")) fp=fp.substring(0,fp.lastIndexOf("/"));
						int dirct=0;
						try {
							if (fl[i].isDirectory()) {
								JcifsFile tdf=new JcifsFile(fl[i].getPath(),ntlmPaswordAuth);
								JcifsFile[] tfl=tdf.listFiles();
								dirct=tfl.length;
							}
						} catch (JcifsException e) {
							sendDebugLogMsg(0,"I","File ignored by exception: "+e.toString()+", "+ "Name="+fn);
						}					
						FileListItem fi=new FileListItem(
								fn,
								fl[i].isDirectory(),
								0,
								0, //fl[i].lastModified(),
								false,
								true, //fl[i].canRead(),
								true, //fl[i].canWrite(),
								false, //fl[i].isHidden(),
								fp,0);
						fi.setSubDirItemCount(dirct);
						fi.setHasExtendedAttr(has_ea);
						rem_list.add(fi);
						sendDebugLogMsg(2,"I","Filelist added: "+ "Name="+fn+", ");
					}
				} else {
					getFLCtrl.setThreadResultCancelled();
					sendDebugLogMsg(-1,"W","Cancelled by main task.");
					break;
				}
			}
		} catch (JcifsException e) {
			e.printStackTrace();
			sendDebugLogMsg(0,"E",e.toString());
			getFLCtrl.setThreadResultError();
			getFLCtrl.setDisabled();
			getFLCtrl.setThreadMessage(e.toString());
		} catch (MalformedURLException e) {
			e.printStackTrace();
			sendDebugLogMsg(0,"E",e.toString());
			getFLCtrl.setThreadResultError();
			getFLCtrl.setDisabled();
			getFLCtrl.setThreadMessage(e.toString());
		}
		return rem_list;
	};

    private ArrayList<FileListItem> readFileList(String url) {
        ArrayList<FileListItem> rem_list=new ArrayList<FileListItem>();
        sendDebugLogMsg(2,"I","Filelist directory: "+url);
        try {
            JcifsFile remoteFile = new JcifsFile(url,ntlmPaswordAuth);
            JcifsFile[] fl = remoteFile.listFiles();

            for (int i=0;i<fl.length;i++){
                if (getFLCtrl.isEnabled()) {
                    String fn=fl[i].getName();
                    if (fn.endsWith("/")) fn=fn.substring(0,fn.length()-1);
                    if (!fn.endsWith("$") &&
                            fl[i].canRead() &&
//							fl[i].isDirectory() &&
                            !fn.equals("System Volume Information") ) {
                        boolean has_ea=fl[i].getAttributes()<16384?false:true;
                        String fp=fl[i].getParent();
                        if (fp.endsWith("/")) fp=fp.substring(0,fp.lastIndexOf("/"));
                        int dirct=0;
                        try {
                            if (fl[i].isDirectory()) {
                                JcifsFile tdf=new JcifsFile(fl[i].getPath(),ntlmPaswordAuth);
                                JcifsFile[] tfl=tdf.listFiles();
                                dirct=tfl.length;
                            }
                        } catch (JcifsException e) {
                            sendDebugLogMsg(0,"I","File ignored by exception: "+e.toString()+", "+
                                    "Name="+fn+", "+
                                    "isDirectory="+fl[i].isDirectory()+", "+
                                    "Length="+fl[i].length()+", "+
                                    "LastModified="+fl[i].getLastModified()+", "+
                                    "CanRead="+fl[i].canRead()+", "+
                                    "CanWrite="+fl[i].canWrite()+", "+
                                    "isHidden="+fl[i].isHidden()+", "+
                                    "hasExtendedAttr="+has_ea+", "+
                                    "Parent="+fp+", " +
                                    "Path="+fl[i].getPath()+", "+
                                    "CanonicalPath="+fl[i].getCanonicalPath());
                        }
                        FileListItem fi=new FileListItem(
                                fn,
                                fl[i].isDirectory(),
                                fl[i].length(),
                                fl[i].getLastModified(),
                                false,
                                fl[i].canRead(),
                                fl[i].canWrite(),
                                fl[i].isHidden(),
                                fp,0);
                        fi.setSubDirItemCount(dirct);
                        fi.setHasExtendedAttr(has_ea);
                        rem_list.add(fi);
                        sendDebugLogMsg(2,"I","Filelist added: "+
                                "Name="+fn+", "+
                                "isDirectory="+fl[i].isDirectory()+", "+
                                "Length="+fl[i].length()+", "+
                                "LastModified="+fl[i].getLastModified()+", "+
                                "CanRead="+fl[i].canRead()+", "+
                                "CanWrite="+fl[i].canWrite()+", "+
                                "isHidden="+fl[i].isHidden()+", "+
                                "hasExtendedAttr="+has_ea+", "+
                                "Parent="+fp+", " +
                                "Path="+fl[i].getPath()+", "+
                                "CanonicalPath="+fl[i].getCanonicalPath());
                    }
                } else {
                    getFLCtrl.setThreadResultCancelled();
                    sendDebugLogMsg(-1,"W","Cancelled by main task.");
                    break;
                }
            }
        } catch (JcifsException e) {
            e.printStackTrace();
            sendDebugLogMsg(0,"E",e.toString());
            getFLCtrl.setThreadResultError();
            getFLCtrl.setDisabled();
            getFLCtrl.setThreadMessage(e.toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
            sendDebugLogMsg(0,"E",e.toString());
            getFLCtrl.setThreadResultError();
            getFLCtrl.setDisabled();
            getFLCtrl.setThreadMessage(e.toString());
        }
        return rem_list;
    };

    private void checkItemExists(String url) {
		for (int i=0;i<dir_list.size();i++) {
			try {		
				JcifsFile remoteFile = 
						new JcifsFile(url+"/"+dir_list.get(i),ntlmPaswordAuth);
				if (!remoteFile.exists()) dir_list.set(i,"");
			} catch (JcifsException e) {
				e.printStackTrace();
				sendDebugLogMsg(0,"E",e.toString());
				getFLCtrl.setThreadResultError();
				getFLCtrl.setDisabled();
				getFLCtrl.setThreadMessage(e.toString());
			} catch (MalformedURLException e) {
				e.printStackTrace();
				sendDebugLogMsg(0,"E",e.toString());
				getFLCtrl.setThreadResultError();
				getFLCtrl.setDisabled();
				getFLCtrl.setThreadMessage(e.toString());
			}
		}
	};
	
	
	private void sendDebugLogMsg(int lvl, final String cat, final String msg) {
		if (mGp.settingDebugLevel>=lvl) {
			Log.v(DEBUG_TAG,"FILELIST"+" "+msg);
		}
	};
	
}