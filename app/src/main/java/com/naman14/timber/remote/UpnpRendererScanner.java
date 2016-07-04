package com.naman14.timber.remote;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

public class UpnpRendererScanner {
    private Thread mWorkerThread;
    ScannerResult mCallback;
    MulticastSocket mSocket;

    final static String SERVICE_NAME = "urn:schemas-upnp-org:device:MediaRenderer:1";
    final static byte[] SEARCH_REQUEST = ("M-SEARCH * HTTP/1.1\r\nHost: 239.255.255.250:1900\r\nST: " + SERVICE_NAME + "\r\nMan: \"ssdp:discover\"\r\nMX: 3\r\n\r\n").getBytes();

    private class ScannerWorker implements Runnable {
        @Override
        public void run() {

            try {
                ArrayList<String> Urls = new ArrayList<>();
                InetAddress addr = Inet4Address.getByAddress(new byte[]{(byte) 239, (byte) 255, (byte) 255, (byte) 250});
                mSocket = new MulticastSocket(1900);
                mSocket.setReuseAddress(true);
                mSocket.joinGroup(addr);
                mSocket.setSoTimeout(2000);
                search(addr);
                while (true) {
                    byte[] buf = new byte[1000];
                    DatagramPacket inPack = new DatagramPacket(buf, buf.length);
                    try {
                        mSocket.receive(inPack);
                    } catch (SocketTimeoutException e) {
                        search(addr);
                    }
                    byte[] resb = inPack.getData();
                    int end;
                    for (end = 0; resb[end] != '\0'; end++) ;
                    String[] lines = new String(resb, 0, end).split("\\r?\\n");
                    boolean isRenderer = false;
                    String Url = null;
                    for (int i = 0; i < lines.length; i++) {
                        String str = lines[i].toUpperCase();
                        if (((str.startsWith("NT")) || (str.startsWith("ST"))) && lines[i].contains(SERVICE_NAME))
                            isRenderer = true;
                        if (str.startsWith("LOCATION")) {
                            Url = lines[i].substring(str.indexOf(' '), str.length());
                        }
                    }

                    if (isRenderer && Url != null && (!Urls.contains(Url))) {
                        Urls.add(Url);
                        try {
                            UpnpRenderer rend = new UpnpRenderer(Url);
                            UpnpRendererScanner.this.mCallback.onRenderFound(rend);
                        } catch (RemoteException e) {
                        }
                    }
                }

            } catch (SocketException e) {

            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        private void search(InetAddress addr) throws IOException {
            DatagramPacket searchPack = new DatagramPacket(SEARCH_REQUEST, SEARCH_REQUEST.length, addr, 1900);
            mSocket.send(searchPack);
        }

    }

    public interface ScannerResult {
        void onRenderFound(UpnpRenderer rend);
    }

    public void startScan(ScannerResult callback) {
        this.mCallback = callback;
        if (mWorkerThread != null) return;
        mWorkerThread = new Thread(new ScannerWorker());
        mWorkerThread.start();

    }

    public void stopScan() {
        if (mWorkerThread == null) return;
        if (!mWorkerThread.isAlive()) return;
        mSocket.close();
    }
}
