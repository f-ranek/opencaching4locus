package org.bogus.domowygpx.oauth;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HttpContext;
import org.bogus.domowygpx.utils.Pair;

import android.content.Context;
import android.util.Log;

public class OAuthRevocationDetectorInterceptor implements HttpResponseInterceptor
{
    protected final Context context;
    protected final OAuth oauth;
    protected final OKAPI okapi;

    public OAuthRevocationDetectorInterceptor(Context context)
    {
        this.oauth = (okapi = OKAPI.getInstance(this.context = context)).getOAuth();
    }
    
    @Override
    public void process(HttpResponse response, HttpContext context) 
    throws HttpException, IOException
    {
        final StatusLine statusLine = response.getStatusLine();
        if (statusLine == null){
            return ;
        }
        if ((statusLine.getStatusCode() == 401 || statusLine.getStatusCode() == 400) 
                && response.getEntity() != null)
        {
            InputStream is = null;
            try{
                final Pair<String, String> errorCode = okapi.getErrorReason(response, true);
                if (errorCode == null){
                    return ;
                }
                
                if ("invalid_token".equals(errorCode.first)){
                    oauth.forgetOAuthCredentials();
                }
                
                int resId = okapi.mapErrorCodeToMessage(errorCode.first);
                if (resId > 0){
                    String message = this.context.getString(resId);
                    response.setStatusLine(new BasicStatusLine(
                        statusLine.getProtocolVersion(), 901, message)); 
                }
            }catch(Exception e){
                Log.e("OAuthRDI", "Failed to investigate error response", e);
            }finally{
                IOUtils.closeQuietly(is);
            }
        }
    }
}
