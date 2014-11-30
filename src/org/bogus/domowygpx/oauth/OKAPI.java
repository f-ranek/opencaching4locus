package org.bogus.domowygpx.oauth;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.bogus.android.AndroidUtils;
import org.bogus.domowygpx.apache.http.client.utils.ResponseUtils;
import org.bogus.domowygpx.utils.Pair;
import org.bogus.geocaching.egpx.BuildConfig;
import org.bogus.geocaching.egpx.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

public class OKAPI
{
    private final static String LOG_TAG = "OKAPI";
    private final static Set<String> JSON_CONTENT_TYPES = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(
            "application/json", "application/x-javascript",
            "text/javascript", "text/x-javascript",
            "text/x-json")));
    
    public final static String BRANCH_PL = "pl";
    public final static String BRANCH_DE = "de";
    
    private final SharedPreferences config;
    private final OAuth oauth;
    
    private final Map<String, String[]> installations;
    private Map<String, OKAPI> installationInstances;
    private boolean isLocked;
    
    private String installationCode;
    private String installationBranch;
    private String apiURL;
    
    private OKAPI(OKAPI base, String installationCode)
    {
        this.config = base.config;
        this.oauth = new OAuth(base.oauth);
        this.installations = base.installations;
        setInstallationCode(installationCode, false, true);
        this.isLocked = true;
    }
    
    public OKAPI(Context ctx)
    {
        InputStream is = null;
        try{
            Properties cnf = new Properties();
            is = OKAPI.class.getResourceAsStream("installations.properties");
            cnf.load(is);
            
            this.installations = new HashMap<String, String[]>();
            
            final Pattern p = Pattern.compile("\\Q,");
            for (Entry<Object, Object> item : cnf.entrySet()){
                String key = (String)item.getKey();
                if (key.length() != 2){
                    continue;
                }
                String val = (String)item.getValue();
                String[] data = p.split(val);
                if (data.length != 2){
                    continue;
                }
                if (data[0].length() != 2){
                    continue;
                }
                try{
                    URL url = new URL(data[1]);
                    if (BuildConfig.DEBUG){
                        Log.d(LOG_TAG, "Installation " + key + "=" + data[0] + "," + url);
                    }
                }catch(MalformedURLException mue){
                    continue;
                }
                this.installations.put(key, data);
            }
        }catch(IOException ioe){
            throw new IllegalStateException(ioe);
        }finally{
            IOUtils.closeQuietly(is);
        }
        
        this.oauth = new OAuth(ctx, this);
        
        this.config = ctx.getSharedPreferences("egpx", Context.MODE_PRIVATE);
        String installation = config.getString("okapi.installation", null); 
        // TODO: odgadnięcie instalacji na podstawie tego, czy pierwsze uruchomienie
        // bądź na podstawie geolokalizacji (dużo roboty)
        // ew. język urządzenia
        setInstallationCode(installation == null ? "pl" : installation, installation == null, false);
    }
    
    public String getAPIUrl()
    {
        return apiURL;
    }
    
    public String getInstallationCode()
    {
        return installationCode;
    }
    
    public String getPreferredLanguageCode()
    {
        return installationCode;
    }
    
    public String getBranchCode()
    {
        return installationBranch;
    }

    public void setInstallationCode(String installationCode){
        setInstallationCode(installationCode, false, false);
    }

    private void setInstallationCode(String installationCode, boolean firstTime, boolean skipConfigPersistance){
        if (isLocked){
            throw new IllegalStateException("Instance is locked");
        }
        String[] data = installations.get(installationCode);
        if (data == null){
            throw new IllegalArgumentException("No such installation: ".concat(installationCode));
        }
        if (!skipConfigPersistance){
            if (firstTime || (this.installationCode != null && !this.installationCode.equals(installationCode))){
                final Editor editor = config.edit();
                editor.putString("okapi.installation", installationCode);
                if (firstTime){
                    oauth.migratePlConfig(editor, installationCode);
                }
                AndroidUtils.applySharedPrefsEditor(editor);
            }
        }
        this.installationCode = installationCode;
        this.installationBranch = data[0];
        this.apiURL = data[1];
        this.oauth.onInstallationChanged();
    }
    
    /**
     * Return the OKAPI instance, whose {@link #getInstallationCode() installationCode} won't change
     * @return
     */
    public OKAPI lockInstallationCode()
    {
        if (isLocked){
            return this;
        } else {
            synchronized(this){
                if (installationInstances == null){
                    installationInstances = new HashMap<String, OKAPI>(2);
                }
            }
            synchronized(installationInstances){
                String installationCode = this.installationCode;
                OKAPI inst = installationInstances.get(installationCode);
                if (inst == null){
                    inst = new OKAPI(this, installationCode);
                    installationInstances.put(installationCode, inst);
                }
                return inst;
            }
        }
    }
    
    public static OKAPI getInstance(android.app.Application application)
    {
        return ((org.bogus.domowygpx.application.Application)application).getOkApi();
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
            if (BuildConfig.DEBUG){
                throw new IllegalStateException("Unknow context class=" + context.getClass().getName());
            } else {
                throw new IllegalStateException();
            }
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

    public void setUserName(String userName, String userUuid)
    {
        Editor editor = config.edit();
        editor.putString(installationCode.concat(":userName"), userName);
        editor.putString(installationCode.concat(":userUuid"), userUuid);
        editor.putString(installationCode + ":userUUID:" + userName, userUuid);
        AndroidUtils.applySharedPrefsEditor(editor);
    }
    
    public String getUserUuid(String userName)
    {
        final String key = userName == null ? installationCode + ":userUuid" : 
            installationCode + ":userUUID:" + userName;
        String userUuid = config.getString(key, null);
        return userUuid;
    }

    public String getUserName()
    {
        final String key = installationCode.concat(":userName");
        String userName = config.getString(key, null);
        return userName;
    }
}
