package org.bogus.domowygpx.oauth;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.RequestWrapper;
import org.apache.http.protocol.HttpContext;

import android.content.Context;

public class OAuthSigningInterceptor implements HttpRequestInterceptor
{
    protected final OAuth oauth;

    public OAuthSigningInterceptor(Context context)
    {
        this.oauth = OKAPI.getInstance(context).getOAuth();
    }
    
    @Override
    public void process(HttpRequest request, HttpContext context) throws HttpException, IOException
    {
        final HttpUriRequest req = (HttpUriRequest)request;
        if (oauth.hasOAuth3()){
            signLevel3(req, context);
        } else {
            signLevel1(req, context);
        }
    }

    protected void signLevel3(HttpUriRequest request, HttpContext context) throws HttpException, IOException
    {
        try{
            URI uri = request.getURI(); // this may be relative URI - deal with it!
            final boolean wasRelative = !uri.isAbsolute();
            if (wasRelative){
                if (request instanceof RequestWrapper){
                    uri = ((HttpUriRequest)((RequestWrapper)request).getOriginal()).getURI();
                }
            }
            if (uri == null || !uri.isAbsolute()){
                throw new HttpException("The given request uri=" + uri + " is not absolute");
            }
            
            uri = oauth.signOAuth3Uri(uri);
            if (wasRelative){
                final URI current = uri;
                uri = new URI(current.getRawPath() + "?" + current.getRawQuery());
            }
            
            if (request instanceof HttpRequestBase){
                ((HttpRequestBase)request).setURI(uri);
            } else
            if (request instanceof RequestWrapper){
                ((RequestWrapper)request).setURI(uri);
            } else {
                throw new IllegalStateException("Unknown request type=" + request.getClass().getName());
            }
        }catch(URISyntaxException use){
            throw new HttpException("Failed to construct URI", use);
        }
    }

    protected void signLevel1(HttpUriRequest request, HttpContext context) throws HttpException, IOException
    {
        try{
            final URI uri = request.getURI();
            final String uriStr = uri.toString();
            final String apiKey = oauth.getAPIKey();
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
            final URI uri2 = new URI(uriStr2);
            if (request instanceof HttpRequestBase){
                ((HttpRequestBase)request).setURI(uri2);
            } else
            if (request instanceof RequestWrapper){
                ((RequestWrapper)request).setURI(uri2);
            } else {
                throw new IllegalStateException("Unknown request type=" + request.getClass().getName());
            }
        }catch(URISyntaxException use){
            throw new HttpException("Failed to construct URI", use);
        }
    }
}
