package org.bogus.domowygpx.oauth;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.StatusLine;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HttpContext;
import org.bogus.domowygpx.apache.http.client.utils.ResponseUtils;
import org.bogus.utils.io.MemoryBufferStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.content.Context;
import android.util.Log;

public class OAuthRevocationDetectorInterceptor implements HttpResponseInterceptor
{
    protected final OAuth oauth;

    public OAuthRevocationDetectorInterceptor(Context context)
    {
        this.oauth = OKAPI.getInstance(context).getOAuth();
    }
    
    @Override
    public void process(HttpResponse response, HttpContext context) 
    throws HttpException, IOException
    {
        final StatusLine statusLine = response.getStatusLine();
        if (statusLine == null){
            return ;
        }
        if (statusLine.getStatusCode() == 401 && response.getEntity() != null){
            InputStream is = null;
            try{
                HttpEntity entity = response.getEntity();
                if (!entity.isRepeatable()){
                    MemoryBufferStream mbs = new MemoryBufferStream();
                    is = entity.getContent();
                    IOUtils.copy(is, mbs);
                    IOUtils.closeQuietly(is);
                    entity = new ByteArrayEntity(mbs.toByteArray());
                    response.setEntity(entity);
                }
                is = entity.getContent();
                final String charset = ResponseUtils.getContentEncoding(response, "UTF-8");
                final String data = IOUtils.toString(is, charset);
                final JSONTokener jr = new JSONTokener(data);
                final JSONObject result = (JSONObject)jr.nextValue();
                final JSONObject errorObj = result.getJSONObject("error");
                final JSONArray reasonStackArr = errorObj.getJSONArray("reason_stack");
                int len = reasonStackArr.length();
                for (int i=0; i<len; i++){
                    Object obj = reasonStackArr.get(i);
                    if ("invalid_token".equals(obj)){
                        oauth.forgetOAuthCredentials();
                        response.setStatusLine(new BasicStatusLine(
                            statusLine.getProtocolVersion(), 901, "Użytkownik odwołał dostęp aplikacji do serwisu")); 
                        // XXX get from resources
                    } else
                    if ("invalid_timestamp".equals(obj)){
                        response.setStatusLine(new BasicStatusLine(
                            statusLine.getProtocolVersion(), 902, "Sprawdź czas na urządzeniu"));
                    }
                }
            }catch(Exception e){
                Log.e("OAuthRDI", "Failed to investigate 401 response", e);
            }finally{
                IOUtils.closeQuietly(is);
            }
        }
    }
}
