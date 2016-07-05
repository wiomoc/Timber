package com.naman14.timber.remote;

import android.graphics.Bitmap;

import java.io.IOException;

/**
 * Created by Christoph on 28.03.2016.
 */
public interface IRemote {

    public enum Type {
        LOCAL, UPNP, CHROMECAST;
    }

    void connect();

    void close();

    void setMedia(String file, String artist, String album, String title, String picture);

    void play() throws IOException;

    void stop() throws IOException;

    void pause() throws IOException;

    void seek(int sec) throws IOException;

    void volumeChange(int vol) throws IOException;

    void setEventListener(IRemoteEvent listener);

    int getPosition();

    Bitmap getImage();

    String getName();

    Type getType();
}
