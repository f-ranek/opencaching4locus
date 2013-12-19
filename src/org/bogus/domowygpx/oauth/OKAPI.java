package org.bogus.domowygpx.oauth;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.bogus.domowygpx.apache.http.client.utils.ResponseUtils;
import org.bogus.domowygpx.application.Application;
import org.bogus.domowygpx.utils.Pair;
import org.bogus.geocaching.egpx.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.app.Service;
import android.content.Context;

public class OKAPI
{
    private volatile static boolean created;
    
    private final static Set<String> JSON_CONTENT_TYPES = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(
            "application/json", "application/x-javascript",
            "text/javascript", "text/x-javascript",
            "text/x-json")));
    
    
    final Context ctx;
    final OAuth oauth;
    
    public OKAPI(Application ctx)
    {
        if (created){
            throw new IllegalStateException();
        }
        created = true;
        this.ctx = ctx;
        this.oauth = new OAuth(ctx, this);
    }
    
    public String getAPIUrl()
    {
        return "http://opencaching.pl/okapi/";
    }
    
    /*public String maskObject(Object obj)
    {
        if (obj == null){
            return null;
        }
        return obj.toString().replace(getAPIKey(), "xxxxxxxxxx");
    }*/
    
    public static OKAPI getInstance(android.app.Application application)
    {
        return ((Application)application).getOkApi();
    }

    public static OKAPI getInstance(Service service)
    {
        return getInstance(service.getApplication());
    }

    public static OKAPI getInstance(Activity activity)
    {
        return getInstance(activity.getApplication());
    }

    public static OKAPI getInstance(Context context)
    {
        if (context instanceof Service){
           return getInstance((Service)context);
        } else
        if (context instanceof Activity){
            return getInstance((Activity)context);
        } else
        if (context instanceof Application){
            return getInstance((Application)context);
        } else {
            throw new IllegalStateException("Unknow context class=" + context.getClass().getName());
        }
    }

    public OAuth getOAuth()
    {
        return oauth;
    }
    
    /**
     * Tries to parse resposne as a JSON object and return error the most informative error code. Returns null, 
     * if the entity is not a JSON-content type, or has no error.
     * @param response
     * @return pair of error_code and error_message
     * @throws JSONException
     * @throws IOException
     */
    public Pair<String, String> getErrorReason(HttpResponse response, boolean doNotConsumeResponse)
    throws JSONException, IOException
    {
        List<Pair<String, String>> reasons = getErrorReasons(response, doNotConsumeResponse);
        if (reasons != null && reasons.size() > 0){
            return reasons.get(0);
        }
        return null;
    }
    
    /**
     * Tries to parse resposne as a JSON object and return error stack, from the most informative position 
     * to the most general. Returns null, if the entity is not a JSON-content type, or has no error.
     * @param response
     * @param doNotConsumeResponse if true, response will still be able to read
     * @return
     * @throws JSONException
     * @throws IOException
     */
    public List<Pair<String, String>> getErrorReasons(HttpResponse response, boolean doNotConsumeResponse)
    throws JSONException, IOException
    {
        if (response.getEntity() == null){
            return null;
        }
        InputStream is = null;
        try{
            if (doNotConsumeResponse){
                ResponseUtils.makeRepetable(response);
            }
            final HttpEntity entity = response.getEntity();
            final Header contentType = entity.getContentType();
            if (contentType != null){
                final HeaderElement[] elems = contentType.getElements();
                if (elems != null && elems.length > 0){
                    String contentTypeStr = elems[0].getName();
                    if (!JSON_CONTENT_TYPES.contains(contentTypeStr)){
                        return null;
                    }
                }
            }
            is = entity.getContent();
            final String charset = ResponseUtils.getContentEncoding(response, "UTF-8");
            final String data = IOUtils.toString(is, charset);
            final JSONTokener jt = new JSONTokener(data);
            final JSONObject resultObj = (JSONObject)jt.nextValue();
            if (resultObj.opt("error") == null){
                return null;
            }
            final JSONObject errorObj = resultObj.getJSONObject("error");
            final JSONArray reasonStackArr = errorObj.getJSONArray("reason_stack");
            final String developerMessage = errorObj.getString("developer_message");
            final int len = reasonStackArr.length();
            final List<Pair<String, String>> result = new ArrayList<Pair<String, String>>(len); 
            for (int i=len-1; i>=0; i--){
                Object obj = reasonStackArr.get(i);
                if (obj instanceof String){
                    result.add(new Pair<String, String>((String)obj, developerMessage));
                }
            }
            return result;
        }finally{
            IOUtils.closeQuietly(is);
        }
    }
    
    public int mapErrorCodeToMessage(String errorCode)
    {
        if (errorCode == null){
            return -1;
        }
        if (errorCode.equals("invalid_token")){
            return R.string.okapiErrorInvalidToken;
        } else 
        if (errorCode.equals("invalid_timestamp")){
            return R.string.okapiErrorInvalidTimestamp;
        } else {
            return -1;
        }
    }
}
