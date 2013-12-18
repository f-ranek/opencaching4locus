package org.bogus.domowygpx.activities;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.bogus.domowygpx.apache.http.client.utils.ResponseUtils;
import org.bogus.domowygpx.oauth.OAuth;
import org.bogus.domowygpx.oauth.OKAPI;
import org.bogus.domowygpx.utils.HttpClientFactory;
import org.bogus.domowygpx.utils.HttpClientFactory.CreateHttpClientConfig;
import org.bogus.geocaching.egpx.R;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class OAuthSigningActivity extends Activity
{
    private final static String LOG_TAG = "OAuthSigningActivity";
    
    private final DialogInterface.OnDismissListener dismissListener = new DialogInterface.OnDismissListener()
    {
        @Override
        public void onDismiss(DialogInterface dlg)
        {
            finish(Activity.RESULT_CANCELED);
        }
    };
    
    AlertDialog dialog;
    OKAPI okApi;
    OAuth oauth;
    HttpClient httpClient;

    volatile OAuthHttpRequest currentTask; 
    volatile HttpUriRequest currentRequest;
    volatile boolean cancelled;
    
    Button btnAuthorize, btnRelaunchAuthorizationPage, btnDone;
    EditText edtPin; 
    
    void showToast(final int textResId)
    {
        runOnUiThread(new Runnable(){
            @Override
            public void run()
            {
                Toast.makeText(OAuthSigningActivity.this, textResId, Toast.LENGTH_LONG).show();
            }});
    }
    
    synchronized HttpClient getHttpClient(){
        if (httpClient == null){
            CreateHttpClientConfig ccc = new CreateHttpClientConfig(this);
            ccc.preventCaching = true;
            httpClient = HttpClientFactory.createHttpClient(ccc);
            // ? HttpClientParams.setRedirecting(httpClient.getParams(), false);
        }
        return httpClient;        
    }
    
    class OAuthHttpRequest extends AsyncTask<URI, Void, Map<String, String>>
    {
        @Override
        protected void onPreExecute()
        {
            cancelled = false;
            btnAuthorize.setEnabled(false);
            btnRelaunchAuthorizationPage.setEnabled(false); 
            btnDone.setEnabled(false);
            edtPin.setEnabled(false);
            Toast.makeText(OAuthSigningActivity.this, R.string.infoOAuthWorkInProgress, Toast.LENGTH_SHORT).show();
        }
        
        @Override
        protected Map<String, String> doInBackground(URI... params)
        {
            URI uri = params[0];
            HttpGet get = new HttpGet(uri);
            HttpResponse response = null;
            try {
                currentRequest = get;
                response = getHttpClient().execute(get);
                StatusLine statusLine = response.getStatusLine();
                if (statusLine.getStatusCode() != 200 || response.getEntity() == null){
                    Log.w(LOG_TAG, "Failed to query=" + uri + ", " + statusLine);
                    ResponseUtils.logResponseContent(LOG_TAG, response);
                    // XXX customize message, how about bad PIN? - 401 ?
                    showToast(R.string.infoOAuthServerFailure);
                    return null;
                }
                
                final String charset = ResponseUtils.getContentEncoding(response, "UTF-8");
                final InputStream content = response.getEntity().getContent();
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
                content.close();
                return result2;
            } catch (IOException e) {
                if (!cancelled){
                    showToast(R.string.infoOAuthNetworkFailure);
                }
                Log.e(LOG_TAG, "Failed to query=" + uri, e);
                return null;
            } finally {
                currentRequest = null;
                ResponseUtils.closeResponse(response);
            }
        }
        
    }
    
    void cancelNetworkRequest()
    {
        cancelled = true;
        final HttpUriRequest currentRequest = this.currentRequest;
        if (currentRequest != null){
            try{
                currentRequest.abort();
            }catch(Exception e){
                // ignore
            }
        }
        final OAuthHttpRequest currentTask = this.currentTask;
        if (currentTask != null){
            currentTask.cancel(true);
        }
    }
    
    private void prepareSignInDialog()
    {
        final LayoutInflater inflater = LayoutInflater.from(this);
        final ViewGroup view = (ViewGroup)inflater.inflate(R.layout.activity_oauth_signing, null);
        
        btnAuthorize = (Button)view.findViewById(R.id.oauthBtnAuthorize);
        btnAuthorize.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                final URI uri1 = oauth.getRequestToken1Uri();
                Log.i(LOG_TAG, "OAuth URI 1 " + uri1);
                edtPin.setText(null);
                currentTask = new OAuthHttpRequest(){

                    @Override
                    protected void onPostExecute(Map<String, String> result)
                    {
                        try{
                            if (result != null){
                                oauth.gotRequestToken1Response(result);
                                final URI uri2 = oauth.getAuthorize2Uri();
                                Log.i(LOG_TAG, "OAuth URI 2 " + uri2);
                                Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri2.toString()));
                                OAuthSigningActivity.this.startActivity(intent);
                            }
                        }catch(IllegalArgumentException iae){
                            Log.e(LOG_TAG, "Failed to save response", iae);
                            Toast.makeText(OAuthSigningActivity.this, R.string.infoOAuthServerFailure2, Toast.LENGTH_LONG).show();
                        }finally{
                            currentTask = null;
                            refreshSigninDialogControls();
                        }
                    }
                };
                currentTask.execute(uri1);
            }
        });
        btnRelaunchAuthorizationPage = (Button)view.findViewById(R.id.oauthBtnRelaunchAuthorizationPage);
        btnRelaunchAuthorizationPage.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                try{
                    final URI uri2 = oauth.getAuthorize2Uri();
                    Log.i(LOG_TAG, "OAuth URI 2 " + uri2);
                    Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri2.toString()));
                    OAuthSigningActivity.this.startActivity(intent);
                }catch(IllegalStateException ise){
                    Log.e(LOG_TAG, "OAuth 2 expired", ise);
                }finally{
                    refreshSigninDialogControls();
                }
            }
        });
        edtPin = (EditText)view.findViewById(R.id.oauthEditNip);
        edtPin.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
            }
            
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
            }
            
            @Override
            public void afterTextChanged(Editable s)
            {
                boolean hasOAuth2 = oauth.hasOAuth2();
                btnDone.setEnabled(hasOAuth2 && s != null && s.length() > 0);
            }
        });
        btnDone = (Button)view.findViewById(R.id.oauthBtnDone);
        btnDone.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                final String pin = edtPin.getText().toString();
                final URI uri3 = oauth.gotAuthorize2Pin(pin);
                Log.i(LOG_TAG, "OAuth URI 3 " + uri3);
                currentTask = new OAuthHttpRequest(){

                    private void getCurrentUserName()
                    {
                        StringBuilder sb = new StringBuilder();
                        sb.append(okApi.getAPIUrl());
                        sb.append("services/users/user?fields=uuid%7Cusername");
                        URI uri = URI.create(sb.toString());
                        uri = oauth.signOAuth3Uri(uri);
                        HttpGet get = new HttpGet(uri);
                        HttpResponse response = null;
                        try {
                            currentRequest = get;
                            response = httpClient.execute(get);
                            StatusLine statusLine = response.getStatusLine();
                            if (statusLine.getStatusCode() != 200 || response.getEntity() == null){
                                Log.w(LOG_TAG, "Failed to query=" + sb + ", " + statusLine);
                                ResponseUtils.logResponseContent(LOG_TAG, response);
                                return ;
                            }
                            
                            final JSONObject obj = (JSONObject)ResponseUtils.getJSONObjectFromResponse(LOG_TAG, get, response);
                            String userUuid = (String)obj.opt("uuid");
                            String userName = (String)obj.opt("username");
                            if (userUuid == null || userName == null || userUuid.length() == 0 || userName.length() == 0){
                                 Log.e(LOG_TAG, "Ups, can not find out logged user name");
                            } else {
                                SharedPreferences config = OAuthSigningActivity.this.getSharedPreferences("egpx", Context.MODE_PRIVATE);
                                Editor editor = config.edit();
                                editor.putString("userName", userName);
                                editor.putString("userUuid", userUuid);
                                editor.putString("userUUID:" + userName, userUuid);
                                editor.commit();
                            }
                            
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Failed to query=" + sb, e);
                        } finally {
                            currentRequest = null;
                            ResponseUtils.closeResponse(response);
                        }
                    }
                    
                    @Override
                    protected Map<String, String> doInBackground(URI... params)
                    {
                        Map<String, String> result = super.doInBackground(params);
                        if (result != null){
                            try{
                                oauth.gotAccessToken3Response(result);
                                getCurrentUserName();
                                showToast(R.string.infoOAuthDone);
                            }catch(IllegalArgumentException iae){
                                Log.e(LOG_TAG, "Failed to save response", iae);
                                showToast(R.string.infoOAuthServerFailure2);
                            }
                        }
                        return result;
                    }
                    
                    @Override
                    protected void onPostExecute(Map<String, String> result)
                    {
                        if (result != null){
                            finish(Activity.RESULT_OK);
                        }
                        currentTask = null;
                        refreshSigninDialogControls();
                    }
                };
                currentTask.execute(uri3);
            }
        });
        
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(R.string.title_activity_oauth_signing);
        //dialogBuilder.setOnCancelListener(cancelListener);
        dialogBuilder.setView(view);
        
        dialog = dialogBuilder.create();
        dialog.show();
        dialog.setOnDismissListener(dismissListener);
        refreshSigninDialogControls();
        
    }
    
    void refreshSigninDialogControls()
    {
        boolean hasOAuth2 = oauth.hasOAuth2();
        btnAuthorize.setEnabled(true);
        btnRelaunchAuthorizationPage.setEnabled(hasOAuth2);
        edtPin.setEnabled(hasOAuth2);
        btnDone.setEnabled(hasOAuth2 && edtPin.getText() != null && edtPin.getText().length() > 0);
    }
    
    private void prepareSignOutDialog()
    {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(R.string.title_activity_oauth_signing);
        dialogBuilder.setNegativeButton(R.string.btnCancel, null);
        dialogBuilder.setPositiveButton(R.string.btnLogout, new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                oauth.forgetOAuthCredentials();
            }});
        dialogBuilder.setMessage(R.string.msgLogoutInfo);
        
        dialog = dialogBuilder.create();
        dialog.setOnDismissListener(dismissListener);
        dialog.show();
        
    }

    @Override
    public void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        oauth = (okApi= OKAPI.getInstance(this)).getOAuth();
        
        if (oauth.hasOAuth3()){
            prepareSignOutDialog();
        } else {
            prepareSignInDialog();
        }
    }

    public void finish(int resultCode){
        if (super.isFinishing()){
            Log.v(LOG_TAG, "Activity.finish() called several times");
            return ;
        }
        super.setResult(resultCode);
        super.finish();
    }
    
    @Override
    protected void onDestroy()
    {
        cancelNetworkRequest();
        
        HttpClientFactory.closeHttpClient(httpClient);
        httpClient = null;
        
        if (dialog != null){
            dialog.dismiss();
        }
        super.onDestroy();
    }
}
