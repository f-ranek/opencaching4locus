package org.bogus.domowygpx.oauth;

import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.RequestWrapper;
import org.apache.http.protocol.HttpContext;
import org.bogus.domowygpx.application.OKAPI;

import android.content.Context;

public class OAuthSigningInterceptor implements HttpRequestInterceptor
{
    private final Context context;
    private OKAPI okApi;

    public OAuthSigningInterceptor(Context context)
    {
        this.context = context;
    }

    @Override
    public void process(HttpRequest request, HttpContext context) throws HttpException, IOException
    {
        final OKAPI okApi = getOkApi();
        final HttpUriRequest req = (HttpUriRequest)request;
        final URI uri = req.getURI(); // this may be relative URI - deal with it!
        final String uriStr = uri.toString();
        final String apiKey = okApi.getAPIKey();
        final StringBuilder sb = new StringBuilder(uriStr.length() + 15 + apiKey.length());
        sb.append(uriStr);
        if (uri.getRawQuery() == null){
            sb.append('?');
        } else {
            sb.append('&');
        }
        sb.append("consumer_key=");
        sb.append(apiKey);
        final String uriStr2 = sb.toString();
        final URI uri2 = URI.create(uriStr2);
        if (req instanceof HttpRequestBase){
            ((HttpRequestBase)req).setURI(uri2);
        } else
        if (req instanceof RequestWrapper){
            ((RequestWrapper)req).setURI(uri2);
        } else {
            throw new IllegalStateException("Unknown request typr=" + req.getClass().getName());
        }
        
    }
    
    protected OKAPI getOkApi()
    {
        if (okApi == null){
            okApi = OKAPI.getInstance(context);
        }
        return okApi;
    }
}
