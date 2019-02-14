package com.sentaroh.android.SMBExplorer;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.sentaroh.android.Utilities.Dialog.CommonDialog;
import com.sentaroh.android.Utilities.Dialog.DialogBackKeyListener;
import com.sentaroh.android.Utilities.NotifyEvent;
import com.sentaroh.android.Utilities.ThreadCtrl;
import com.sentaroh.jcifs.JcifsAuth;
import com.sentaroh.jcifs.JcifsUtil;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;

public class ScanSmbServer {

    private MainActivity mActivity =null;
    private Context mContext = null;
    private GlobalParameters mGp=null;
    private CommonUtilities mUtil=null;

    private int mSmbLevel = JcifsAuth.JCIFS_FILE_SMB211;
    public ScanSmbServer(MainActivity a, GlobalParameters gp, String smb_level) {
        mActivity=a;
        mContext=gp.context;
        mGp=gp;
        mUtil=gp.mUtil;
        mSmbLevel =Integer.parseInt(smb_level);
    }

    public void scanSmbServerDlg(final NotifyEvent p_ntfy, String port_number, boolean scan_start) {
        final Dialog dialog = new Dialog(mActivity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        dialog.setContentView(R.layout.scan_remote_ntwk_dlg);

        LinearLayout ll_dlg_view = (LinearLayout) dialog.findViewById(R.id.scan_remote_ntwk_dlg_view);
        ll_dlg_view.setBackgroundColor(mGp.themeColorList.dialog_msg_background_color);

        final LinearLayout title_view = (LinearLayout) dialog.findViewById(R.id.scan_remote_ntwk_title_view);
        final TextView title = (TextView) dialog.findViewById(R.id.scan_remote_ntwk_title);
        title_view.setBackgroundColor(mGp.themeColorList.dialog_title_background_color);
        title.setTextColor(mGp.themeColorList.text_color_dialog_title);

        final Button btn_scan = (Button) dialog.findViewById(R.id.scan_remote_ntwk_btn_ok);
        final Button btn_cancel = (Button) dialog.findViewById(R.id.scan_remote_ntwk_btn_cancel);
        final TextView tvmsg = (TextView) dialog.findViewById(R.id.scan_remote_ntwk_msg);
        final TextView tv_result = (TextView) dialog.findViewById(R.id.scan_remote_ntwk_scan_result_title);
        tvmsg.setText(mContext.getString(R.string.msgs_scan_network_smb_press_scan));
        tv_result.setVisibility(TextView.GONE);

        final String from = getLocalIpAddress();
        String subnet = from.substring(0, from.lastIndexOf("."));
        String subnet_o1, subnet_o2, subnet_o3;
        subnet_o1 = subnet.substring(0, subnet.indexOf("."));
        subnet_o2 = subnet.substring(subnet.indexOf(".") + 1, subnet.lastIndexOf("."));
        subnet_o3 = subnet.substring(subnet.lastIndexOf(".") + 1, subnet.length());
        final EditText baEt1 = (EditText) dialog.findViewById(R.id.scan_remote_ntwk_begin_address_o1);
        final EditText baEt2 = (EditText) dialog.findViewById(R.id.scan_remote_ntwk_begin_address_o2);
        final EditText baEt3 = (EditText) dialog.findViewById(R.id.scan_remote_ntwk_begin_address_o3);
        final EditText baEt4 = (EditText) dialog.findViewById(R.id.scan_remote_ntwk_begin_address_o4);
        final EditText eaEt4 = (EditText) dialog.findViewById(R.id.scan_remote_ntwk_end_address_o4);
        baEt1.setText(subnet_o1);
        baEt2.setText(subnet_o2);
        baEt3.setText(subnet_o3);
        baEt4.setText("1");
        baEt4.setSelection(1);
        eaEt4.setText("254");
        baEt4.requestFocus();

        final CheckBox ctv_use_port_number = (CheckBox) dialog.findViewById(R.id.scan_remote_ntwk_ctv_use_port);
        final EditText et_port_number = (EditText) dialog.findViewById(R.id.scan_remote_ntwk_port_number);

        CommonDialog.setDlgBoxSizeLimit(dialog, true);

        if (port_number.equals("")) {
            et_port_number.setEnabled(false);
            ctv_use_port_number.setChecked(false);
        } else {
            et_port_number.setEnabled(true);
            et_port_number.setText(port_number);
            ctv_use_port_number.setChecked(true);
        }
        ctv_use_port_number.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                ctv_use_port_number.toggle();
                boolean isChecked = ctv_use_port_number.isChecked();
                et_port_number.setEnabled(isChecked);
            }
        });

        final NotifyEvent ntfy_lv_click = new NotifyEvent(mContext);
        ntfy_lv_click.setListener(new NotifyEvent.NotifyEventListener() {
            @Override
            public void positiveResponse(Context c, Object[] o) {
                dialog.dismiss();
                p_ntfy.notifyToListener(true, o);
            }

            @Override
            public void negativeResponse(Context c, Object[] o) {
            }
        });

        final ArrayList<AdapterSmbServerList.NetworkScanListItem> ipAddressList = new ArrayList<AdapterSmbServerList.NetworkScanListItem>();
        final ListView lv = (ListView) dialog.findViewById(R.id.scan_remote_ntwk_scan_result_list);
        final AdapterSmbServerList adapter =
                new AdapterSmbServerList(mContext, R.layout.scan_address_result_list_item, ipAddressList, ntfy_lv_click);
        lv.setAdapter(adapter);
        lv.setScrollingCacheEnabled(false);
        lv.setScrollbarFadingEnabled(false);

        //SCANボタンの指定
        btn_scan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ipAddressList.clear();
                NotifyEvent ntfy = new NotifyEvent(mContext);
                ntfy.setListener(new NotifyEvent.NotifyEventListener() {
                    @Override
                    public void positiveResponse(Context c, Object[] o) {
                        if (ipAddressList.size() < 1) {
                            tvmsg.setText(mContext.getString(R.string.msgs_scan_network_smb_server_not_detected));
                            tv_result.setVisibility(TextView.GONE);
                        } else {
                            tvmsg.setText(mContext.getString(R.string.msgs_scan_network_smb_select_detected_server));
                            tv_result.setVisibility(TextView.VISIBLE);
                        }
                    }
                    @Override
                    public void negativeResponse(Context c, Object[] o) {}
                });
                if (auditScanAddressRangeValue(dialog)) {
                    tv_result.setVisibility(TextView.GONE);
                    String ba1 = baEt1.getText().toString();
                    String ba2 = baEt2.getText().toString();
                    String ba3 = baEt3.getText().toString();
                    String ba4 = baEt4.getText().toString();
                    String ea4 = eaEt4.getText().toString();
                    String subnet = ba1 + "." + ba2 + "." + ba3;
                    int begin_addr = Integer.parseInt(ba4);
                    int end_addr = Integer.parseInt(ea4);
                    scanNetwork(dialog, lv, adapter, ipAddressList, subnet, begin_addr, end_addr, ntfy);
                } else {
                    //error
                }
            }
        });

        //CANCELボタンの指定
        btn_cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
                p_ntfy.notifyToListener(false, null);
            }
        });
        // Cancelリスナーの指定
        dialog.setOnCancelListener(new Dialog.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface arg0) {
                btn_cancel.performClick();
            }
        });
        dialog.show();

        if (scan_start) btn_scan.performClick();
    }

    private int mScanCompleteCount = 0, mScanAddrCount = 0;
    private ArrayList<String> mScanRequestedAddrList = new ArrayList<String>();
    private String mLockScanCompleteCount = "";

    private void scanNetwork(
            final Dialog dialog,
            final ListView lv_ipaddr,
            final AdapterSmbServerList adap,
            final ArrayList<AdapterSmbServerList.NetworkScanListItem> ipAddressList,
            final String subnet, final int begin_addr, final int end_addr,
            final NotifyEvent p_ntfy) {
        final Handler handler = new Handler();
        final ThreadCtrl tc = new ThreadCtrl();
        final LinearLayout ll_addr = (LinearLayout) dialog.findViewById(R.id.scan_remote_ntwk_scan_address);
        final LinearLayout ll_prog = (LinearLayout) dialog.findViewById(R.id.scan_remote_ntwk_progress);
        final TextView tvmsg = (TextView) dialog.findViewById(R.id.scan_remote_ntwk_progress_msg);
        final Button btn_scan = (Button) dialog.findViewById(R.id.scan_remote_ntwk_btn_ok);
        final Button btn_cancel = (Button) dialog.findViewById(R.id.scan_remote_ntwk_btn_cancel);
        final Button scan_cancel = (Button) dialog.findViewById(R.id.scan_remote_ntwk_progress_cancel);

        final CheckBox ctv_use_port_number = (CheckBox) dialog.findViewById(R.id.scan_remote_ntwk_ctv_use_port);
        final EditText et_port_number = (EditText) dialog.findViewById(R.id.scan_remote_ntwk_port_number);

        tvmsg.setText("");
        scan_cancel.setText(R.string.msgs_progress_spin_dlg_cancel);
        ll_addr.setVisibility(LinearLayout.GONE);
        ll_prog.setVisibility(LinearLayout.VISIBLE);
        btn_scan.setVisibility(Button.GONE);
        btn_cancel.setVisibility(Button.GONE);
        adap.setButtonEnabled(false);
        scan_cancel.setEnabled(true);
        dialog.setOnKeyListener(new DialogBackKeyListener(mContext));
        dialog.setCancelable(false);
        // CANCELボタンの指定
        scan_cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                scan_cancel.setText(mContext.getString(R.string.msgs_progress_dlg_canceling));
                scan_cancel.setEnabled(false);
                mUtil.addDebugMsg(1, "W", "IP Address list creation was cancelled");
                tc.setDisabled();
            }
        });
        dialog.show();

        mUtil.addDebugMsg(1, "I", "Scan IP address range is " + subnet + "." + begin_addr + " - " + end_addr);

        mScanRequestedAddrList.clear();

        final String scan_prog = mContext.getString(R.string.msgs_scan_netowrk_smb_server_scan_progress);
        String p_txt = String.format(scan_prog, 0);
        tvmsg.setText(p_txt);

        Thread th=new Thread(new Runnable() {
            @Override
            public void run() {//non UI thread
                mScanCompleteCount = 0;
                mScanAddrCount = end_addr - begin_addr + 1;
                int scan_thread = 254;//60;
                String scan_port = "";
                if (ctv_use_port_number.isChecked())
                    scan_port = et_port_number.getText().toString();
                for (int i = begin_addr; i <= end_addr; i += scan_thread) {
                    if (!tc.isEnabled()) break;
                    boolean scan_end = false;
                    for (int j = i; j < (i + scan_thread); j++) {
                        if (j <= end_addr) {
                            startRemoteNetworkScanThread(handler, tc, dialog, p_ntfy,
                                    lv_ipaddr, adap, tvmsg, subnet + "." + j, ipAddressList, scan_port);
                        } else {
                            scan_end = true;
                        }
                    }
                    if (!scan_end) {
                        for (int wc = 0; wc < 210; wc++) {
                            if (!tc.isEnabled()) break;
                            SystemClock.sleep(30);
                        }
                    }
                }
                if (!tc.isEnabled()) {
                    for (int i = 0; i < 1000; i++) {
                        SystemClock.sleep(100);
                        synchronized (mScanRequestedAddrList) {
                            if (mScanRequestedAddrList.size() == 0) break;
                        }
                    }
                    handler.post(new Runnable() {// UI thread
                        @Override
                        public void run() {
                            closeScanRemoteNetworkProgressDlg(dialog, p_ntfy, lv_ipaddr, adap, tvmsg);
                        }
                    });
                } else {
                    for (int i = 0; i < 1000; i++) {
                        SystemClock.sleep(100);
                        synchronized (mScanRequestedAddrList) {
                            if (mScanRequestedAddrList.size() == 0) break;
                        }
                    }
                    handler.post(new Runnable() {// UI thread
                        @Override
                        public void run() {
                            synchronized (mLockScanCompleteCount) {
                                lv_ipaddr.setSelection(lv_ipaddr.getCount());
                                adap.notifyDataSetChanged();
                                closeScanRemoteNetworkProgressDlg(dialog, p_ntfy, lv_ipaddr, adap, tvmsg);
                            }
                        }
                    });
                }
            }
        });
        th.start();
    }

    private void closeScanRemoteNetworkProgressDlg(
            final Dialog dialog,
            final NotifyEvent p_ntfy,
            final ListView lv_ipaddr,
            final AdapterSmbServerList adap,
            final TextView tvmsg) {
        final LinearLayout ll_addr = (LinearLayout) dialog.findViewById(R.id.scan_remote_ntwk_scan_address);
        final LinearLayout ll_prog = (LinearLayout) dialog.findViewById(R.id.scan_remote_ntwk_progress);
        final Button btn_scan = (Button) dialog.findViewById(R.id.scan_remote_ntwk_btn_ok);
        final Button btn_cancel = (Button) dialog.findViewById(R.id.scan_remote_ntwk_btn_cancel);
        ll_addr.setVisibility(LinearLayout.VISIBLE);
        ll_prog.setVisibility(LinearLayout.GONE);
        btn_scan.setVisibility(Button.VISIBLE);
        btn_cancel.setVisibility(Button.VISIBLE);
        adap.setButtonEnabled(true);
        dialog.setOnKeyListener(null);
        dialog.setCancelable(true);
        if (p_ntfy != null) p_ntfy.notifyToListener(true, null);

    }

    private void startRemoteNetworkScanThread(final Handler handler,
                                              final ThreadCtrl tc,
                                              final Dialog dialog,
                                              final NotifyEvent p_ntfy,
                                              final ListView lv_ipaddr,
                                              final AdapterSmbServerList adap,
                                              final TextView tvmsg,
                                              final String addr,
                                              final ArrayList<AdapterSmbServerList.NetworkScanListItem> ipAddressList,
                                              final String scan_port) {
        final String scan_prog = mContext.getString(R.string.msgs_scan_netowrk_smb_server_scan_progress);
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (mScanRequestedAddrList) {
                    mScanRequestedAddrList.add(addr);
                }
                if (isIpAddrSmbHost(addr, scan_port)) {
                    final String srv_name = JcifsUtil.getSmbHostNameByAddress(mSmbLevel, addr);
                    handler.post(new Runnable() {// UI thread
                        @Override
                        public void run() {
                            synchronized (mScanRequestedAddrList) {
                                mScanRequestedAddrList.remove(addr);
                                AdapterSmbServerList.NetworkScanListItem li = new AdapterSmbServerList.NetworkScanListItem();
                                li.server_address = addr;
                                li.server_name = srv_name;
                                ipAddressList.add(li);
                                Collections.sort(ipAddressList, new Comparator<AdapterSmbServerList.NetworkScanListItem>() {
                                    @Override
                                    public int compare(AdapterSmbServerList.NetworkScanListItem lhs,
                                                       AdapterSmbServerList.NetworkScanListItem rhs) {
                                        String r_o4 = rhs.server_address.substring(rhs.server_address.lastIndexOf(".") + 1);
                                        String r_key = String.format("%3s", Integer.parseInt(r_o4)).replace(" ", "0");
                                        String l_o4 = lhs.server_address.substring(lhs.server_address.lastIndexOf(".") + 1);
                                        String l_key = String.format("%3s", Integer.parseInt(l_o4)).replace(" ", "0");
//										Log.v("","r="+r_key+", l="+l_key);
                                        return l_key.compareTo(r_key);
                                    }
                                });
                                adap.notifyDataSetChanged();
                            }
                            synchronized (mLockScanCompleteCount) {
                                mScanCompleteCount++;
                            }
                        }
                    });
                } else {
                    synchronized (mScanRequestedAddrList) {
//						Log.v("","addr="+addr+", contained="+mScanRequestedAddrList.contains(addr));
                        mScanRequestedAddrList.remove(addr);
                    }
                    synchronized (mLockScanCompleteCount) {
                        mScanCompleteCount++;
                    }
                }
                handler.post(new Runnable() {// UI thread
                    @Override
                    public void run() {
                        synchronized (mLockScanCompleteCount) {
                            lv_ipaddr.setSelection(lv_ipaddr.getCount());
                            adap.notifyDataSetChanged();
                            String p_txt = String.format(scan_prog,
                                    (mScanCompleteCount * 100) / mScanAddrCount);
                            tvmsg.setText(p_txt);
                        }
                    }
                });
            }
        });
        th.setName(addr);
        th.start();
    }

    private boolean isIpAddrSmbHost(String address, String scan_port) {
        boolean smbhost = false;
        if (scan_port.equals("")) {
            if (!isIpAddressAndPortConnected(address, 445, 3000)) {
                smbhost = isIpAddressAndPortConnected(address, 139, 3000);
            } else smbhost = true;
        } else {
            smbhost = isIpAddressAndPortConnected(address, Integer.parseInt(scan_port), 3000);
        }
        mUtil.addDebugMsg(2, "I", "isIpAddrSmbHost Address=" + address +
                ", port=" + scan_port + ", smbhost=" + smbhost);
        return smbhost;
    }

    static private boolean isIpAddressAndPortConnected(String address, int port, int timeout) {
        boolean reachable = false;
        Socket socket = new Socket();
        try {
            socket.bind(null);
            socket.connect((new InetSocketAddress(address, port)), timeout);
            reachable = true;
            socket.close();
        } catch (IOException e) {
//        	e.printStackTrace();
        } catch (Exception e) {
//        	e.printStackTrace();
        }
        return reachable;
    }

    private boolean auditScanAddressRangeValue(Dialog dialog) {
        boolean result = false;
        final EditText baEt1 = (EditText) dialog.findViewById(R.id.scan_remote_ntwk_begin_address_o1);
        final EditText baEt2 = (EditText) dialog.findViewById(R.id.scan_remote_ntwk_begin_address_o2);
        final EditText baEt3 = (EditText) dialog.findViewById(R.id.scan_remote_ntwk_begin_address_o3);
        final EditText baEt4 = (EditText) dialog.findViewById(R.id.scan_remote_ntwk_begin_address_o4);
        final EditText eaEt4 = (EditText) dialog.findViewById(R.id.scan_remote_ntwk_end_address_o4);
        final TextView tvmsg = (TextView) dialog.findViewById(R.id.scan_remote_ntwk_msg);

        String ba1 = baEt1.getText().toString();
        String ba2 = baEt2.getText().toString();
        String ba3 = baEt3.getText().toString();
        String ba4 = baEt4.getText().toString();
        String ea4 = eaEt4.getText().toString();

        tvmsg.setText("");
        if (ba1.equals("")) {
            tvmsg.setText(mContext.getString(R.string.msgs_ip_address_range_dlg_begin_notspecified));
            baEt1.requestFocus();
            return false;
        } else if (ba2.equals("")) {
            tvmsg.setText(mContext.getString(R.string.msgs_ip_address_range_dlg_begin_notspecified));
            baEt2.requestFocus();
            return false;
        } else if (ba3.equals("")) {
            tvmsg.setText(mContext.getString(R.string.msgs_ip_address_range_dlg_begin_notspecified));
            baEt3.requestFocus();
            return false;
        } else if (ba4.equals("")) {
            tvmsg.setText(mContext.getString(R.string.msgs_ip_address_range_dlg_begin_notspecified));
            baEt4.requestFocus();
            return false;
        } else if (ea4.equals("")) {
            tvmsg.setText(mContext.getString(R.string.msgs_ip_address_range_dlg_end_notspecified));
            eaEt4.requestFocus();
            return false;
        }
        int iba1 = Integer.parseInt(ba1);
        if (iba1 > 255) {
            tvmsg.setText(mContext.getString(R.string.msgs_ip_address_range_dlg_addr_range_error));
            baEt1.requestFocus();
            return false;
        }
        int iba2 = Integer.parseInt(ba2);
        if (iba2 > 255) {
            tvmsg.setText(mContext.getString(R.string.msgs_ip_address_range_dlg_addr_range_error));
            baEt2.requestFocus();
            return false;
        }
        int iba3 = Integer.parseInt(ba3);
        if (iba3 > 255) {
            tvmsg.setText(mContext.getString(R.string.msgs_ip_address_range_dlg_addr_range_error));
            baEt3.requestFocus();
            return false;
        }
        int iba4 = Integer.parseInt(ba4);
        int iea4 = Integer.parseInt(ea4);
        if (iba4 > 0 && iba4 < 255) {
            if (iea4 > 0 && iea4 < 255) {
                if (iba4 <= iea4) {
                    result = true;
                } else {
                    baEt4.requestFocus();
                    tvmsg.setText(mContext.getString(R.string.msgs_ip_address_range_dlg_begin_addr_gt_end_addr));
                }
            } else {
                eaEt4.requestFocus();
                tvmsg.setText(mContext.getString(R.string.msgs_ip_address_range_dlg_end_range_error));
            }
        } else {
            baEt4.requestFocus();
            tvmsg.setText(mContext.getString(R.string.msgs_ip_address_range_dlg_begin_range_error));
        }

        if (iba1 == 192 && iba2 == 168) {
            //class c private
        } else {
            if (iba1 == 10) {
                //class a private
            } else {
                if (iba1 == 172 && (iba2 >= 16 && iba2 <= 31)) {
                    //class b private
                } else {
                    //not private
                    result = false;
                    tvmsg.setText(mContext.getString(R.string.msgs_ip_address_range_dlg_not_private));
                }
            }
        }

        return result;
    }

    private static String getLocalIpAddress() {
        String result = "";
        boolean exit = false;
        try {
            for (Enumeration<NetworkInterface> en =
                 NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr =
                     intf.getInetAddresses();
                     enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (inetAddress.isSiteLocalAddress() && (inetAddress instanceof Inet4Address)) {
                        result = inetAddress.getHostAddress();
                        if (intf.getName().equals("wlan0")) {
                            exit = true;
                            break;
                        }
                    }
                }
                if (exit) break;
            }
        } catch (SocketException ex) {
            result = "192.168.0.1";
        }
        if (result.equals("")) result = "192.168.0.1";
        return result;
    }

}
