package com.naman14.timber.remote;

import android.content.Context;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;

import static com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;
import static com.google.android.gms.cast.CastMediaControlIntent.categoryForCast;

/**
 * Created by Christoph on 05.07.2016.
 */

public class ChromeCastScanner {


    private final MediaRouteSelector mMediaRouteSelector;
    private final MyMediaRouterCallback mMediaRouterCallback;
    private IRemoteScanFound mCallback;
    private MediaRouter mMediaRouter;
    private Cast.Listener mCastListener;
    private Context con;

    public ChromeCastScanner(Context con) {
        this.con = con;
        mMediaRouter = MediaRouter.getInstance(con);
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(categoryForCast(DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)).build();

        mMediaRouterCallback = new MyMediaRouterCallback();
    }

    public void startScan(IRemoteScanFound callback) {
        this.mCallback = callback;
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    public void stopScan() {
        mMediaRouter.removeCallback(mMediaRouterCallback);
    }

    private class MyMediaRouterCallback extends MediaRouter.Callback {

        @Override
        public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route) {
            Log.d("added", route.toString());
            CastDevice SelectedDevice = CastDevice.getFromBundle(route.getExtras());
            ChromeCast cast = new ChromeCast(SelectedDevice, con);
            mCallback.onRenderFound(cast);

        }
    }
}


