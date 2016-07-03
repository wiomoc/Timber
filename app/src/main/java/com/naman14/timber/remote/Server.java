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
import java.nio.charset.Charset;
import java.util.Hashtable;
import java.util.Random;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by Christoph on 21.01.2016.
 */
public class Server extends NanoHTTPD {
    private class UriInfo {
        String uri;
        String info;
    }

    private IRemoteEvent.RemoteState lastState;
    private IRemoteEvent eventCallback;
    Hashtable<String, UriInfo> resou = new Hashtable<String, UriInfo>();


    Method meth;

    public Server() {
        super(45840);
    }

    String addResource(String path, String info) {
        String uri = String.valueOf(new Random().nextInt(1000000)) + path.substring(path.lastIndexOf('/') + 1);
        UriInfo ui = new UriInfo();
        ui.uri = path;
        ui.info = info;
        resou.put(uri, ui);
        return uri;
    }

    public void setEventListener(IRemoteEvent eventCallback) {
        this.eventCallback = eventCallback;
    }

    private Response sendFile(String uri, Method meth) {
        if (uri == null || "".equals(uri)) return null;
        UriInfo ui = resou.get(uri);
        String filename = ui.uri;
        if (filename == null) return null;
        try {
            File file = new File(filename);

            InputStream in = new FileInputStream(file);
            String[] meta = ui.info.split(":");
            Response res;
            long len = file.length();
            if (meth == Method.GET) res = new Response(Response.Status.OK, meta[2], in, len);
            else if (meth == Method.HEAD) res = new Response(Response.Status.OK, meta[2], "");
            else return null;

            if (meta.length > 3) res.addHeader("ContentFeatures.DLNA.ORG", meta[3]);
            res.addHeader("Content-Range", "bytes 0-" + len + "/" + len);
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
