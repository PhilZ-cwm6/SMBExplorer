package com.sentaroh.android.SMBExplorer;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;

import com.sentaroh.android.Utilities.Base64Compat;
import com.sentaroh.android.Utilities.Dialog.CommonDialog;
import com.sentaroh.android.Utilities.EncryptUtil;
import com.sentaroh.android.Utilities.NotifyEvent;
import com.sentaroh.android.Utilities.ThreadCtrl;
import com.sentaroh.android.Utilities.Widget.CustomSpinnerAdapter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;

import static android.content.Context.MODE_PRIVATE;
import static com.sentaroh.android.SMBExplorer.Constants.SMBEXPLORER_KEY_STORE_ALIAS;
import static com.sentaroh.android.SMBExplorer.Constants.SMBEXPLORER_PROFILE_NAME;

public class SmbServerUtil {

    public static SmbServerConfig getSmbServerConfigItem(String name, ArrayList<SmbServerConfig> sl) {
        SmbServerConfig result=null;
        for(SmbServerConfig item:sl) {
            if (item.getName().equals(name)) {
                result=item;
                break;
            }
        }
        return result;
    }

    static public ArrayList<SmbServerConfig> createSmbServerConfigList(GlobalParameters gp, boolean sdcard, String fp) {
        BufferedReader br = null;
        ArrayList<SmbServerConfig> rem = new ArrayList<SmbServerConfig>();
        boolean error=false;
        try {
            String priv_key=null;
            EncryptUtil.CipherParms cp_int=null;
            if (sdcard) {
                File sf = new File(fp);
                if (sf.exists()) {
                    br = new BufferedReader(new FileReader(fp));
                } else {
                    gp.commonDlg.showCommonDialog(false,"E",
                            String.format(gp.context.getString(R.string.msgs_local_file_list_create_nfound), fp),"",null);
                    error=true;
                }
            } else {
                priv_key=KeyStoreUtil.getGeneratedPasswordNewVersion(gp.context, SMBEXPLORER_KEY_STORE_ALIAS);
                cp_int= EncryptUtil.initDecryptEnv(priv_key);

                InputStream in = gp.context.openFileInput(SMBEXPLORER_PROFILE_NAME);
                br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            }
            if (!error) {
                String pl="", dec_pl="";
                String[] alp;
                while ((pl = br.readLine()) != null) {
                    if (sdcard) dec_pl=pl;
                    else {
                        byte[] dec_array = Base64Compat.decode(pl, Base64Compat.NO_WRAP);
                        dec_pl = EncryptUtil.decrypt(dec_array, cp_int);
                    }
                    alp = parseSmbServerConfigString(dec_pl);
//                    public SmbServerConfig(String pfn,
//                            String domain, String pf_user, String pf_pass, String pf_addr, String pf_port, String pf_share){
                    SmbServerConfig sc=new SmbServerConfig(alp[1], "", alp[3], alp[4], alp[5], alp[6], alp[7]);
                    sc.setSmbLevel(alp[9]);
                    rem.add(sc);
                }
                br.close();
            }
        } catch (FileNotFoundException e) {
            if (sdcard) {
                gp.mUtil.addDebugMsg(0,"E",e.toString());
                gp.commonDlg.showCommonDialog(false,"E", gp.context.getString(R.string.msgs_exception),e.toString(),null);
            }
            error=true;
        } catch (IOException e) {
            gp.mUtil.addDebugMsg(0,"E",e.toString());
            gp.commonDlg.showCommonDialog(false,"E", gp.context.getString(R.string.msgs_exception),e.toString(),null);
            error=true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (error) {
            rem.add(new SmbServerConfig("HOME-D","", "Android", "", "192.168.200.128", "","D"));
            rem.add(new SmbServerConfig("HOME-E", "", "Android", "", "192.168.200.128", "","E"));
            rem.add(new SmbServerConfig("HOME-F", "", "Android", "", "192.168.200.128", "","F"));
            rem.add(new SmbServerConfig("SRV-D",  "", "Android", "", "192.168.200.10",  "","D"));
        }
        Collections.sort(rem);

        return rem;
    }

    static public void saveSmbServerConfigList(GlobalParameters gp) {
        saveSmbServerConfigList(gp, false, "", "");
    }

    static public void saveSmbServerConfigList(GlobalParameters gp, boolean sdcard, String fd, String fn) {
        PrintWriter pw;
        BufferedWriter bw = null;
        try {
            String priv_key=null;
            EncryptUtil.CipherParms cp_int=null;

            if (sdcard) {
                File lf = new File(fd);
                if (!lf.exists()) lf.mkdir();
                bw = new BufferedWriter(new FileWriter(fd+"/"+fn));
                pw = new PrintWriter(bw);
            } else {
                priv_key=KeyStoreUtil.getGeneratedPasswordNewVersion(gp.context, SMBEXPLORER_KEY_STORE_ALIAS);
                cp_int= EncryptUtil.initDecryptEnv(priv_key);

                OutputStream out = gp.context.openFileOutput(SMBEXPLORER_PROFILE_NAME, MODE_PRIVATE);
                pw = new PrintWriter(new OutputStreamWriter(out, "UTF-8"));
            }

            if (gp.smbConfigList !=null) {
                for (int i = 0; i <= (gp.smbConfigList.size() - 1); i++) {
                    SmbServerConfig item = gp.smbConfigList.get(i);
                    if (item.getType().equals("R")) {
                        if (sdcard) {
                            String pl = item.getType() + "\t"   //0
                                    + item.getName() + "\t"     //1
                                    + "" + "\t"                 //2
                                    + item.getUser() + "\t"     //3
                                    + "" + "\t"                 //4 Do not save password
                                    + item.getAddr() + "\t"     //5
                                    + item.getPort() + "\t"     //6
                                    + item.getShare()+ "\t"     //7
                                    + item.getDomain()+ "\t"    //8
                                    + item.getSmbLevel()+ "\t"  //9
                                    ;
                            pw.println(pl);
                        } else {
                            String pl = item.getType() + "\t"   //0
                                    + item.getName() + "\t"     //1
                                    + "" + "\t"                 //2
                                    + item.getUser() + "\t"     //3
                                    + item.getPass() + "\t"     //4
                                    + item.getAddr() + "\t"     //5
                                    + item.getPort() + "\t"     //6
                                    + item.getShare()+ "\t"     //7
                                    + item.getDomain()+ "\t"    //8
                                    + item.getSmbLevel()+ "\t"  //9
                                    ;
                            String enc =Base64Compat.encodeToString(EncryptUtil.encrypt(pl, cp_int), Base64Compat.NO_WRAP);
                            pw.println(enc);
                        }
                    }
                }
            }
            pw.close();
//            bw.close();
        } catch (IOException e) {
            gp.mUtil.addDebugMsg(0,"E",e.toString());
            gp.commonDlg.showCommonDialog(false,"E",gp.context.getString(R.string.msgs_exception),e.toString(),null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static private String[] parseSmbServerConfigString(String text) {

        String[] parm = new String[100];
        String[] parm_t = text.split("\t");

        for (int i = 0; i < parm.length; i++) {
            if (i<parm_t.length) {
                if (parm_t[i].length()==0) parm[i] = "";
                else parm[i] = parm_t[i];
            } else parm[i] = "";
        }
        return parm;

    }

    static public void importSmbServerConfigDlg(GlobalParameters gp, final String curr_dir, String file_name) {

        gp.mUtil.addDebugMsg(1,"I","Import profile dlg.");

        NotifyEvent ne=new NotifyEvent(gp.context);
        // set commonDialog response
        ne.setListener(new NotifyEvent.NotifyEventListener() {
            @Override
            public void positiveResponse(Context c,Object[] o) {
                String fpath=(String)o[0];

                ArrayList<SmbServerConfig> tfl = createSmbServerConfigList(gp, true, fpath);
                if (tfl!=null) {
                    gp.smbConfigList =tfl;
                    saveSmbServerConfigList(gp);
                    updateSmbShareSpinner(gp);
                    gp.commonDlg.showCommonDialog(false,"I",gp.context.getString(R.string.msgs_select_import_dlg_success), fpath, null);
                }

            }

            @Override
            public void negativeResponse(Context c,Object[] o) {}
        });
        gp.commonDlg.fileOnlySelectWithCreate(curr_dir, "/SMBExplorer",file_name,"Select import file.",ne);
    }

    static public void exportSmbServerConfigListDlg(GlobalParameters gp, final String curr_dir, final String ifn) {
        gp.mUtil.addDebugMsg(1,"I","Export profile.");

        NotifyEvent ne=new NotifyEvent(gp.context);
        // set commonDialog response
        ne.setListener(new NotifyEvent.NotifyEventListener() {
            @Override
            public void positiveResponse(Context c, Object[] o) {
                String fpath=(String)o[0];
                String fd=fpath.substring(0,fpath.lastIndexOf("/"));
                String fn=fpath.replace(fd+"/","");
                writeSmbServerConfigList(gp, fd,fn);
            }

            @Override
            public void negativeResponse(Context c,Object[] o) {}
        });
        gp.commonDlg.fileOnlySelectWithCreate(curr_dir, "/SMBExplorer",ifn,"Select export file.",ne);
    }

    static public void writeSmbServerConfigList(GlobalParameters gp, final String profile_dir, final String profile_filename) {
        gp.mUtil.addDebugMsg(1,"I","Export profile to file");

        File lf = new File(profile_dir + "/" + profile_filename);
        if (lf.exists()) {
            NotifyEvent ne=new NotifyEvent(gp.context);
            // set commonDialog response
            ne.setListener(new NotifyEvent.NotifyEventListener() {
                @Override
                public void positiveResponse(Context c,Object[] o) {
                    saveSmbServerConfigList(gp, true,profile_dir,profile_filename);
                    gp.commonDlg.showCommonDialog(false,"I",gp.context.getString(R.string.msgs_select_export_dlg_success),
                            profile_dir+"/"+profile_filename, null);
                }

                @Override
                public void negativeResponse(Context c,Object[] o) {}
            });
            gp.commonDlg.showCommonDialog(true,"I",
                    String.format(gp.context.getString(R.string.msgs_select_export_dlg_override),
                            profile_dir+"/"+profile_filename),"",ne);
            return;
        } else {
            saveSmbServerConfigList(gp, true,profile_dir,profile_filename);
            gp.commonDlg.showCommonDialog(false,"I", gp.context.getString(R.string.msgs_select_export_dlg_success),
                    profile_dir+"/"+profile_filename, null);
        }
    }

    static public void createSmbServerFileList(MainActivity activity, GlobalParameters gp, String opcd, String smb_level,
                                               String url, String user, String pass, final NotifyEvent n_event) {
        final ArrayList<FileListItem> remoteFileList=new ArrayList<FileListItem>();

        final ThreadCtrl tc = new ThreadCtrl();
        tc.setEnabled();

        final Dialog pi_dialog= CommonDialog.showProgressSpinIndicator(activity);
        pi_dialog.setOnCancelListener(new Dialog.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface arg0) {
                tc.setDisabled();//disableAsyncTask();
                gp.mUtil.addDebugMsg(1, "W", "CreateRemoteFileList cancelled.");
            }
        });
        final Handler hndl=new Handler();
        NotifyEvent ne=new NotifyEvent(gp.context);
        ne.setListener(new NotifyEvent.NotifyEventListener() {
            @Override
            public void positiveResponse(Context c,Object[] o) {
                pi_dialog.dismiss();
                if (tc.isThreadResultSuccess()) {
                    hndl.post(new Runnable() {
                        @Override
                        public void run() {
                            n_event.notifyToListener(true, new Object[]{remoteFileList});
                        }
                    });
                } else {
                    hndl.post(new Runnable() {
                        @Override
                        public void run() {
                            String err="";
                            if (tc.isThreadResultCancelled()) err="Filelist was cancelled";
                            else err=tc.getThreadMessage();
                            n_event.notifyToListener(false, new Object[]{err});
                        }
                    });
                }
            }
            @Override
            public void negativeResponse(Context c,Object[] o) {
            }
        });

        Thread th = new RetrieveFileList(gp, tc, opcd, smb_level, url, remoteFileList,user,pass,ne);
        th.start();
        pi_dialog.show();
    }

    static public void updateSmbShareSpinner(GlobalParameters gp) {
        final CustomSpinnerAdapter spAdapter = (CustomSpinnerAdapter)gp.remoteFileListDirSpinner.getAdapter();
        int sel_no=gp.remoteFileListDirSpinner.getSelectedItemPosition();
        if (spAdapter.getItem(0).startsWith("---")) {
            spAdapter.clear();
            spAdapter.add("--- Not selected ---");
        } else {
            spAdapter.clear();
        }
        int a_no=0;
        for (int i = 0; i<gp.smbConfigList.size(); i++) {
            spAdapter.add(gp.smbConfigList.get(i).getName());
        }
    }

    static public void replaceCurrentSmbServerConfig(GlobalParameters gp) {
        if (gp.currentSmbServerConfig==null) return;
        for(SmbServerConfig item:gp.smbConfigList) {
            if (item.getName().equals(gp.currentSmbServerConfig.getName())) {
                gp.currentSmbServerConfig=item;
                break;
            }
        }
    }

}
