package org.bogus.domowygpx.services.downloader;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import org.bogus.ToStringBuilder;
import org.bogus.domowygpx.services.FilesDownloaderService;
import org.bogus.geocaching.egpx.BuildConfig;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;

public class FileData implements java.io.Serializable, Cloneable, Parcelable {
    private static final long serialVersionUID = 3016216584069909739L;

    public int fileDataId;
    public int taskId;
    public int state;
    
    public final static int FILE_STATE_SCHEDULED = 0;
    public final static int FILE_STATE_RUNNING = 1;
    public final static int FILE_STATE_FINISHED = 2;
    public final static int FILE_STATE_SKIPPED = 3;
    public final static int FILE_STATE_ABORTED = 101;
    public final static int FILE_STATE_TRANSIENT_ERROR = 102;
    public final static int FILE_STATE_PERMANENT_ERROR = 103;
    
    // INPUT VALUES
    /** Source cache code */
    public String cacheCode;
    /** Data URI */
    public URI source;
    /** Target place to save file */
    public File target;
    /** New file URI */
    public String virtualTarget;
    /** File priority, less means download earlier */
    public int priority;
    
    // OTHER STATE
    /** Used by {@link FilesDownloaderService} */
    public int retryCount;
    
    // OUTPUT VALUES
    /** HTTP response status line text */
    public String statusLine;
    /** HTTP headers, array of [Header, Value]*/
    public String[][] headers;
    /** Exception, if any was thrown during file download */
    public Exception exception;
    public String exceptionString;

    // TEMPORARY STATE
    /** Number of bytes stored in a temp file (from previous session) */
    public long initialSize;
    /** Number of bytes read in this session */
    public long sessionAmount;
    /** Expected final file size */
    public long expectedSize;
    
    @Override
    public FileData clone()
    {
        try{
            FileData result = (FileData)super.clone();
            // ignore headers cloning
            return result;
        }catch(CloneNotSupportedException cnse){
            throw new IllegalStateException(cnse);
        }
    }
    
    @Override
    public String toString()
    {
        if (BuildConfig.DEBUG){
            ToStringBuilder builder = new ToStringBuilder(this);
            builder.add("fileDataId", fileDataId);
            builder.add("taskId", taskId);
            switch(state){
                case FILE_STATE_SCHEDULED: builder.add("state", "SCHEDULED"); break;
                case FILE_STATE_RUNNING: builder.add("state", "RUNNING"); break;
                case FILE_STATE_FINISHED: builder.add("state", "FINISHED"); break;
                case FILE_STATE_SKIPPED: builder.add("state", "SKIPPED"); break;
                //case FILE_STATE_ABORTED: builder.add("state", "ABORTED"); break;
                case FILE_STATE_TRANSIENT_ERROR: builder.add("state", "TRANSIENT_ERROR"); break;
                case FILE_STATE_PERMANENT_ERROR: builder.add("state", "PERMANENT_ERROR"); break;
                default: builder.add("state", state); break;
            }
            
            builder.add("cacheCode", cacheCode);
            builder.add("source", source);
            builder.add("target", target);
            builder.add("virtualTarget", virtualTarget);
            builder.add("priority", priority);
            builder.add("retryCount", retryCount, 0); 
            builder.add("statusLine", statusLine);
            builder.add("exception", exception);
            if (headers != null){
                builder.add("headers", Arrays.deepToString(headers));
            }
            //builder.add("size", size, 0);
            return builder.toString();
        } else {
            return super.toString();
        }
    }
    
    @SuppressLint("SimpleDateFormat")
    public StringBuilder toDeveloperDebugString(StringBuilder sb)
    {
        sb.append("ID: ").append(fileDataId).append('\n');
        sb.append("CO: ").append(source).append('\n');
        sb.append("GDZIE: ").append(target).append('\n');
        sb.append("SKĄD: ").append(cacheCode).append('\n');
        sb.append("STATUS: ");
        switch(state){
            case FILE_STATE_SCHEDULED: sb.append("SCHEDULED"); break;
            case FILE_STATE_RUNNING: sb.append("RUNNING"); break;
            case FILE_STATE_FINISHED: sb.append("FINISHED"); break;
            case FILE_STATE_SKIPPED: sb.append("SKIPPED"); break;
            case FILE_STATE_TRANSIENT_ERROR: sb.append("TRANSIENT_ERROR"); break;
            case FILE_STATE_PERMANENT_ERROR: sb.append("PERMANENT_ERROR"); break;
            default: sb.append(state); break;
        }
        sb.append('\n');
        if (statusLine != null || headers != null){
            sb.append("SZCZEGÓŁY HTTP\n");
            if (statusLine != null){
                sb.append(statusLine).append('\n');
            }
            if (headers != null){
                for (String[] header : headers){
                    sb.append(header[0]).append(": ").append(header[1]).append('\n');
                }
            }
        }
        if (exceptionString != null){
            sb.append("SZCZEGÓŁY BŁĘDU\n").append(exceptionString).append('\n');
        }
        return sb;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeInt(fileDataId);
        dest.writeInt(state);
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
            dest.fileDataId = source.readInt();
            dest.state = source.readInt();
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