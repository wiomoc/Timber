package com.naman14.timber.remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.images.WebImage;

import java.io.IOException;
import java.util.Date;

import static com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;

/**
 * Created by Christoph on 05.07.2016.
 */

public class ChromeCast implements IRemote, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private final Context con;
    private CastDevice device;
    private boolean mConnected;
    private Object workingLock = new Object();
    private boolean working;
    private Cast.Listener mCastListener;
    private GoogleApiClient mApiClient;
    private RemoteMediaPlayer mRemoteMediaPlayer;
    private long positionLast;
    private long positionStart;
    private int positionOffset;


    ChromeCast(CastDevice device, Context con) {
        this.device = device;
        this.con = con;
    }


    private void beginWork() {

        synchronized (workingLock) {
            if (working) try {
                workingLock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            working = true;
        }
        Log.d("WORK", "begin");
    }

    private void endWork() {
        Log.d("WORK", "end");
        synchronized (workingLock) {

            workingLock.notifyAll();

            working = false;
        }
    }

    @Override
    public void connect() {
        beginWork();
        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions.builder(this.device, new Cast.Listener() {

            @Override
            public void onApplicationDisconnected(int errorCode) {
                Log.d("Cast", "disconnected");
            }

        });
        mApiClient = new GoogleApiClient.Builder(this.con)
                .addApi(Cast.API, apiOptionsBuilder.build())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).build();

        mApiClient.connect();

    }

    @Override
    public void close() {

    }

    @Override
    public void setMedia(String file, String artist, String album, String title, String cover) {
        Server server = Server.getServer();
        String audioUrl = server.addResource(file, null);


        MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);

        movieMetadata.putString(MediaMetadata.KEY_ARTIST, artist);
        movieMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE, album);
        movieMetadata.putString(MediaMetadata.KEY_TITLE, title);
        String coverUrl = "";
        if (cover != null) {
            coverUrl = server.addResource(cover, null);
            movieMetadata.addImage(new WebImage(Uri.parse(coverUrl)));
        }
        final MediaInfo mediaInfo = new MediaInfo.Builder(audioUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType("audio/*")
                .setMetadata(movieMetadata)
                .build();
        new Thread() {
            public void run() {
                beginWork();
                mRemoteMediaPlayer.load(mApiClient, mediaInfo);
                endWork();
            }
        }.start();


    }

    @Override
    public void play() throws IOException {
        new Thread() {
            public void run() {
                beginWork();
                mRemoteMediaPlayer.play(mApiClient);
                positionStart = new Date().getTime();
                loadPosition();
                endWork();
            }
        }.start();
    }

    @Override
    public void stop() throws IOException {
        new Thread() {
            public void run() {
                beginWork();
                mRemoteMediaPlayer.stop(mApiClient);
                positionOffset += new Date().getTime() - positionStart;
                positionStart = -1;
                endWork();
            }
        }.start();
    }

    @Override
    public void pause() throws IOException {
        new Thread() {
            public void run() {
                beginWork();
                mRemoteMediaPlayer.pause(mApiClient);
                positionOffset += new Date().getTime() - positionStart;
                positionStart = -1;
                loadPosition();
                endWork();
            }
        }.start();
    }

    @Override
    public void seek(final int sec) throws IOException {
        new Thread() {
            public void run() {
                beginWork();
                mRemoteMediaPlayer.seek(mApiClient,sec);
                positionOffset += sec;
                endWork();
            }
        }.start();
    }

    @Override
    public void volumeChange(int vol) throws IOException {

    }

    @Override
    public void setEventListener(IRemoteEvent listener) {

    }
    void loadPosition(){
        new Thread() {
            public void run() {
                beginWork();

                positionOffset = (int) mRemoteMediaPlayer.getApproximateStreamPosition();
                positionStart = new Date().getTime();
                endWork();
            }
        }.start();
    }
    @Override
    public int getPosition() {
        long time = new Date().getTime();
        if (time > positionLast + 3000) {
            positionLast = time;
            loadPosition();
        }
        if (positionStart != -1) {
            return (int) (positionOffset + (time - positionStart));
        }

        return positionOffset;
    }

    @Override
    public Bitmap getImage() {
        //return TimberUtils.loadBitmapByURL(device.getIcon(90,90).getUrl().toString());
        return null;
    }

    @Override
    public String getName() {
        return this.device.getFriendlyName();
    }

    @Override
    public Type getType() {
        return Type.CHROMECAST;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d("Cast", "connected");
        Cast.CastApi.launchApplication(mApiClient, DEFAULT_MEDIA_RECEIVER_APPLICATION_ID, false).setResultCallback(new ResultCallback<Cast.ApplicationConnectionResult>() {
            @Override
            public void onResult(Cast.ApplicationConnectionResult result) {
                Status status = result.getStatus();
                Log.d("Status",
                        "ApplicationConnectionResultCallback.onResult:"
                                + status.getStatusCode());
                if (status.isSuccess()) {
                    mRemoteMediaPlayer = new RemoteMediaPlayer();
                    mRemoteMediaPlayer.setOnStatusUpdatedListener(
                            new RemoteMediaPlayer.OnStatusUpdatedListener() {
                                @Override
                                public void onStatusUpdated() {

                                }
                            });

                    mRemoteMediaPlayer.setOnMetadataUpdatedListener(
                            new RemoteMediaPlayer.OnMetadataUpdatedListener() {
                                @Override
                                public void onMetadataUpdated() {

                                }
                            });
                    try {
                        Cast.CastApi.setMessageReceivedCallbacks(mApiClient,
                                mRemoteMediaPlayer.getNamespace(), mRemoteMediaPlayer);
                    } catch (IOException e) {
                        Log.e("EE", "Exception while creating media channel", e);
                    }
                    mRemoteMediaPlayer
                            .requestStatus(mApiClient)
                            .setResultCallback(
                                    new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                                        @Override
                                        public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                                            if (!result.getStatus().isSuccess()) {
                                                Log.e("E", "Failed to request status.");
                                                return;
                                            }
                                            endWork();
                                        }
                                    });
                }

            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("Cast", "connectedS " + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d("Cast", "connection failed " + connectionResult.getErrorMessage());
    }
}
