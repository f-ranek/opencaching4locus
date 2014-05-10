package org.bogus.utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ByteArraysInputStream extends InputStream{

    protected int markChunkIdx;
    protected int markChunkPos;
    
    /** current chunk index in buffer */
    protected int chunkIdx;
    /** position in current chunk */
    protected int chunkPos;
    
    /** the whole buffer */
    protected byte[][] buff;
    /** current chunk */
    protected byte[] chunk;
    
    /** last chunk length */
    protected int lastChunkLimit;
    
    private long len = -1;
    
    /** for subclassing */
    protected ByteArraysInputStream()
    {
        
    }
    
    /**
     * Called by {@link MemoryBufferStream}.
     * 
     * @param buff Buffer elements (chunks).
     * @param count  Chunks count. If the count is lesser than buff size, buff 
     *      is copied to hold only neccessary chunks. 
     * @param lastLength  Last chunk length
     */
    protected ByteArraysInputStream(byte[][] buff, int count, int lastLength)
    {
        if (lastLength < 0){
            throw new IllegalArgumentException("lastLength(" + lastLength + ") < 0");
        }
        if (count < 0){
            throw new IllegalArgumentException("count(" + count + ") < 0");
        }
        if (buff.length == count){
            this.buff = buff;
        } else {
            this.buff = new byte[count][];
            System.arraycopy(buff, 0, this.buff, 0, count);
        }
        lastChunkLimit = lastLength;
        chunk = this.buff[0];
    }
    
    @Override
    public synchronized int read() throws IOException
    {
        checkClosed();

        if (chunkIdx == buff.length-1){
            // last chunk
            if (chunkPos >= lastChunkLimit){
                return -1; // eof
            } else {
                return chunk[chunkPos++];
            }
        } else {
            if (chunkPos == chunk.length){
                // advance to the next chunk
                chunk = buff[++chunkIdx];
                chunkPos = 0;
            }
            return chunk[chunkPos++];
        }
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException
    {
        checkClosed();

        if (len == 0){
            return 0;
        }
        
        if (len < 0){
            throw new IndexOutOfBoundsException("len(" + len + ") < 0");
        }
        
        int read = 0;
        do{
            if (chunkIdx == buff.length-1){
                // last chunk
                if (chunkPos >= lastChunkLimit){
                    return read == 0 ? -1 : read;
                } else {
                    int available = Math.min(len, lastChunkLimit - chunkPos);
                    System.arraycopy(chunk, chunkPos, b, off, available);
                    read += available;
                    off += available;
                    len -= available;
                    chunkPos += available;
                    return read; 
                }
            } else {
                if (chunkPos == chunk.length){
                    chunk = buff[++chunkIdx];
                    chunkPos = 0;
                    if (chunkIdx == buff.length-1){
                        continue; // we will jump to the IF block which ELSE belongs to
                    }
                }
                int available = Math.min(len, chunk.length - chunkPos);
                System.arraycopy(chunk, chunkPos, b, off, available);
                read += available;
                off += available;
                len -= available;
                chunkPos += available;
            }
        }while(len > 0);
        return read;
    }

    
    @Override
    public synchronized long skip(long n) throws IOException
    {
        checkClosed();
        
        if (n <= 0){
            return 0;
        }
        
        long skipped = 0;
        do{
            if (chunkIdx == buff.length-1){
                // last chunk
                if (chunkPos == lastChunkLimit){
                    return skipped;
                } else {
                    long available = Math.min(n, (lastChunkLimit - chunkPos));
                    skipped += available;
                    n -= available;
                    chunkPos += (int)available;
                    return skipped; 
                }
            } else {
                if (chunkPos == chunk.length){
                    chunk = buff[++chunkIdx];
                    chunkPos = 0;
                }
                long available = Math.min(n, (chunk.length - chunkPos));
                skipped += available;
                chunkPos += (int)available;
                n -= available;
            }
        }while(n > 0);
        return skipped;
    }

    @Override
    public synchronized int available() throws IOException
    {
        checkClosed();
        
        if (chunkIdx == buff.length-1){
            // last chunk
            return lastChunkLimit - chunkPos;
        } else {
            if (chunkPos == chunk.length){
                chunk = buff[++chunkIdx];
                chunkPos = 0;
            }
            return chunk.length - chunkPos;
        }
    }
    
    /**
     * Calculates the stream length, ie. the total number of bytes that can be read
     * @return
     * @throws IOException 
     */
    public long length() throws IOException
    {
        checkClosed(); 
        if (len == -1){
            long len = 0;
            for (int i=0; i<buff.length-1; i++){
                byte[] chunk = buff[i];
                len += chunk.length;
            }
            len += lastChunkLimit;
            return this.len = len;
        } else {
            return len;
        }
    }
    
    /**
     * <p>
     * Copies the all content of this stream to output stream. The stream current position
     * is not changed.
     * <p>
     * Returns number of bytes written.
     * @param os
     * @return
     * @throws IOException
     */
    public long writeAllTo(OutputStream os)
    throws IOException
    {
        checkClosed();
        long len = 0;
        for (int i=0; i<buff.length-1; i++){
            final byte[] chunk = buff[i];
            os.write(chunk);
            len += chunk.length;
        }
        if (lastChunkLimit > 0){
            final byte[] last = buff[buff.length-1];
            os.write(last, 0, lastChunkLimit);
            len += lastChunkLimit;
        }
        return len;
    }

    /**
     * <p>
     * Copies the content of this stream from it's current position to output stream. 
     * Returns number of bytes written.
     * <p>
     * The stream is positioned to EOF, one can reposition it using {@link #reset()} 
     * @param os
     * @return
     * @throws IOException
     */
    public synchronized long writeTo(OutputStream os)
    throws IOException
    {
        checkClosed();
        long len = 0;
        
        // write down current chunk
        if (chunkIdx == buff.length-1){
            // we are in the last chunk
            final int count = lastChunkLimit - chunkPos;
            if (count > 0){
                os.write(chunk, chunkPos, count);
                chunkPos+=count;
            }
            return count;
        } else {
            int count = chunk.length - chunkPos;
            os.write(chunk, chunkPos, count);
            len += count;
            
            // advance to the next chunk
            chunk = buff[++chunkIdx];
            chunkPos = 0;
        }
        
        // write down middle chunks
        for (int i=chunkIdx; i<buff.length-1; i++){
            byte[] chunk = buff[i];
            int count = chunk.length;
            os.write(chunk, 0, count);
            len += count;
        }
        
        // last chunk
        chunk = buff[chunkIdx = buff.length-1];
        chunkPos = lastChunkLimit;
        if (lastChunkLimit > 0){
            os.write(chunk, 0, lastChunkLimit);
            len += lastChunkLimit;
        }
        
        return len;
    }
    
    /**
     * Closes the stream releasing all the byte buffers. No further 
     * operations are possible. 
     */
    @Override
    public synchronized void close() throws IOException
    {
        chunkIdx = chunkPos = -1;
        buff = null;
        chunk = null;
    }

    @Override
    public synchronized void mark(int readlimit)
    {
        markChunkPos = chunkPos;
        markChunkIdx = chunkIdx;
    }

    /**
     * Resets the stream position to last remembered by the call to {@link #mark(int)}.
     * If {@link #mark(int)} has not ever been called, resets the stream to it's 
     * beginning.
     */
    @Override
    public synchronized void reset() throws IOException
    {
        checkClosed();
        chunkIdx = markChunkIdx;
        chunkPos = markChunkPos;
        chunk = buff[chunkIdx];
    }

    @Override
    public boolean markSupported()
    {
        return true;
    }
    
    protected final void checkClosed()
    throws IOException
    {
        if (chunkIdx == -1 || chunkPos == -1){
            throw new IOException("Stream is closed");
        }
    }
}
