package org.bogus.domowygpx.oauth;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Base64;

public class OAuth
{
    // private final static String LOG_TAG = "OAuth";
    
    private final static Charset UTF_8 = Charset.forName("UTF-8");
    private final static Pattern AMPERSAND = Pattern.compile("\\Q&");
    private final static Pattern EQUALS = Pattern.compile("\\Q=");
    
    final Context ctx;
    final OKAPI okApi;
    
    private String consumerKey;
    private String consumerSecret;
    
    private SharedPreferences config;
    private AtomicInteger nonce;
    
    OAuth(Context ctx, OKAPI okApi)
    {
        this.ctx = ctx;
        this.okApi = okApi;

        config = ctx.getSharedPreferences("egpx", Context.MODE_PRIVATE);
        nonce = new AtomicInteger((int)(System.currentTimeMillis()%100000));
    }
    
    public String getAPIKey()
    {
        if (consumerKey == null){
            final String apiSecret = OKAPISecrets.getApiKey(ctx);
            int idx = apiSecret.indexOf('|');
            if (idx > 0){
                consumerKey = apiSecret.substring(0, idx);
                consumerSecret = apiSecret.substring(idx+1);
            } else {
                consumerKey = apiSecret;
            }
        }
        return consumerKey;
    }
    
    private String getConsumerSecret()
    {
        getAPIKey();
        return consumerSecret;
    }
    
    public String getOAuthRequestTokenUrl()
    {
        return okApi.getAPIUrl() + "services/oauth/request_token";
    }
    
    public String getOAuthAuthorizeUrl()
    {
        return okApi.getAPIUrl() + "services/oauth/authorize";
    }
   
    public String getOAuthAccessTokenUrl()
    {
        return okApi.getAPIUrl() + "services/oauth/access_token";
    }

    /**
     * Indicates, whether {@link #gotRequestToken1Response(Map)} has been called with appropriate
     * arguments. Now, {@link #getAuthorize2Uri()} should be called.
     * 
     * @return
     */
    public boolean hasOAuth2()
    {
        boolean result = config.getString("oauth2_token", "").length() > 0
                && config.getString("oauth2_token_secret", "").length() > 0
                && config.getLong("oauth2_token_time", -1) > System.currentTimeMillis()-15L*60L*1000L;  
        return result;
    }    

    /**
     * Indicates, whether oauth dance has been performed, we have level 3 authorization, 
     * and {@link #signOAuth3Uri(URI)} can be called. 
     * <p><b>Warning</b> Successfull signing of the request does not mean, that the request will
     * succeed. I.e., user may have revoked our authorization.
     *   
     * @return
     */
    public boolean hasOAuth3()
    {
        boolean result = config.getString("oauth3_token", "").length() > 0
                && config.getString("oauth3_token_secret", "").length() > 0;
        return result;
    }    

    protected String signUrl(String url, String secret)
    {
        //Log.d(LOG_TAG, "signUrl, url=" + url + ", secret=" + secret);
        final URI uri = URI.create(url);
        final StringBuilder signatureBase0 = new StringBuilder(128);
        signatureBase0.append("GET");
        signatureBase0.append('&');
        {
            final StringBuilder signatureBase2 = new StringBuilder(128);
            final String scheme = uri.getScheme().toLowerCase(Locale.US);
            signatureBase2.append(scheme);
            signatureBase2.append("://");
            String authority = uri.getAuthority(); 
            boolean defaultPort = ("http".equals(scheme) && uri.getPort() == 80)
                    || ("https".equals(scheme) && uri.getPort() == 443);
            if (defaultPort) {
                // find the last : in the authority
                int index = authority.lastIndexOf(":");
                if (index >= 0) {
                    authority = authority.substring(0, index);
                }
            }
            signatureBase2.append(authority);
            String path = uri.getRawPath();
            if (path == null || path.length() == 0) {
                path = "/"; 
            }
            signatureBase2.append(path);
            signatureBase0.append(oauthEncode(signatureBase2.toString()));
        }
        {
            final String query = uri.getRawQuery();
            try{
                List<String[]> parameters = new ArrayList<String[]>();
                String[] paramsAndValues = AMPERSAND.split(query);
                for (String paramAndValue : paramsAndValues){
                    String[] pv = EQUALS.split(paramAndValue, 2);
                    String name = URLDecoder.decode(pv[0], UTF_8.name());
                    if ("oauth_signature".equals(name)){
                        continue;
                    }
                    String value = pv.length == 1 ? "" : URLDecoder.decode(pv[1], UTF_8.name());
                    String nameEncoded = oauthEncode(name);
                    String valueEncoded = oauthEncode(value);
                    parameters.add(new String[]{nameEncoded, valueEncoded});
                }
                Collections.sort(parameters, new Comparator<String[]>(){
    
                    @Override
                    public int compare(String[] lhs, String[] rhs)
                    {
                        String name1 = lhs[0];
                        String name2 = rhs[0];
                        int result = name1.compareTo(name2);
                        if (result == 0){
                            String value1 = lhs[0];
                            String value2 = rhs[0];
                            result = value1.compareTo(value2);
                        }
                        return result;
                    }});
                final StringBuilder signatureBase2 = new StringBuilder(128);
                for (String[] param : parameters){
                    if (signatureBase2.length() != 0){
                        signatureBase2.append('&');                        
                    }
                    signatureBase2.append(param[0]);
                    signatureBase2.append('=');
                    signatureBase2.append(param[1]);
                }
                
                signatureBase0.append('&');
                signatureBase0.append(oauthEncode(signatureBase2.toString()));
            }catch(UnsupportedEncodingException usee){
                throw new IllegalStateException(usee);
            }
        }
        //Log.d(LOG_TAG, "signUrl, string to sign=" + signatureBase0);
        String signature = getSignature(signatureBase0, secret);
        
        StringBuilder signedUrl = new StringBuilder(url.length() + 17 + signature.length());
        signedUrl.append(url);
        signedUrl.append("&oauth_signature=");
        signedUrl.append(oauthEncode(signature));
        
        //Log.d(LOG_TAG, "signUrl, signature=" + signature);
        
        return signedUrl.toString();
    }
    
