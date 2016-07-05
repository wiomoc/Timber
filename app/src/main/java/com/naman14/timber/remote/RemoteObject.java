package com.naman14.timber.remote;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

public class RemoteObject implements Parcelable {
    public int id;
    public String name;
    public Bitmap image;
    public IRemote.Type type;

    public RemoteObject(int id, String name, Bitmap image, IRemote.Type type) {
        this.id = id;
        this.name = name;
        this.image = image;
        this.type = type;
    }

    protected RemoteObject(Parcel in) {
        id = in.readInt();
        name = in.readString();
        image = in.readParcelable(Bitmap.class.getClassLoader());
        type = IRemote.Type.values()[in.readInt()];
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(name);
        dest.writeParcelable(image, flags);
        dest.writeInt(type.ordinal());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<RemoteObject> CREATOR = new Creator<RemoteObject>() {
        @Override
        public RemoteObject createFromParcel(Parcel in) {
            return new RemoteObject(in);
        }

        @Override
        public RemoteObject[] newArray(int size) {
            return new RemoteObject[size];
        }
    };
}