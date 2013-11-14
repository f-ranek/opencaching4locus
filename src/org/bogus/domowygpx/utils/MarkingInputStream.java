package org.bogus.domowygpx.utils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

// XXX find a better name for the class
public class MarkingInputStream extends FilterInputStream
{
    public MarkingInputStream()
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
