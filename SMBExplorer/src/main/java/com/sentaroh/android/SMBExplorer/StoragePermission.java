package com.sentaroh.android.SMBExplorer;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.sentaroh.android.Utilities2.Dialog.CommonDialog;
import com.sentaroh.android.Utilities2.SafManager3;

import java.util.ArrayList;

public class StoragePermission {

    private Context mContext=null;
    private ActivityMain mActivity=null;
    private GlobalParameters mGp=null;
    private CommonUtilities mUtil=null;
    private CommonDialog commonDlg=null;
    
    private Dialog mDialog=null;
    
    public StoragePermission(ActivityMain a, GlobalParameters gp) {
        mContext = gp.context;
        mActivity = a;
        mGp = gp;
        mUtil = gp.mUtil;
        commonDlg = gp.commonDlg;
    }

    public boolean isStoragePermissionGranted() {
        ArrayList<String>rows=buildStoragePermissionRequiredList();
        if (rows.size()>0) return true;
        else return false;
    }

    private ArrayList<String> buildStoragePermissionRequiredList() {
        final ArrayList<SafManager3.StorageVolumeInfo>svi_list= SafManager3.getStorageVolumeInfo(mContext);
        final ArrayList<String>rows=new ArrayList<String>();

        for(SafManager3.StorageVolumeInfo ssi:svi_list) {
            if (!mGp.safMgr.isUuidRegistered(ssi.uuid)) {
                if (ssi.uuid.equals(SafManager3.SAF_FILE_PRIMARY_UUID)) {
                    if (Build.VERSION.SDK_INT>=29) rows.add(ssi.description);
                } else {
                    rows.add(ssi.description);
                }
            }
        }
        return rows;
    }

    public void showDialog() {
        initDialog();
    }

    private void initDialog() {
        // カスタムダイアログの生成
        mDialog = new Dialog(mActivity);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.setContentView(R.layout.storage_permission_dlg);

        LinearLayout ll_dlg_view = (LinearLayout) mDialog.findViewById(R.id.storage_permission_dlg_view);
//        ll_dlg_view.setBackgroundColor(mGp.themeColorList.dialog_msg_background_color);

        LinearLayout title_view = (LinearLayout) mDialog.findViewById(R.id.storage_permission_dlg_title_view);
        title_view.setBackgroundColor(mGp.themeColorList.title_background_color);
        TextView dlg_title = (TextView) mDialog.findViewById(R.id.storage_permission_dlg_title);
        dlg_title.setTextColor(mGp.themeColorList.title_text_color);

        CommonDialog.setDlgBoxSizeLimit(mDialog, true);

        final Button btn_ok = (Button) mDialog.findViewById(R.id.storage_permission_dlg_ok);
//        btn_ok.setBackgroundColor(Color.DKGRAY);
        final Button btn_cancel = (Button) mDialog.findViewById(R.id.storage_permission_dlg_cancel);
//        btn_cancel.setBackgroundColor(Color.TRANSPARENT);//.DKGRAY);
        final TextView tv_msg = (TextView) mDialog.findViewById(R.id.storage_permission_dlg_msg);

        final ListView lv = (ListView) mDialog.findViewById(R.id.storage_permission_dlg_storage_list);
        final ArrayList<SafManager3.StorageVolumeInfo>svi_list=SafManager3.getStorageVolumeInfo(mContext);

        final ArrayList<String>rows=buildStoragePermissionRequiredList();
        if (rows.size()==0) {
            commonDlg.showCommonDialog(false, "W", "There was no storage requiring permissions.","",null);
            return;
        }

        lv.setAdapter(new ArrayAdapter<String>(mActivity, android.R.layout.simple_list_item_multiple_choice, rows));
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        lv.setScrollingCacheEnabled(false);
        lv.setScrollbarFadingEnabled(false);

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long id) {
                mUtil.addDebugMsg(1,"I", "clicked pos="+pos);
                SparseBooleanArray checked = lv.getCheckedItemPositions();
                boolean selected=false;
                for (int i = 0; i <= rows.size(); i++) {
                    if (checked.get(i) == true) {
                        selected=true;
                        break;
                    }
                }
                if (selected) CommonDialog.setViewEnabled(mActivity, btn_ok, true);
                else CommonDialog.setViewEnabled(mActivity, btn_ok, false);
            }
        });

        CommonDialog.setViewEnabled(mActivity, btn_ok, false);
        btn_ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SparseBooleanArray checked = lv.getCheckedItemPositions();
                boolean selected=false;
                for (int i = 0; i <= rows.size(); i++) {
                    if (checked.get(i) == true) {
                        String r_desc=rows.get(i);
                        for(SafManager3.StorageVolumeInfo ssi:svi_list) {
                            if (ssi.description.equals(r_desc)) {
                                mActivity.requestStooragePermissionsByUuid(ssi.uuid);
                            }
                        }
                    }
                }
                mDialog.dismiss();
            }
        });

        btn_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDialog.dismiss();
            }
        });

        mDialog.show();
    }
}
