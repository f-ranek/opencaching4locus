package org.bogus.domowygpx.downloader;

import java.util.Arrays;

import org.bogus.ToStringBuilder;

public class DownloadedFileData implements java.io.Serializable 
{
    private static final long serialVersionUID = 2624166232649857995L;
    public FileData fileData;
    public String statusLine;
    public Exception exception;
    public String[][] headers;
    
    public long size;

    @Override
    public String toString()
    {
        ToStringBuilder builder = new ToStringBuilder(this);
        builder.add("fileData", fileData);
        builder.add("statusLine", statusLine);
        builder.add("exception", exception);
        if (headers != null){
            builder.add("headers", Arrays.deepToString(headers));
        }
        builder.add("size", size);
        return builder.toString();
    }
}
