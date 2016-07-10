package com.naman14.timber.remote;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by Christoph on 21.01.2016.
 */
public class Server extends NanoHTTPD {
    private static Server server;
    final String ip;

    private class UriInfo {
        String uri;
        String info;
        boolean transcode;
    }

    private IRemoteEvent.RemoteState lastState;
    private IRemoteEvent eventCallback;
    Hashtable<String, UriInfo> resou = new Hashtable<String, UriInfo>();


    Method meth;

    public static Server getServer() {
        if (server == null) try {
            (server = new Server()).start();
        } catch (IOException e) {
        }
        return server;
    }

    private Server() {
        super(45840);
        this.ip = getDeviceIP().getHostAddress();

    }

    static InetAddress getDeviceIP() {

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {

                NetworkInterface intf = en.nextElement();

                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {

                    InetAddress inetAdress = enumIpAddr.nextElement();
                    if (!inetAdress.isLoopbackAddress() && inetAdress instanceof Inet4Address) {

                        return inetAdress;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    String addResource(String path, String info) {
        return addResource(path, info, false);
    }

    String addResource(String path, String info, boolean transcode) {
        int last = path.lastIndexOf('.');
        String uri = String.valueOf(new Random().nextInt(100000000));
        if (!transcode && last > path.length() - 7) uri += path.substring(last);
        else if (transcode) uri += ".lpcm";
        UriInfo ui = new UriInfo();
        ui.uri = path;
        ui.info = info;
        ui.transcode = transcode;
        resou.put(uri, ui);
        Log.d("added", uri);
        return "http://" + ip + ":45840/media/" + uri;
    }

    public void setEventListener(IRemoteEvent eventCallback) {
        this.eventCallback = eventCallback;
    }

    private Response sendFile(String uri, Method meth) {
        if (uri == null || "".equals(uri)) return null;
        UriInfo ui = resou.get(uri);
        Log.d("send", uri + " " + ui.transcode);
        String filename = ui.uri;
        if (filename == null) return null;
        try {
            File file = new File(filename);
            Response res;
            long len = file.length();
            if (ui.info != null) {
                String[] meta = ui.info.split(":");
                if (meth == Method.GET) {
                    if (ui.transcode) {
                        return new PCMUtils.TrancodingResponse(ui.uri, meta[3]);
                    }
                    InputStream in = new FileInputStream(file);
                    res = new Response(Response.Status.OK, meta[2], in, len);
                } else if (meth == Method.HEAD) {
                    res = new Response(Response.Status.OK, meta[2], "");
                } else return null;

                if (meta.length > 3) res.addHeader("ContentFeatures.DLNA.ORG", meta[3]);
            } else {
                InputStream in = new FileInputStream(file);
                res = new Response(Response.Status.OK, "image/jpeg", in, len);
            }
            if (!ui.transcode) res.addHeader("Content-Range", "bytes 0-" + len + "/" + len);
            res.addHeader("Scid.DLNA.ORG", "134252567");
            res.addHeader("TransferMode.DLNA.ORG", "Streaming");
            return res;
        } catch (FileNotFoundException e) {
            return null;
        }


    }

    private void parseFrameEvent(IHTTPSession session) {

        try {
            XmlPullParserFactory xmlFactoryObject = XmlPullParserFactory.newInstance();
            XmlPullParser myParser = xmlFactoryObject.newPullParser();
            myParser.setInput(session.getInputStream(), null);
            int event = myParser.getEventType();
            boolean change = false;
            while (event != XmlPullParser.END_DOCUMENT) {
                String name = myParser.getName();

                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (name.equalsIgnoreCase("LastChange")) change = true;
                        break;

                    case XmlPullParser.END_TAG:
                        if (name.equalsIgnoreCase("LastChange")) change = false;
                        break;
                    case XmlPullParser.TEXT:
                        if (change) {
                            session.getInputStream().close();
                            parseEvent(myParser.getText());
                            return;
                        }
                        break;
                }
                event = myParser.next();
            }
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        }

    }

    void parseEvent(String str) {
        try {
            XmlPullParserFactory xmlFactoryObject = XmlPullParserFactory.newInstance();
            XmlPullParser myParser = xmlFactoryObject.newPullParser();
            myParser.setInput(new ByteArrayInputStream(str.getBytes(Charset.defaultCharset())), null);
            int event = myParser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                String name = myParser.getName();

                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (name.equalsIgnoreCase("TransportState")) {
                            IRemoteEvent.RemoteState state = IRemoteEvent.RemoteState.valueOf(myParser.getAttributeValue(null, "val"));
                            if (state != null && state != lastState) {
                                eventCallback.onStateChange(state);
                                lastState = state;
                            }

                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if (name.equalsIgnoreCase("TransportState"))
                            break;
                    case XmlPullParser.TEXT:

                        break;
                }
                event = myParser.next();
            }

        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public Response serve(IHTTPSession session) {
        String[] uri = session.getUri().split("/");
        Log.d(session.getMethod().toString(), session.getUri());
        if (uri.length > 2) {
            switch (uri[1]) {
                case "media":
                    Response res = sendFile(uri[2], session.getMethod());
                    if (res != null) return res;
                    break;
                case "event":
                    parseFrameEvent(session);
                    return new Response(Response.Status.OK, "text/xml", "");


            }
        }
        return new Response(Response.Status.NOT_FOUND, "text/plain", "404");
    }

}
