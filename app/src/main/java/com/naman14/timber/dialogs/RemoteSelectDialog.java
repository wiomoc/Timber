package com.naman14.timber.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.naman14.timber.R;
import com.naman14.timber.remote.RemoteObject;

/**
 * Created by Christoph on 27.03.2016.
 */
public class RemoteSelectDialog extends DialogFragment {
    ListView listView;
    RemoteAdapter adapter;
    RemoteObject connected;
    BroadcastReceiver broadcastReceiver;
    public static final String REMOTE_FOUND = "de.wiomoc.Timber.RemoteFound";
    public static final String REMOTE_START_SCAN = "de.wiomoc.Timber.StartScan";
    public static final String REMOTE_STOP_SCAN = "de.wiomoc.Timber.StopScan";
    public static final String REMOTE_CONNECT = "de.wiomoc.Timber.Connect";
    public static final String REMOTE_STATE_CHANGE = "de.wiomoc.Timber.StateChange";
    public static final String REMOTE_STATE = "RemoteState";
    public static final String REMOTE_ID = "ID";
    public static final int REMOTE_CONNECTED = 0;
    public static final int REMOTE_DISCONNECTED = 1;
    public static final int REMOTE_ERROR = 2;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Activity activity = getActivity();
        activity.sendBroadcast(new Intent(REMOTE_START_SCAN));
        adapter = new RemoteAdapter(activity);
        adapter.add(new RemoteObject(0, "Local", null));
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case REMOTE_FOUND:
                        RemoteObject obj = intent.getParcelableExtra("Remote");
                        if (obj != null) adapter.add(obj);
                        break;
                    case REMOTE_STATE_CHANGE:
                        int state = intent.getIntExtra(RemoteSelectDialog.REMOTE_STATE, REMOTE_ERROR);
                        int id = intent.getIntExtra(RemoteSelectDialog.REMOTE_ID, 0);
                        if (state == REMOTE_CONNECTED) {
                            connected = adapter.getItem(id);
                            Toast.makeText(context, "Connected to " + connected.name, Toast.LENGTH_SHORT).show();

                        } else if (state == REMOTE_ERROR) {
                            Toast.makeText(context,"Error, Remote:"+connected.name,Toast.LENGTH_SHORT).show();
                        }

                        break;
                }
            }
        };

        listView = new ListView(activity);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
                                            @Override
                                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                                                int id = ((RemoteObject) view.getTag()).id;
                                                Intent intent = new Intent(REMOTE_CONNECT);
                                                intent.putExtra(REMOTE_ID, id);
                                                getActivity().sendBroadcast(intent);
                                            }
                                        }

        );
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {
                        getActivity().sendBroadcast(new Intent(REMOTE_STOP_SCAN));
                    }
                }

        ).setView(listView);
        return builder.create();
    }
    @Override
    public void onAttach(Activity activity){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(REMOTE_FOUND);
        intentFilter.addAction(REMOTE_STATE_CHANGE);
        activity.registerReceiver(broadcastReceiver, intentFilter);
        activity.sendBroadcast(new Intent(REMOTE_START_SCAN));
    }
    @Override
    public void onDetach(){
        getActivity().unregisterReceiver(broadcastReceiver);
    }
    private class RemoteAdapter extends ArrayAdapter<RemoteObject> {
        private Activity con;

        public RemoteAdapter(Activity context) {
            super(context, R.layout.item_remote);
            this.con = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            RemoteObject remoteObject = getItem(position);
            if (rowView == null) {
                LayoutInflater inflater = this.con.getLayoutInflater();
                rowView = inflater.inflate(R.layout.item_remote, null);

            }
            rowView.setTag(remoteObject);
            ImageView imgview = (ImageView) rowView.findViewById(R.id.remoteImage);
            imgview.setImageBitmap(remoteObject.image);
            TextView textView = (TextView) rowView.findViewById(R.id.remoteName);
            textView.setText(remoteObject.name);
            return rowView;

        }
    }


}
