package com.naman14.timber.remote;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

/**
 * Created by Christoph on 10.07.2016.
 */

public class AirPlayScanner implements ServiceListener {
    private IRemoteScanFound mCallback;
    private JmDNS jmdns;
    private Thread thread;
    WifiManager.MulticastLock lock;
    ArrayList<String>FoundDevices = new ArrayList<>();

    public void startScan(IRemoteScanFound callback, final Context con) {
        this.mCallback = callback;

        thread = new Thread() {
            @Override
            public void run() {
                try {
                    WifiManager wifi = (WifiManager) con.getSystemService(android.content.Context.WIFI_SERVICE);
                    lock = wifi.createMulticastLock("timber");
                    lock.setReferenceCounted(true);
                    lock.acquire();
                    jmdns = JmDNS.create(Server.getDeviceIP());
                    jmdns.addServiceListener("_raop._tcp.local.", AirPlayScanner.this);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();


    }

    public void stopScan() {
        if (jmdns != null) try {
            jmdns.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (thread != null && thread.isAlive()) thread.interrupt();
        lock.release();
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {

    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        Log.d("AirplayR", event.toString());
        javax.jmdns.ServiceInfo info = event.getInfo();
        if(FoundDevices.contains(info.getName()))return;
        FoundDevices.add(info.getName());
        mCallback.onRenderFound(new AirPlay(info));
    }
}
