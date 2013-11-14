package org.bogus.domowygpx.downloader;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.bogus.ToStringBuilder;

import android.os.Parcel;
import android.os.Parcelable;

public class FileData implements java.io.Serializable, Parcelable {
    private static final long serialVersionUID = 3016216584069909739L;

    public String cacheCode;
    public URI source;
    public File target;
    public String virtualTarget;
    public int priority;
    
    @Override
    public String toString()
    {
        ToStringBuilder builder = new ToStringBuilder(this);
        builder.add("cacheCode", cacheCode);
        builder.add("source", source);
        builder.add("target", target);
        builder.add("virtualTarget", virtualTarget);
        builder.add("priority", priority);
        return builder.toString();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(cacheCode);
        dest.writeString(source == null ? null : source.toASCIIString());
        dest.writeString(target == null ? null : target.toString());
        dest.writeString(virtualTarget);
        dest.writeInt(priority);
    }
    
    public static final Parcelable.Creator<FileData> CREATOR = new Creator<FileData>()
    {
        
        @Override
        public FileData[] newArray(int size)
        {
            return new FileData[size];
        }
        
        @Override
        public FileData createFromParcel(Parcel source)
        {
            final FileData dest = new FileData();
            dest.cacheCode = source.readString();
            String uri = source.readString();
            if (uri != null){
                try{
                    dest.source = new URI(uri);
                }catch(URISyntaxException usi){
                    throw new IllegalStateException("Can not deserialize URI = " + uri, usi);
                }
            }
            String file = source.readString();
            if (file != null){
                dest.target = new File(file);
            }
            dest.virtualTarget = source.readString();
            dest.priority = source.readInt();
            return dest;
        }
    };
}