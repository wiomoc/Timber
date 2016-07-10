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
    IRemoteScanFound mCallback;
    MulticastSocket mSocket;
    ArrayList<String> Urls = new ArrayList<>();
    final static String SERVICE_NAME = "urn:schemas-upnp-org:device:MediaRenderer:1";
    final static byte[] SEARCH_REQUEST0 = ("M-SEARCH * HTTP/1.1\r\nST: " + SERVICE_NAME + "\r\nHost: 239.255.255.250:1900\nMan: \"ssdp:discover\"\r\nMX: 3\r\n\r\n").getBytes();
    final static byte[] SEARCH_REQUEST1 = ("M-SEARCH * HTTP/1.1\r\nST: " + UpnpRenderer.AVTRANSPORT + "\r\nHost: 239.255.255.250:1900\r\nMan: \"ssdp:discover\"\r\nMX: 3\r\n\r\n").getBytes();

    private class ScannerWorker implements Runnable {
        @Override
        public void run() {

            try {

                InetAddress addr = Inet4Address.getByAddress(new byte[]{(byte) 239, (byte) 255, (byte) 255, (byte) 250});
                mSocket = new MulticastSocket(1900);
                mSocket.setReuseAddress(true);
                mSocket.joinGroup(addr);
                mSocket.setSoTimeout(1000);
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
            DatagramPacket searchPack = new DatagramPacket(SEARCH_REQUEST0, SEARCH_REQUEST0.length, addr, 1900);
            mSocket.send(searchPack);
            searchPack = new DatagramPacket(SEARCH_REQUEST1, SEARCH_REQUEST1.length, addr, 1900);
            mSocket.send(searchPack);
        }

    }

    public void startScan(IRemoteScanFound callback) {
        this.mCallback = callback;
        if (mWorkerThread != null) return;
        mWorkerThread = new Thread(new ScannerWorker());
        mWorkerThread.start();

    }

    public void stopScan() {
        if (mWorkerThread == null) return;
        if (!mWorkerThread.isAlive()) return;
        mSocket.close();
        mWorkerThread=null;
    }
}
