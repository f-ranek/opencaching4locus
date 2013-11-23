package org.bogus.domowygpx.utils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A holder for an input stream. The underlying input stream
 * can be set at any moment by calling {@link #setInputStream(InputStream)}
 * 
 * @author Boguś
 */
public class InputStreamHolder extends FilterInputStream
{
    public InputStreamHolder()
    {
        super(null);
    }
    
    public void setInputStream(InputStream is)
    {
        super.in = is;
    }
    @Override
    public void close()
    throws IOException
    {
        if (super.in != null){
            super.in.close();
        }
    }
}
