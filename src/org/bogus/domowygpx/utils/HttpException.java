package org.bogus.domowygpx.utils;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;

public class HttpException extends IOException {

    private static final long serialVersionUID = -870690719865945304L;

    public final int httpCode;
    
    public HttpException(int httpCode)
    {
        super();
        this.httpCode = httpCode;
    }

    public HttpException(int httpCode, String detailMessage)
    {
        super(detailMessage);
        this.httpCode = httpCode;
    }
    
    public static HttpException fromHttpResponse(final HttpResponse resp, final HttpUriRequest request)
    {
        StatusLine statusLine = resp.getStatusLine();
        if (request == null){
            return new HttpException(statusLine.getStatusCode(), statusLine.toString());
        } else {
            return new HttpException(statusLine.getStatusCode(), statusLine + " for uri=" + request.getURI());
        }
    }
}