    /**
     * Starts the OAuth dance. Returns URL, which should be executed, and return parameters should be
     * passed to {@link #gotRequestToken1Response(Map)}.
     * 
     * @return
     */
    public synchronized URI getRequestToken1Uri()
    {
        final StringBuilder sb = new StringBuilder(128);
        sb.append(getOAuthRequestTokenUrl());
        sb.append("?oauth_consumer_key=").append(urlEncode(getAPIKey()));
        sb.append("&oauth_signature_method=HMAC-SHA1");
        sb.append("&oauth_timestamp=").append(System.currentTimeMillis()/1000);
        sb.append("&oauth_nonce=").append(nonce.incrementAndGet());
        sb.append("&oauth_callback=oob");
        sb.append("&oauth_version=1.0");
        String signedUrl = signUrl(sb.toString(), null);
        
        final URI result = URI.create(signedUrl);

        forgetOAuthCredentials();
        
        return result;
    }
    
    /**
     * Saves values return from call to {@link #getRequestToken1Uri()}. Now, you can call {@link #getAuthorize2Uri()}
     * @param parameters
     * @throws IllegalArgumentException If the parameters are invalid, i.e. required parameter is missing.
     */
    public synchronized void gotRequestToken1Response(Map<String, String> parameters)
    throws IllegalArgumentException
    {
        //Log.d(LOG_TAG, "gotRequestToken1Response, parameters=" + parameters);
        final String token = parameters.get("oauth_token");
        final String secret = parameters.get("oauth_token_secret");
        if (token == null || token.length() == 0){
            throw new IllegalArgumentException("Missing oauth_token");
        }
        if (secret == null || secret.length() == 0){
            throw new IllegalArgumentException("Missing oauth_token_secret");
        }
        
        Editor editor = config.edit();
        editor.putString("oauth2_token", token);
        editor.putString("oauth2_token_secret", secret);
        editor.putLong("oauth2_token_time", System.currentTimeMillis());
        editor.commit();
    }
    
    /**
     * Returns the URL, where user should be redirected to confirm his decision.
     * @return
     */
    public synchronized URI getAuthorize2Uri()
    throws IllegalStateException
    {
        final String token = config.getString("oauth2_token", "");
        if (token.length() == 0){
            throw new IllegalStateException("No OAuth2 authorization");
        }
        final StringBuilder sb = new StringBuilder(128);
        sb.append(getOAuthAuthorizeUrl());
        sb.append("?oauth_token=").append(urlEncode(token));
        
        return URI.create(sb.toString());
    }
    
    /**
     * Returns the URL, which should be executed upon user providing PIN value from
     * call to {@link #getAuthorize2Uri()}. The returned parameters whould be passed to {@link #gotAccessToken3Response(Map)}
     * @return
     */
    public synchronized URI gotAuthorize2Pin(String verifierValue)
    throws IllegalStateException
    {
        //Log.d(LOG_TAG, "gotAuthorize2Pin, verifierValue=" + verifierValue);
        if (verifierValue == null || verifierValue.length() == 0){
            throw new IllegalArgumentException("No verifierValue");
        }
        final String secret = config.getString("oauth2_token_secret", "");
        final String token = config.getString("oauth2_token", "");
        if (secret.length() == 0 || token.length() == 0){
            throw new IllegalStateException("No OAuth2 authorization");
        }

        final StringBuilder sb = new StringBuilder(128);
        sb.append(getOAuthAccessTokenUrl());
        sb.append("?oauth_consumer_key=").append(urlEncode(getAPIKey()));
        sb.append("&oauth_signature_method=HMAC-SHA1");
        sb.append("&oauth_timestamp=").append(System.currentTimeMillis()/1000);
        sb.append("&oauth_nonce=").append(nonce.incrementAndGet());
        sb.append("&oauth_verifier=").append(urlEncode(verifierValue));
        sb.append("&oauth_version=1.0");
        sb.append("&oauth_token=").append(token);
        String signedUrl = signUrl(sb.toString(), secret);
        return URI.create(signedUrl);
    }

