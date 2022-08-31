package com.survey360.quadcoptercontroluv.Utils.Communication;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.widget.Toast;

import com.survey360.quadcoptercontroluv.Utils.PermissionsRequest;

import java.lang.reflect.Method;

/**
 * Created by AAstudillo on 18/09/2017.
 */

public class CommunicationManager {

    PermissionsRequest mPermissions;
    public static Context ctx;
    public static Activity act;
    WifiConfiguration wc;

    public CommunicationManager(Context context, Activity activity){
        ctx = context;
        act = activity;
        mPermissions = new PermissionsRequest(ctx, act);
        mPermissions.SettingPermission();

        wc = new WifiConfiguration();
    }

    private boolean changeWifiHotspotState(Context context,boolean enable) {
        try {
            WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            Method method = manager.getClass().getDeclaredMethod("setWifiApEnabled", WifiConfiguration.class,
                    Boolean.TYPE);
            method.setAccessible(true);

            /*Method[] methods = manager.getClass().getDeclaredMethods();
            for (Method m: methods) {
                if (m.getName().equals("getWifiApConfiguration")) {
                    wc = (WifiConfiguration)m.invoke(manager);
                }
            }*/

            boolean isSuccess = (Boolean) method.invoke(manager, wc, enable);
            return isSuccess;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void startHotspot(){
        changeWifiHotspotState(ctx,true);
        Toast.makeText(ctx, "Hotspot ON", Toast.LENGTH_SHORT).show();
    }

    public void stopHotspot(){
        changeWifiHotspotState(ctx,false);
        Toast.makeText(ctx, "Hotspot OFF", Toast.LENGTH_SHORT).show();
    }

    public void configureHotspot(String SSID, String passwordHotspot){
        if(passwordHotspot==""){
            wc.SSID = SSID;
            wc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            wc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            //wc.status = WifiConfiguration.Status.ENABLED;
            wc.status = WifiConfiguration.Status.CURRENT;
        }else{
            wc.SSID = SSID ;
            wc.preSharedKey = passwordHotspot;
            wc.hiddenSSID = false;
            //wc.status = WifiConfiguration.Status.ENABLED;
            wc.status = WifiConfiguration.Status.CURRENT;
            wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            wc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);


            /*WifiConfiguration config = new WifiConfiguration();
            config.ipAssignment = WifiConfiguration.IpAssignment.UNASSIGNED;
            config.proxySettings = WifiConfiguration.ProxySettings.STATIC;
            config.linkProperties.setHttpProxy(new ProxyProperties("127.0.0.1", 3128, ""));*/
        }
    }
}
