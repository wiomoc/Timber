package com.naman14.timber.remote;

import android.graphics.Bitmap;

import java.io.IOException;

import javax.jmdns.ServiceInfo;

/**
 * Created by Christoph on 10.07.2016.
 */

public class AirPlay implements IRemote {
    String name;

    public AirPlay(ServiceInfo info) {
        this.name = info.getName();
        int at = this.name.indexOf('@');
        if (at != -1) this.name = this.name.substring(at+1);
    }

    @Override
    public void connect() {

    }

    @Override
    public void close() {

    }

    @Override
    public void setMedia(String file, String artist, String album, String title, String picture) {

    }

    @Override
    public void play() throws IOException {

    }

    @Override
    public void stop() throws IOException {

    }

    @Override
    public void pause() throws IOException {

    }

    @Override
    public void seek(int sec) throws IOException {

    }

    @Override
    public void volumeChange(int vol) throws IOException {

    }

    @Override
    public void setEventListener(IRemoteEvent listener) {

    }

    @Override
    public int getPosition() {
        return 0;
    }

    @Override
    public Bitmap getImage() {
        return null;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Type getType() {
        return Type.AIRPLAY;
    }
}
