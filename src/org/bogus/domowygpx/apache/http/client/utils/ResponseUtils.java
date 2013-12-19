package org.bogus.domowygpx.apache.http.client.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ConnectionReleaseTrigger;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.HttpEntityWrapper;
import org.bogus.domowygpx.apache.http.client.entity.CountingEntity;
import org.bogus.domowygpx.utils.HttpException;
import org.bogus.utils.io.MemoryBufferStream;
import org.json.JSONException;
import org.json.JSONTokener;

import android.util.Log;

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
    
    public static void abortResponse(HttpResponse response)
    {
        if (response != null){
            try{
                HttpEntity e = response.getEntity();
                if (e instanceof ConnectionReleaseTrigger){
                    ((ConnectionReleaseTrigger)e).abortConnection();
                }
            }catch(Exception e){
                // ignore
            }
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
    
    public static boolean isCountingCapable(HttpEntity e)
    {
        return mineCountingEntity(e) != null;
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
    

    public static String getContentEncoding(final HttpResponse resp, final String defaultValue)
    {
        if (resp.getEntity() == null){
            return null;
        }
        final Header contentType = resp.getEntity().getContentType();
        if (contentType != null){
            final HeaderElement[] elems = contentType.getElements();
            if (elems != null && elems.length > 0){
                final NameValuePair nvp = elems[0].getParameterByName("charset");
                if (nvp != null){
                    return nvp.getValue();
                }
            }
        }
        return defaultValue;
    }    

    public static void logResponseContent(final String logTag, final HttpResponse resp)
    {
        Log.i(logTag, String.valueOf(resp.getStatusLine()));
        try{
            if (resp.getEntity() == null){
                return;
            }
            String charset = ResponseUtils.getContentEncoding(resp, "US-ASCII");
            InputStream is = resp.getEntity().getContent();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, charset));
            String line;
            while ((line = br.readLine()) != null){
                Log.i(logTag, line);
            }
            IOUtils.closeQuietly(is);
        }catch(Exception e){
            Log.i(logTag, "Failed to dump response", e);
        }
        ResponseUtils.closeResponse(resp);
    }

    public static Object getJSONObjectFromResponse(final String logTag, final HttpUriRequest request, final HttpResponse resp)
    throws IOException, JSONException
    {
        InputStream is = null;
        try{
            final int statusCode = resp.getStatusLine().getStatusCode(); 
            if (statusCode == 200){
                is = resp.getEntity().getContent();
                final String charset = ResponseUtils.getContentEncoding(resp, "UTF-8");
                final String data = IOUtils.toString(is, charset);
                final JSONTokener jr = new JSONTokener(data);
                final Object result = jr.nextValue();
                return result;
            } else 
            if (statusCode == 404 || statusCode == 204){
                ResponseUtils.logResponseContent(logTag, resp);
                throw new FileNotFoundException("Got " + statusCode + " for " + request.getURI());
            } else {
                ResponseUtils.logResponseContent(logTag, resp);
                throw HttpException.fromHttpResponse(resp, request);
            }
        }finally{
            IOUtils.closeQuietly(is);
            ResponseUtils.closeResponse(resp);
        }
    }
    
    public static Map<String, String> parseFormUrlEncoded(HttpResponse response)
    throws IOException
    {
        final InputStream content = response.getEntity().getContent();
        try{
            final String charset = ResponseUtils.getContentEncoding(response, "UTF-8");
            final Map<String, String> result = new HashMap<String, String>();
            {
                int b;
                StringBuilder temp = new StringBuilder(32);
                String name = null, value = null;
                int state = 1; // 1 - name, 2 - value
                while ((b = content.read()) >= 0){
                    if (b == '='){
                        if (state == 1){
                            name = temp.toString();
                            temp.setLength(0);
                            state = 2;
                        }
                    } else
                    if (b == '&'){
                        if (state == 2 || temp.length() > 0){
                            value = temp.toString();
                            temp.setLength(0);
                            result.put(name, value);
                        }
                        state = 1;
                        name = value = null;
                    } else {
                        temp.append((char)b);
                    }
                }
                if (temp.length() > 0){
                    if (state == 1){
                        result.put(temp.toString(), null);
                    } else {
                        result.put(name, temp.toString());
                    }
                }
            }
            // unescape
            Map<String, String> result2 = new HashMap<String, String>(result.size());
            for (Entry<String, String> e : result.entrySet()){
                String name = e.getKey();
                String value = e.getValue();
                name = URLDecoder.decode(name, charset);
                if (value != null){
                    value = URLDecoder.decode(value, charset);
                }
                result2.put(name, value);
            }
            return result2;
        }finally{
            IOUtils.closeQuietly(content);
        }
    }
    
    public static void makeRepetable(HttpResponse response)
    throws IOException
    {
        final HttpEntity entity = response.getEntity();
        if (entity == null){
            return;
        }
        
        if (!entity.isRepeatable()){
            final MemoryBufferStream mbs = new MemoryBufferStream();
            InputStream is2 = entity.getContent();
            IOUtils.copy(is2, mbs);
            IOUtils.closeQuietly(is2);
            AbstractHttpEntity entity2 = new AbstractHttpEntity(){

                @Override
                public boolean isRepeatable()
                {
                    return true;
                }

                @Override
                public long getContentLength()
                {
                    return mbs.length();
                }

                @Override
                public InputStream getContent() throws IOException, IllegalStateException
                {
                    return mbs.getInputStream();
                }

                @Override
                public void writeTo(OutputStream outstream) throws IOException
                {
                    mbs.writeTo(outstream);
                }

                @Override
                public boolean isStreaming()
                {
                    return false;
                }
                
            };
            entity2.setChunked(false);
            entity2.setContentEncoding(entity.getContentEncoding());
            entity2.setContentType(entity.getContentType());
            response.setEntity(entity2);
        } 
    }
}
