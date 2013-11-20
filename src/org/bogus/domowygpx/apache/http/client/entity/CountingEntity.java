package org.bogus.domowygpx.apache.http.client.entity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

public class CountingEntity extends HttpEntityWrapper 
{
    /**
     * {@link #getContent()} method must return the same {@link InputStream}
     * instance when DecompressingEntity is wrapping a streaming entity.
     */
    private CountingInputStream content;

    /**
     * Creates a new {@link DecompressingEntity}.
     *
     * @param wrapped
     *            the non-null {@link HttpEntity} to be wrapped
     */
    public CountingEntity(final HttpEntity wrapped) {
        super(wrapped);
    }
    
    @Override
    public InputStream getContent() throws IOException {
        if (content == null) {
            content = new CountingInputStream(super.getContent());
        }
        return content;
    }

    @Override
    public boolean isRepeatable()
    {
        return false;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void writeTo(final OutputStream outstream) throws IOException {
        final InputStream instream = getContent();
        try {
            IOUtils.copy(instream, outstream);
        } finally {
            instream.close();
        }
    }

    /**
     * Returns estimated number of bytes read from the server (excluding headers), or -1 if it is unknown
     * @return
     */
    public long getSocketBytesRead()
    {
        if (content == null){
            return 0;
        }
        return content.getByteCount();
    }

    /**
     * Returns expected number of bytes to be read from the server (excluding headers), or -1 if it is unknown
     * @return
     */
    public long getSocketContentLength() 
    {
        long result = super.getContentLength();
        if (result < 0){
            return -1;
        } else {
            return result;
        }
    }
}
