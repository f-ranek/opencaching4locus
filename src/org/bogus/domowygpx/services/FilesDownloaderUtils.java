package org.bogus.domowygpx.services;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;
import org.bogus.domowygpx.services.downloader.FileData;
import org.bogus.utils.io.MemoryBufferStream;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

public class FilesDownloaderUtils
{
    public static void setFilesInIntent(Intent intent, List<FileData> files)
    {
        if (files.size() <= 20){
            intent.putExtra(FilesDownloaderService.INTENT_EXTRA_FILES, files.toArray(new Parcelable[files.size()]));
            intent.removeExtra(FilesDownloaderService.INTENT_EXTRA_FILES_PACK);
        } else {
            intent.removeExtra(FilesDownloaderService.INTENT_EXTRA_FILES);
            // try limiting data amount
            OutputStream os2 = null;
            try{
                final MemoryBufferStream mbs = new MemoryBufferStream();
                final Parcel parcel = Parcel.obtain();
                parcel.writeInt(files.size());
                for (FileData file : files){
                    file.writeToParcel(parcel, 0);
                }
                final byte[] parcelData = parcel.marshall();

                os2 = new GZIPOutputStream(mbs);
                os2.write(parcelData);
                os2.close();
                parcel.recycle();
                
                final byte[] data = mbs.toByteArray();
                intent.putExtra(FilesDownloaderService.INTENT_EXTRA_FILES_PACK, data);
                intent.putExtra(FilesDownloaderService.INTENT_EXTRA_FILES_PACK_SIZE, parcelData.length);
            }catch(IOException ioe){
                throw new IllegalStateException("Unexpected exception", ioe);
            }finally{
                IOUtils.closeQuietly(os2);
            }
        }
    }
    
    public static List<FileData> getFilesFromIntent(Intent intent)
    {
        if (intent.hasExtra(FilesDownloaderService.INTENT_EXTRA_FILES)){
            final Parcelable[] files = intent.getParcelableArrayExtra(FilesDownloaderService.INTENT_EXTRA_FILES);
            final List<Parcelable> files2 = Arrays.asList(files);

            // bad casting, but we do not need copying arrays
            @SuppressWarnings("unchecked")
            final List<FileData> files3 = (List<FileData>)(Object)files2;
            return files3;
        }
        if (intent.hasExtra(FilesDownloaderService.INTENT_EXTRA_FILES_PACK)){
            byte[] data = intent.getByteArrayExtra(FilesDownloaderService.INTENT_EXTRA_FILES_PACK);
            InputStream is = null;
            try{
                is = new GZIPInputStream(new ByteArrayInputStream(data));
                int buffSize = intent.getIntExtra(FilesDownloaderService.INTENT_EXTRA_FILES_PACK_SIZE, -1);
                byte[] parcelData = new byte[buffSize];
                {
                    int offset = 0;
                    while (buffSize > 0) {
                        int bytesRead = is.read(parcelData, offset, buffSize);
                        if (bytesRead < 0) {
                            throw new EOFException();
                        }
                        offset += bytesRead;
                        buffSize -= bytesRead;
                    }                    
                }
                is.close();
                
                final Parcel parcel = Parcel.obtain();
                parcel.unmarshall(parcelData,0,parcelData.length);
                parcel.setDataPosition(0);
                int count = parcel.readInt();
                final List<FileData> result = new ArrayList<FileData>(count);
                for (int i=0; i<count; i++){
                    FileData fileData = FileData.CREATOR.createFromParcel(parcel);
                    result.add(fileData);
                }
                return result;
            }catch(IOException ioe){
                throw new IllegalStateException("Unexpected exception", ioe);
            }finally{
                IOUtils.closeQuietly(is);
            }
        }
        return null;
    }
}
