package org.bogus.domowygpx.utils;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;

public class HttpException extends IOException {

    private static final long serialVersionUID = -870690719865945304L;

    public final int httpCode;
    public final String httpMessage;

    public HttpException(int httpCode, String detailMessage, String httpMessage)
    {
        super(detailMessage);
        this.httpCode = httpCode;
        this.httpMessage = httpMessage;
    }
    
    public HttpException(int httpCode, String detailMessage)
    {
        this(httpCode, detailMessage, null);
    }

    public static HttpException fromHttpResponse(final HttpResponse resp, final HttpUriRequest request)
    {
        StatusLine statusLine = resp.getStatusLine();
        if (request == null){
            return new HttpException(statusLine.getStatusCode(), statusLine.toString(), statusLine.getReasonPhrase());
        } else {
            return new HttpException(statusLine.getStatusCode(), statusLine + " for uri=" + request.getURI(), statusLine.getReasonPhrase());
        }
    }
    
    public boolean isCustomErrorCode()
    {
        return httpCode >= 900 && httpCode <= 990;
    }
}