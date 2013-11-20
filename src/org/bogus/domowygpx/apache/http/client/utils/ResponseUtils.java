package org.bogus.domowygpx.apache.http.client.utils;

import java.lang.reflect.Field;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.HttpEntityWrapper;
import org.bogus.domowygpx.apache.http.client.entity.CountingEntity;

public class ResponseUtils
{
    public static void closeResponse(HttpResponse response)
    {
        if (response != null){
            try{
                HttpEntity e = response.getEntity();
                if (e != null){
                    e.consumeContent();
                }
            }catch(Exception e){
                // ignore
            }
            //if (response instanceof CloseableHttpResponse){
            //    IOUtils.closeQuietly((CloseableHttpResponse)response);
            //}
        }
    }
    
    /**
     * Returns expected number of bytes to be read from the server (excluding headers), or -1 if it is unknown
     * @return
     */
    public static long getContentLength(HttpResponse response)
    {
        if (response != null){
            final HttpEntity e = response.getEntity();
            if (e != null){
                final CountingEntity ce = mineCountingEntity(e);
                if (ce != null){
                    return ce.getSocketContentLength();
                } else {
                    long result = e.getContentLength();
                    if (result < 0){
                        result = -1;
                    }
                    return result;
                }
            }
        }
        return -1;
    }

    /**
     * Returns estimated number of bytes read from the server (excluding headers), or -1 if it is unknown
     * @return
     */
    public static long getBytesRead(HttpResponse response)
    {
        if (response != null){
            final HttpEntity e = response.getEntity();
            if (e != null){
                final CountingEntity ce = mineCountingEntity(e);
                if (ce != null){
                    return ce.getSocketBytesRead();
                }
            }
        }
        return -1;
    }
    
    /**
     * Wraps the response, so future calls to {@link #getBytesRead(HttpResponse)} will succeed
     * @param response
     */
    public static void wrapResponseToBeCountable(HttpResponse response)
    {
        HttpEntity e = response.getEntity();
        if (e == null || mineCountingEntity(e) != null){
            return;
        }
        response.setEntity(new CountingEntity(e));
    }
    
    private final static Field wrappedEntity;
    static {
        Field f;
        try{
            f = HttpEntityWrapper.class.getDeclaredField("wrappedEntity");
            f.setAccessible(true);
        }catch(Exception e){
            f = null;
        }
        wrappedEntity = f;
    }
    
    // mine as mining
    private static CountingEntity mineCountingEntity(HttpEntity e)
    {
        if (e == null){
            return null;
        }
        if (e instanceof CountingEntity){
            return (CountingEntity)e;
        }
        if (wrappedEntity != null){
            try{
                do{
                    if (e instanceof HttpEntityWrapper){
                        e = (HttpEntity)wrappedEntity.get(e);
                        if (e instanceof CountingEntity){
                            return (CountingEntity)e;
                        }
                    } else {
                        return null;
                    }
                }while(e != null);
            }catch(IllegalAccessException iae){
                
            }
        }
        return null;
    }
}