    /**
     * Saves values returned from call to {@link #gotAuthorize2Pin(String)}. When the method
     * returns successfully, we have OAuth level 3 authorization.
     * 
     * @param parameters
     * @throws IllegalArgumentException
     */
    public synchronized void gotAccessToken3Response(Map<String, String> parameters)
            throws IllegalArgumentException
    {
        //Log.d(LOG_TAG, "gotAccessToken3Response, parameters=" + parameters);
        final String token = parameters.get("oauth_token");
        final String secret = parameters.get("oauth_token_secret");
        if (token == null || token.length() == 0){
            throw new IllegalArgumentException("Missing oauth_token");
        }
        if (secret == null || secret.length() == 0){
            throw new IllegalArgumentException("Missing oauth_token_secret");
        }
        
        Editor editor = config.edit();
        editor.putString("oauth3_token", token);
        editor.putString("oauth3_token_secret", secret);
        editor.remove("oauth2_token_time");
        editor.remove("oauth2_token");
        editor.remove("oauth2_token_secret");
        editor.commit();
    }
    
    public synchronized void forgetOAuthCredentials()
    {
        Editor editor = config.edit();
        editor.remove("userUuid");
        editor.remove("oauth2_token");
        editor.remove("oauth2_token_secret");
        editor.remove("oauth3_token");
        editor.remove("oauth3_token_secret");
        editor.commit();        
    }
    
    /**
     * Signs given uri.
     * @param uri
     * @return
     * @throws IllegalStateException If we have not OAuth authorization level 3
     * @throws IllegalArgumentException If the given uri is not absolute
     */
    public URI signOAuth3Uri(URI uri)
    throws IllegalStateException, IllegalArgumentException
    {
        final String token = config.getString("oauth3_token", "");
        final String secret = config.getString("oauth3_token_secret", "");
        if (token.length() == 0 || secret.length() == 0){
            throw new IllegalStateException("No OAuth3 authorization");
        }
        
        if (!uri.isAbsolute()){
            throw new IllegalArgumentException("URI is not absolute");
        }
        
        final StringBuilder sb = new StringBuilder(128);
        sb.append(uri.toString());
        sb.append(uri.getRawQuery() == null ? '?' : '&');
        sb.append("oauth_consumer_key=").append(urlEncode(getAPIKey()));
        sb.append("&oauth_signature_method=HMAC-SHA1");
        sb.append("&oauth_timestamp=").append(System.currentTimeMillis()/1000);
        sb.append("&oauth_nonce=").append(nonce.incrementAndGet());
        sb.append("&oauth_token=").append(token);
        sb.append("&oauth_version=1.0");
        String signedUrl = signUrl(sb.toString(), secret);
        
        return URI.create(signedUrl);
    }
    
    protected static String oauthEncode(String string)
    {
        final CharBuffer cb = CharBuffer.allocate(1);
        final ByteBuffer bb = ByteBuffer.allocate(10);
        final CharsetEncoder encoder = UTF_8.newEncoder();
        final StringBuilder result = new StringBuilder(string.length()*2);
        for (int i=0; i<string.length(); i++){
            char c = string.charAt(i);
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
                    || c == '-' || c == '.' || c == '_' || c == '~'){
                result.append(c);
            } else {
                cb.clear();
                bb.clear();
                cb.append(c);
                cb.flip();
                encoder.encode(cb, bb, true);
                bb.flip();
                result.append('%');
                while (bb.hasRemaining()){
                    byte b = bb.get();
                    byte bh = (byte)((b>>4)&0x0F);
                    if (bh >= 10){
                        result.append((char)('A' + bh - 10));
                    } else {
                        result.append((char)('0' + bh));
                    }
                    byte bl = (byte)(b&0x0F);
                    if (bl >= 10){
                        result.append((char)('A' + bl - 10));
                    } else {
                        result.append((char)('0' + bl));
                    }
                }
            }
        }
        return result.toString();
    }
    
    @SuppressWarnings("deprecation")
    static String urlEncode(Object object)
    {
        if (object == null){
            return null;
        }
        return URLEncoder.encode(object.toString());
    }
    
    protected String getSignature(CharSequence baseString, String secret) 
    {
        try {
            final SecretKey key;
            final byte[] signatureBytes;
            {
                StringBuilder keyString = new StringBuilder();
                keyString.append(oauthEncode(getConsumerSecret()));
                keyString.append('&');
                if (secret != null){
                    keyString.append(oauthEncode(secret));
                }
                final ByteBuffer bb = UTF_8.encode(CharBuffer.wrap(keyString));
                key = new SecretKeySpec(
                    bb.array(), bb.arrayOffset(), bb.arrayOffset() + bb.limit(), "HmacSHA1");
            }
            
            {
                final Mac mac = Mac.getInstance("HmacSHA1");
                mac.init(key);
                
                final ByteBuffer bb = UTF_8.encode(CharBuffer.wrap(baseString));
                mac.update(bb.array(), bb.arrayOffset(), bb.arrayOffset() + bb.limit());
                
                signatureBytes = mac.doFinal();
            }
            String signature64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP /*| Base64.URL_SAFE*/);
            return signature64;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
