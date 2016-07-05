package com.naman14.timber.remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ProtocolException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class UpnpRenderer implements IRemote, Runnable {
    @Override
    public void run() {
        synchronized (queue) {
            while (true) {
                try {
                    Request request = queue.poll();

                    if (request != null) {
                        InputStream in = sendXMLPost(request.urls, request.content, request.action);
                        if (request.runnable != null) request.runnable.run(in);
                    }
                    queue.wait();
                } catch (IOException ignored) {

                } catch (InterruptedException e) {
                    break;
                }
            }
        }

    }

    interface RequestRunnable {
        void run(InputStream in);
    }

    private class Request {
        String urls;
        String content;
        String action;
        RequestRunnable runnable;
    }

    private class VolumeUpdater implements RequestRunnable {
        int vol;

        VolumeUpdater(int vol) {
            this.vol = vol;
        }

        @Override
        public void run(InputStream in) {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = null;
                db = dbf.newDocumentBuilder();
                Document doc = db.parse(in);
                vol += Integer.valueOf(doc.getElementsByTagName("CurrentVolume").item(0).getTextContent());
                sendRequest(UpnpRenderer.this.controlRC, "SetVolume", "<Channel>MASTER</Channel><DesiredVolume>"+vol+"</DesiredVolume>", RENDERINGCONTROL, true, false, null);

            } catch (ParserConfigurationException | IOException | SAXException e) {
            }


        }
    }

    String SCPDRC = null;
    String controlRC = null;
    String eventRC = null;
    String SCPDAVT = null;
    String controlAVT = null;
    String eventAVT = null;
    String SCPDCON = null;
    String controlCON = null;
    private Queue<Request> queue = new LinkedList<>();
    long positionLast = 0;
    int positionOffset = 0;
    long positionStart = 0;
    private Thread thread;
    private Context mContext;
    private String mIp;
    private static Server mServer = null;
    private String mInstanceID = "0";
    private String mName;
    private String mImgUrl;
    private HashMap<String, String> mSuppFormats = null;
    final static String AVTRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1";
    final static String CONNECTIONMANAGER = "urn:schemas-upnp-org:service:ConnectionManager:1";
    final static String RENDERINGCONTROL = "urn:schemas-upnp-org:service:RenderingControl:1";
    final static String SET_MEDIA0 = "<CurrentURI xmlns:dt=\"urn:schemas-microsoft-com:datatypes\" dt:dt=\"string\">";
    final static String SET_MEDIA1 = "</CurrentURI><CurrentURIMetaData ";
    final static String SET_MEDIA2 = "</CurrentURIMetaData>";
    final static String META0 = "xmlns:dt=\"urn:schemas-microsoft-com:datatypes\" dt:dt=\"string\">&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot;?&gt;&lt;DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:microsoft=\"urn:schemas-microsoft-com:WMPNSS-1-0/\" xmlns:dlna=\"urn:schemas-dlna-org:metadata-1-0/\"&gt;&lt;item&gt;";
    final static String META_TITLE0 = "&lt;dc:title&gt;";
    final static String META_TITLE1 = "&lt;/dc:title&gt;";
    final static String META_ARTIST0 = "&lt;dc:creator&gt;";
    final static String META_ARTIST1 = "&lt;/dc:creator&gt;" +
            "&lt;desc id=\"artist\" nameSpace=\"urn:schemas-microsoft-com:WMPNSS-1-0/\" xmlns:microsoft=\"urn:schemas-microsoft-com:WMPNSS-1-0/\"&gt;" +
            "&lt;microsoft:artistPerformer&gt;";
    final static String META_ARTIST2 = "&lt;/microsoft:artistPerformer&gt;" +
            "&lt;/desc&gt;" +
            "&lt;upnp:artist role=\"Performer\"&gt;";
    final static String META_ARTIST3 = "&lt;/upnp:artist&gt;";


    final static String META_ALBUM0 = "&lt;upnp:album&gt;";
    final static String META_ALBUM1 = "&lt;/upnp:album&gt;";


    final static String META1 = "&lt;upnp:toc&gt;F+96+6B4E+BEF2+109C8+15699+1B0EB+1FFAB+23F5A+29621+2FF94+363DF+3AA98+403F0+45CC2+4A9E8+5004D&lt;/upnp:toc&gt;&lt;upnp:class&gt;object.item.audioItem.musicTrack&lt;/upnp:class&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;";


    UpnpRenderer(String urlStr) throws RemoteException {
        try {
            Log.d("url", urlStr);

            String baseURL = urlStr.substring(0, urlStr.indexOf('/', 8));
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new URL(urlStr).openStream());
            NodeList serviceList = doc.getElementsByTagName("service");
            for (int i = 0; i < serviceList.getLength(); i++) {
                Element ele = (Element) serviceList.item(i);
                String type = ele.getElementsByTagName("serviceType").item(0).getTextContent();
                switch (type) {
                    case "urn:schemas-upnp-org:service:RenderingControl:1":
                        SCPDRC = ele.getElementsByTagName("SCPDURL").item(0).getTextContent();
                        controlRC = ele.getElementsByTagName("controlURL").item(0).getTextContent();
                        eventRC = ele.getElementsByTagName("eventSubURL").item(0).getTextContent();
                        break;
                    case "urn:schemas-upnp-org:service:AVTransport:1":
                        SCPDAVT = ele.getElementsByTagName("SCPDURL").item(0).getTextContent();
                        controlAVT = ele.getElementsByTagName("controlURL").item(0).getTextContent();
                        eventAVT = ele.getElementsByTagName("eventSubURL").item(0).getTextContent();
                        break;
                    case "urn:schemas-upnp-org:service:ConnectionManager:1":
                        SCPDCON = ele.getElementsByTagName("SCPDURL").item(0).getTextContent();
                        controlCON = ele.getElementsByTagName("controlURL").item(0).getTextContent();
                        break;
                }
            }
            if (SCPDAVT == null || SCPDRC == null || controlAVT == null || controlRC == null || eventAVT == null || eventRC == null)
                throw new RemoteException();
            if (controlAVT.startsWith("/")) controlAVT = baseURL + controlAVT;
            if (controlRC.startsWith("/")) controlRC = baseURL + controlRC;
            if (eventAVT.startsWith("/")) eventAVT = baseURL + eventAVT;
            if (eventRC.startsWith("/")) eventRC = baseURL + eventRC;
            if (SCPDAVT.startsWith("/")) SCPDAVT = baseURL + SCPDAVT;
            if (SCPDRC.startsWith("/")) SCPDRC = baseURL + SCPDRC;
            if (SCPDCON != null) if (SCPDCON.startsWith("/")) SCPDCON = baseURL + SCPDCON;
            if (controlCON != null)
                if (controlCON.startsWith("/")) controlCON = baseURL + controlCON;
            mName = doc.getElementsByTagName("friendlyName").item(0).getTextContent();
            mImgUrl = ((Element) doc.getElementsByTagName("iconList").item(0)).getElementsByTagName("url").item(0).getTextContent();
            if (mImgUrl.startsWith("/")) mImgUrl = baseURL + mImgUrl;
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new RemoteException();
        }
        mIp = getDeviceIP();

        getSupportedFormats();
        registerEvent(eventAVT, false);
        registerEvent(eventRC, false);
        for (Map.Entry<String, String> ent : mSuppFormats.entrySet()) {
            Log.d(ent.getKey(), ent.getValue());
        }
    }

    public void setEventListener(IRemoteEvent event) {
        if (mServer == null) try {
            (mServer = new Server()).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mServer.setEventListener(event);

    }

    public void close() {
        registerEvent(eventAVT, true);
        registerEvent(eventRC, true);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }

    private void registerEvent(String urlstr, boolean unregister) {
        try {
            URL url = new URL(urlstr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            if (mServer == null) (mServer = new Server()).start();
            connection.setDoInput(true);
            //connection.setDoOutput(true);
            connection.setUseCaches(false);
            if (unregister) setMethod(connection, "UNSUBSCRIBE");
            else setMethod(connection, "SUBSCRIBE");
            Log.d("meth", connection.getRequestMethod());
            connection.setRequestProperty("NT", "upnp:event");
            connection.setRequestProperty("Timeout", "Second-86400");
            if (!unregister)
                connection.setRequestProperty("Callback", "<http://" + mIp + ":45840/event/123>");

            connection.getInputStream();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void getSupportedFormats() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(sendRequest(this.controlCON, "GetProtocolInfo", null, CONNECTIONMANAGER, false, false, null));
            String res = doc.getElementsByTagName("Sink").item(0).getTextContent();
            if (res == null) return;
            String[] res2 = res.split(",");
            mSuppFormats = new HashMap<String, String>();
            for (String res3 : res2) {
                Log.d("FORMATS", res3);
                if (res3.contains("http-get") && res3.contains("audio")) {
                    if (res3.contains("mp3") || res3.contains("MP3")) {
                        mSuppFormats.put("mp3", res3);
                    } else if (res3.contains("wma")) {
                        mSuppFormats.put("wma", res3);
                    } else if (res3.contains("wav")) {
                        mSuppFormats.put("wav", res3);
                    } else if (res3.contains("m4a") || res3.contains("mp4")) {
                        mSuppFormats.put("mp4", res3);
                        mSuppFormats.put("m4a", res3);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private InputStream sendRequest(String url, String op, String data, String context, boolean sendInstanceID, boolean async, RequestRunnable runnable) throws IOException {
        String req = "<?xml version=\"1.0\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\" SOAP-ENV:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><SOAP-ENV:Body><m:" + op + " xmlns:m=\"" + context + "\"" + ((data == null && (!sendInstanceID)) ? "/" : "") + ">";
        if (sendInstanceID)
            req += "<InstanceID xmlns:dt=\"urn:schemas-microsoft-com:datatypes\" dt:dt=\"ui4\">" + mInstanceID + "</InstanceID>";
        if (data != null || sendInstanceID) {
            if (data != null) req += data;
            req += "</m:" + op + ">";
        }
        req += "</SOAP-ENV:Body></SOAP-ENV:Envelope>";
        if (async) {
            sendXMLPostAsync(url, req, "\"" + context + "#" + op + "\"", runnable);
            return null;
        }
        return sendXMLPost(url, req, "\"" + context + "#" + op + "\"");

    }

    public Bitmap getImage() {
        Log.d("img", mImgUrl);
        InputStream instr = null;
        try {
            URL url = new URL(mImgUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            instr = connection.getInputStream();
            return BitmapFactory.decodeStream(instr);
        } catch (IOException e) {
            return null;
        } finally {
            if (instr != null) try {
                instr.close();
            } catch (IOException e) {
            }
        }
    }

    private void sendXMLPostAsync(String urls, String content, String action, RequestRunnable runnable) {
        Request request = new Request();
        request.urls = urls;
        request.content = content;
        request.action = action;
        request.runnable = runnable;
        synchronized (queue) {
            queue.add(request);
            queue.notify();
        }
        if (thread == null || !thread.isAlive()) {
            thread = new Thread(this);
            thread.start();
        }
    }

    private void loadPosition() {
        try {
            sendRequest(this.controlAVT, "GetPositionInfo", "", AVTRANSPORT, true, true, new RequestRunnable() {
                @Override
                public void run(InputStream in) {

                    try {
                        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                        DocumentBuilder db = dbf.newDocumentBuilder();
                        Document doc = db.parse(in);
                        String timestring = doc.getElementsByTagName("RelTime").item(0).getTextContent();

                        if (timestring != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                            positionOffset = (int) (sdf.parse(timestring).getTime());
                            positionStart = -1;
                        }
                        Log.d("POS", positionOffset + "  " + timestring);
                    } catch (ParserConfigurationException | IOException | SAXException | ParseException e) {
                    }


                }
            });
        } catch (IOException e) {

        }
    }

    public int getPosition() {
        long time = new Date().getTime();
        if (time > positionLast + 1000) {
            positionLast = time;
            loadPosition();
        }
        if (positionStart != -1) {
            return (int) (positionOffset + (time - positionStart));
        }

        return positionOffset;
    }

    private static InputStream sendXMLPost(String urls, String content, String action) throws IOException {
        URL url = new URL(urls);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "text/xml");
        connection.setRequestProperty("charset", "utf-8");
        connection.setRequestProperty("Content-Length", String.valueOf(content.length()));
        if (action != null) connection.setRequestProperty("SOAPAction", action);
        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
        wr.write(content.getBytes());
        wr.close();
        return connection.getInputStream();
    }

    public void setMedia(String file, String artist, String album, String title) {
        if (file == null || file.length() == 0) return;
        try {
            String ending = file.substring(file.lastIndexOf('.') + 1);
            String info = null;
            if (mSuppFormats != null) {
                info = mSuppFormats.get(ending);
            }
            if (mServer == null) (mServer = new Server()).start();

            String audioUrl = "http://" + mIp + ":45840/media/" + mServer.addResource(file, info);

            sendRequest(controlAVT, "SetAVTransportURI", SET_MEDIA0 + audioUrl + SET_MEDIA1 + getMeta(info, audioUrl, artist, album, title) + SET_MEDIA2, "urn:schemas-upnp-org:service:AVTransport:1", true, true, null);


        } catch (IOException e) {
            e.printStackTrace();
        }
        positionOffset = 0;
    }

    public void play() throws IOException {
        sendRequest(this.controlAVT, "Play", "<Speed xmlns:dt=\"urn:schemas-microsoft-com:datatypes\" dt:dt=\"string\">1</Speed>", AVTRANSPORT, true, true, null);
        positionStart = new Date().getTime();
        loadPosition();
    }

    public void pause() throws IOException {
        sendRequest(this.controlAVT, "Pause", "", AVTRANSPORT, true, true, null);
        positionOffset += new Date().getTime() - positionStart;
        positionStart = -1;
        loadPosition();
    }

    public void stop() throws IOException {
        sendRequest(this.controlAVT, "Stop", "", AVTRANSPORT, true, true, null);
        positionOffset += new Date().getTime() - positionStart;
        positionStart = -1;
    }

    public void seek(int secs) throws IOException {
        sendRequest(this.controlAVT, "Seek", "<Unit xmlns:dt=\"urn:schemas-microsoft-com:datatypes\" dt:dt=\"string\">REL_TIME</Unit><Target xmlns:dt=\"urn:schemas-microsoft-com:datatypes\" dt:dt=\"string\">" + secs / 3600 + ":" + secs / 60 + ":" + secs % 60 + "</Target>", AVTRANSPORT, true, true, null);
        positionOffset += secs;
    }

    @Override
    public void volumeChange(int vol) throws IOException {
        sendRequest(this.controlRC, "GetVolume", "<Channel>MASTER</Channel>", RENDERINGCONTROL, true, true, new VolumeUpdater(vol));
    }

    private String getMeta(String info, String url, String artist, String album, String title) {
        String tmp = "&lt;res duration=&quot;0:04:45.246&quot; bitrate=&quot;40000&quot; protocolInfo=&quot;" + info + "&quot; sampleFrequency=&quot;44100&quot; bitsPerSample=&quot;16&quot; nrAudioChannels=&quot;2&quot;&gt;" + url + "&lt;/res&gt;";
        String x = META0 + tmp;
        if (title != null) x += (META_TITLE0 + title + META_TITLE1);
        if (album != null) x += (META_ALBUM0 + album + META_ALBUM1);
        if (artist != null)
            x += (META_ARTIST0 + artist + META_ARTIST1 + artist + META_ARTIST2 + artist + META_ARTIST3);
        x += META1;
        return x;
    }

    private static String getDeviceIP() {

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {

                NetworkInterface intf = en.nextElement();

                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {

                    InetAddress inetAdress = enumIpAddr.nextElement();
                    if (!inetAdress.isLoopbackAddress() && inetAdress instanceof Inet4Address) {

                        return inetAdress.getHostAddress().toString();
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public String getName() {
        return this.mName;
    }

    static void setMethod(HttpURLConnection httpURLConnection, String method) {
        try {
            httpURLConnection.setRequestMethod(method);
        } catch (final ProtocolException pe) {
            try {
                final Class<?> httpURLConnectionClass = httpURLConnection
                        .getClass();
                final Class<?> parentClass = httpURLConnectionClass
                        .getSuperclass();
                final Field methodField;
                if (parentClass == HttpsURLConnection.class) {
                    methodField = parentClass.getSuperclass().getDeclaredField(
                            "method");
                } else {
                    methodField = parentClass.getDeclaredField("method");
                }
                methodField.setAccessible(true);
                methodField.set(httpURLConnection, method);
            } catch (final Exception e) {
                throw new RuntimeException(e);

            }
        }
    }
}
