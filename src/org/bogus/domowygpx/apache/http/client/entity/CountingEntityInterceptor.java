package org.bogus.domowygpx.apache.http.client.entity;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;
import org.bogus.domowygpx.apache.http.client.utils.ResponseUtils;

public class CountingEntityInterceptor implements HttpResponseInterceptor
{
    @Override
    public void process(HttpResponse response, HttpContext context) throws HttpException, IOException
    {
        HttpEntity e = response.getEntity();
        if (e == null || ResponseUtils.isCountingCapable(e)){
            return;
        }
        response.setEntity(new CountingEntity(e));
    }

}
