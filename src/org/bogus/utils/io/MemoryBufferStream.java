package org.bogus.utils.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * <p>
 * The output stream optimized for temporary in-memory data storage.
 * The stream is suposed to be used as an input by call to {@link #getInputStream()}.
 * The conversion to {@code InputStream} is very time and space efficient.
 * <p>
 * <p>
 * The stream stores it's data in a series of byte chunks. The initial chunk size
 * can be set invoking {@link #MemoryBufferStream(int)}. Next chunks of the same size
 * are allocated as neccessary. If {@link #isCanExpandChunkSize()} is {@code true}
 * (the default behaviour), a call to {@link #write(byte[], int, int)} with a long
 * data length may allocate bigger chunk to fit all the data. Further chunk allocation
 * will anyway use the default chunk size.
 *
 * @author szczepb
 *
 */
public class MemoryBufferStream extends OutputStream
{
    /** Number of entries allocated in buff */
    protected final static int BUFF_CHUNK_SIZE = 4;
    /** Number of bytes allocated in chunk */
    protected final static int DEFAULT_CHUNK_SIZE = 4096;

    /** the whole buffer */
    protected byte[][] buff;
    /** current chunk */
    protected byte[] chunk;
    /** current chunk index in buffer */
    protected int chunkIdx;
    /** position in current chunk */
    protected int chunkPos;
    /** input stream exported */
    protected boolean isExp;

    private boolean canExpandChunkSize = true;

    /**
     * Creates the stream with default chunk size of 4kB.
     */
    public MemoryBufferStream()
    {
        this(DEFAULT_CHUNK_SIZE);
    }

    /**
     * Creates the stream with given chunk size. The minimum value
     * of {@code size} is 16.
     *
     * @param size
     */
    public MemoryBufferStream(int size)
    {
        if (size <= 0){
            throw new IllegalArgumentException();
        }

        if (size < 16){
            size = 16;
        }

        buff = new byte[BUFF_CHUNK_SIZE][];
        buff[0] = chunk = new byte[size];
    }

    @Override
    public synchronized void write(int b) throws IOException
    {
        checkClosed();

        makeRoom(1);
        chunk[chunkPos++] = (byte)b;
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException
    {
        checkClosed();

        if (len == 0){
            return ;
        }
        if (len < 0){
            throw new IndexOutOfBoundsException("len(" + len + ") < 0");
        }
        do{
            final int room = makeRoom(len);
            System.arraycopy(b, off, chunk, chunkPos, room);
            off+=room;
            len-=room;
            chunkPos+=room;
        } while(len > 0);
    }

    /**
     * Closes this stream. No further writes until call to {@link #reset()}
     * or {@link #release()} are possible.
     */
    @Override
    public synchronized void close() throws IOException
    {
        chunk = null;
    }

    /**
     * <p>
     * Returns the {@code InputStream} with content being a snapshot of the
     * current stream content. No further operation (writes, reset)
     * on this stream will affect returned {@code InputStream}.
     * <p>
     * This operation is very space efficient - it does not copy any
     * buffer data.
     * @return
     */
    public synchronized ByteArraysInputStream getInputStream()
    {
        isExp = true;
        return new ByteArraysInputStream(buff, chunkIdx+1, chunkPos);
    }

    /**
     * Calculates the stream length, ie. number of already written bytes.
     * @return
     */
    public synchronized long length()
    {
        long len = 0;
        for (int i=0; i<=chunkIdx-1; i++){
            byte[] chunk = buff[i];
            len += chunk.length;
        }
        len += chunkPos;
        return len;
    }

    /**
     * Calculates the stream length, ie. number of already written bytes.
     * @return The number of bytes written, or <code>Integer.MAX_VALUE</code>
     * if it does not fit in an <code>int</code> type.
     * @see #length()
     */
    public int size()
    {
        long len = length();
        if (len > Integer.MAX_VALUE){
            len = Integer.MAX_VALUE;
        }
        return (int)len;
    }

    /**
     * Retrieves the content of this stream as a byte array
     * @return
     * @throws IllegalStateException When the stream length is bigger than Integer.MAX_VALUE
     */
    public synchronized byte[] toByteArray()
    throws IllegalStateException
    {
        final long len = length();
        if (len >= Integer.MAX_VALUE){
            throw new IllegalStateException("Stream data too long: " + len);
        }
        final byte[] result = new byte[(int)len];
        int pos = 0;
        for (int i=0; i<=chunkIdx-1; i++){
            final byte[] chunk = buff[i];
            System.arraycopy(chunk, 0, result, pos, chunk.length);
            pos += chunk.length;
        }
        final byte[] last = buff[chunkIdx];
        System.arraycopy(last, 0, result, pos, chunkPos);
        return result;
    }

    /**
     * Copies the content of this stream to another output stream.
     * Returns number of bytes written.
     * @param os
     * @return
     * @throws IOException
     */
    public synchronized long writeTo(OutputStream os)
    throws IOException
    {
        long len = 0;
        for (int i=0; i<=chunkIdx-1; i++){
            final byte[] chunk = buff[i];
            os.write(chunk);
            len += chunk.length;
        }
        if (chunkPos > 0){
            final byte[] last = buff[chunkIdx];
            os.write(last, 0, chunkPos);
            len += chunkPos;
        }
        return len;
    }

    /**
     * Resets the stream to zero bytes written, so it can be reused
     */
    public synchronized void reset()
    {
        if (isExp){
            release();
        } else {
            chunkIdx = 0;
            chunkPos = 0;
            chunk = buff[0];
        }
    }

    /**
     * Releases and reinitilizes buffer
     */
    public synchronized void release()
    {
        isExp = false;
        chunkIdx = 0;
        chunkPos = 0;
        final int len = buff[0].length;
        buff = new byte[BUFF_CHUNK_SIZE][];
        buff[0] = chunk = new byte[len];
    }

    /**
     * Allocates new chunk (if neccessary). Returns number of bytes available
     * in the current chunk.
     *
     * @param needed
     * @return
     */
    protected int makeRoom(int needed)
    {
        int avail = chunk.length - chunkPos;
        if (avail >= needed){
            return needed;
        }
        if (avail > 0){
            return avail;
        }

        if (chunkIdx < buff.length-1){
            // use existing byte buffer
            chunk = buff[++chunkIdx];
            if (chunk == null){
                if (canExpandChunkSize){
                    chunk = new byte[Math.max(buff[0].length, needed)];
                } else {
                    chunk = new byte[buff[0].length];
                    needed = Math.min(needed, chunk.length);
                }
                buff[chunkIdx] = chunk;
            }
            chunkPos = 0;
            return Math.min(needed, chunk.length);
        } else {
            // allocate new byte buffer
            byte[][] newBuff = new byte[buff.length+BUFF_CHUNK_SIZE][];
            System.arraycopy(buff, 0, newBuff, 0, buff.length);
            buff = newBuff;
            if (canExpandChunkSize){
                chunk = new byte[Math.max(newBuff[0].length, needed)];
            } else {
                chunk = new byte[newBuff[0].length];
                needed = Math.min(needed, chunk.length);
            }
            buff[++chunkIdx] = chunk;
            chunkPos = 0;
            return needed;
        }
    }

    /**
     * If {@code true} (default), a call to {@link #write(byte[], int, int)} with a long data
     * may allocate chunk longer than default chunk length. If {@code false}, a long data will
     * be split over multiple chunks.
     *
     * @return
     */
    public boolean isCanExpandChunkSize()
    {
        return canExpandChunkSize;
    }

    public synchronized void setCanExpandChunkSize(boolean canExpandChunkSize)
    {
        this.canExpandChunkSize = canExpandChunkSize;
    }

    protected final void checkClosed()
    throws IOException
    {
        if (chunk == null){
            throw new IOException("Stream is closed");
        }
    }
}
