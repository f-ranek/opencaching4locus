package org.bogus.domowygpx.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.HttpClient;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionManagerFactory;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.AbstractHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;
import org.bogus.domowygpx.apache.http.client.entity.CountingEntityInterceptor;
import org.bogus.domowygpx.apache.http.client.protocol.RequestAcceptEncoding;
import org.bogus.domowygpx.apache.http.client.protocol.ResponseContentEncoding;
import org.bogus.domowygpx.oauth.OAuthRevocationDetectorInterceptor;
import org.bogus.domowygpx.oauth.OAuthSigningInterceptor;
import org.bogus.domowygpx.oauth.OKAPI;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

public class HttpClientFactory
{
    private final static String LOG_TAG = "HttpClientFactory";
    
    public static class CreateHttpClientConfig
    {
        public final Context context;
        public OKAPI okapi;

        /** Shared client, instance created by {@link HttpClientFactory#createSharingContext(Context) */
        public SharingContext sharingContext;
        /** Add interceptor to preemptively add OAuth headers */
        public boolean authorizeRequests;
        /** Prevents HTTP caching */
        public boolean preventCaching;

        public CreateHttpClientConfig(Context context)
        {
            this.context = context;
        }
    }    
    
    /**
     * Size in bytes (integer), of the receive buffer size passed to the operating system
     */
    public static final String RAW_SOCKET_RECEIVE_BUFFER_SIZE = "http.raw-socket.receive-buffer-size";
    /**
     * Size in bytes (integer), of the send buffer size passed to the operating system
     */
    public static final String RAW_SOCKET_SEND_BUFFER_SIZE = "http.raw-socket.send-buffer-size";

    static class RawSocketBuffSizeFactoryDecorator implements SocketFactory
    {
        
        protected final SocketFactory delegate;
        protected final HttpParams httpParams;

        public RawSocketBuffSizeFactoryDecorator(SocketFactory delegate, HttpParams httpParams)
        {
            this.delegate = delegate;
            this.httpParams = httpParams;
        }

        protected void setBufferSize(Socket socket) throws SocketException
        {
            if (socket != null){
                int socketBuffSize = httpParams.getIntParameter(RAW_SOCKET_RECEIVE_BUFFER_SIZE, -1);
                if (socketBuffSize > 0){
                    socket.setReceiveBufferSize(socketBuffSize);
                }
                socketBuffSize = httpParams.getIntParameter(RAW_SOCKET_SEND_BUFFER_SIZE, -1);
                if (socketBuffSize > 0){
                    socket.setSendBufferSize(socketBuffSize);
                }
            }
        }
        
        @Override
        public Socket createSocket() throws IOException
        {
            Socket result = delegate.createSocket();
            setBufferSize(result);
            return result;
        }

        @Override
        public Socket connectSocket(Socket sock, String host, int port, InetAddress localAddress, int localPort,
            HttpParams params) throws IOException, UnknownHostException, ConnectTimeoutException
        {
            setBufferSize(sock);
            Socket result = delegate.connectSocket(sock, host, port, localAddress, localPort, params);
            return result;
        }

        @Override
        public boolean isSecure(Socket sock) throws IllegalArgumentException
        {
            return delegate.isSecure(sock);
        }
        
        public static void decorateHttpClient(HttpClient httpClient)
        {
            decorateSchemeRegistry(httpClient.getConnectionManager().getSchemeRegistry(), httpClient.getParams());
        }
        public static void decorateSchemeRegistry(SchemeRegistry sr, HttpParams httpParams)
        {
            if (httpParams == null){
                throw new NullPointerException();
            }
            synchronized(sr){
                final List<String> schemeNames = sr.getSchemeNames();
                for (String schemeName : schemeNames){
                    final Scheme scheme = sr.get(schemeName);
                    final SocketFactory socketFactory = scheme.getSocketFactory();
                    final int port = scheme.getDefaultPort();
                    
                    final SocketFactory newSocketFactory = 
                            socketFactory instanceof LayeredSocketFactory 
                            ? new RawSocketBuffSizeLayeredFactoryDecorator((LayeredSocketFactory)socketFactory, httpParams)
                            : new RawSocketBuffSizeFactoryDecorator(socketFactory, httpParams);
                    
                    final Scheme newScheme = new Scheme(schemeName, newSocketFactory, port);
                    sr.register(newScheme);
                }
                
            }
        }
    }
    
    static class RawSocketBuffSizeLayeredFactoryDecorator 
        extends RawSocketBuffSizeFactoryDecorator 
        implements LayeredSocketFactory
    {
        private final LayeredSocketFactory delegate;

        public RawSocketBuffSizeLayeredFactoryDecorator(LayeredSocketFactory delegate, HttpParams httpParams)
        {
            super(delegate, httpParams);
            this.delegate = delegate;
        }
        
        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException,
            UnknownHostException
        {
            setBufferSize(socket);
            Socket result = delegate.createSocket(socket, host, port, autoClose);
            // setBufferSize(result);
            return result;
        }

    }
    
    public static class MyHttpParams extends AbstractHttpParams
    {
        /** Map of HTTP parameters that this collection contains. */
        Map<String, Object> parameters;

        @Override
        public Object getParameter(final String name) {
            // See if the parameter has been explicitly defined
            Object param = null;
            if (this.parameters != null) {
                param = this.parameters.get(name);
            }    
            return param;
        }

        @Override
        public HttpParams setParameter(final String name, final Object value) {
            if (this.parameters == null) {
                this.parameters = new HashMap<String, Object>();
            }
            this.parameters.put(name, value);
            return this;
        }
        
        @Override
        public boolean removeParameter(String name) {
            if (this.parameters == null) {
                return false;
            }
            //this is to avoid the case in which the key has a null value
            if (this.parameters.containsKey(name)) {
                this.parameters.remove(name);
                return true;
            } else {
                return false;
            }
        }
        
        @Override
        public HttpParams copy() {
            MyHttpParams result = new MyHttpParams();
            if (parameters != null){
                result.parameters = new HashMap<String, Object>(this.parameters);
            }
            return result;
        }
    }
    
    public static class SharingContext {
        MyHttpParams params;
        ClientConnectionManager ccm;
    }
    
    /** 
     * Creates config for shared client, which means it can be used by differet threads simultaneously.
     * The config may be shared (used) across many invocations to {@link #createHttpClient} 
     */
    public static SharingContext createSharingContext(Context context)
    {
        final SharedPreferences config = context.getSharedPreferences("egpx", Context.MODE_PRIVATE);
        final MyHttpParams params = new MyHttpParams();
        // max connections per route
        final int maxConnectionsPerHost = config.getInt("HttpClientFactory_maxConnectionsPerHost", 4);
        // total max connections per HttpClient
        final int maxTotalConnections = config.getInt("HttpClientFactory_maxTotalConnections", 16);
        // timeout (in seconds) for a connection while blocking on HttpClientFactory_maxConnectionsPerHost
        // or HttpClientFactory_maxTotalConnections limit
        final long connectionTimeout = config.getInt("HttpClientFactory_waitForConnectionTimeout", 5*60);

        ConnManagerParams.setTimeout(params, connectionTimeout * 1000L);
        ConnManagerParams.setMaxTotalConnections(params, maxTotalConnections);
        
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRoute(){
            @Override
            public int getMaxForRoute(HttpRoute route)
            {
                return maxConnectionsPerHost;
            }});
        params.setParameter(ClientPNames.CONNECTION_MANAGER_FACTORY, new ClientConnectionManagerFactory(){
        @Override
        public ClientConnectionManager newInstance(HttpParams params, SchemeRegistry schemeRegistry)
        {
            return new ThreadSafeClientConnManager(params, schemeRegistry);
        }});
        final SharingContext result = new SharingContext();
        result.params = params;
        return result;
    }
    
    public static HttpClient createHttpClient(final CreateHttpClientConfig cfg)
    {
        final SharedPreferences config = cfg.context.getSharedPreferences("egpx", Context.MODE_PRIVATE);
        
        if (config.getBoolean("HttpClientFactory_enableHeadersLogging", false)){
            java.util.logging.Logger.getLogger("org.apache.http.headers").setLevel(java.util.logging.Level.FINEST);
        }
        
        DefaultHttpClient httpClient = new DefaultHttpClient()
        {
            @Override
            protected ClientConnectionManager createClientConnectionManager() {
                if (cfg.sharingContext != null){ 
                    ClientConnectionManager ccm = cfg.sharingContext.ccm;
                    if (ccm == null){
                        ccm = super.createClientConnectionManager();
                        cfg.sharingContext.ccm = ccm;
                    }
                    return ccm;
                } else {
                    return super.createClientConnectionManager();
                }
            }
        };

        final HttpParams params = httpClient.getParams();
       
        if (cfg.sharingContext != null && cfg.sharingContext.params.parameters != null){
            for (Entry<String, Object> param : cfg.sharingContext.params.parameters.entrySet()){
                params.setParameter(param.getKey(), param.getValue());
            }
        }

        // timeout to wait for a connection establishment (in seconds)
        final int connectionTimeout = config.getInt("HttpClientFactory_connectionTimeout", 60);
        // timeout to wait for data (in seconds)
        final int socketTimeout = config.getInt("HttpClientFactory_socketTimeout", connectionTimeout);
        // buffer size for recive and send data to sockets
        // NOTE: internal operating system socket buffers can be set using RAW_SOCKET_RECEIVE_BUFFER_SIZE
        // and RAW_SOCKET_SEND_BUFFER_SIZE parameter names (int)
        final int socketBufferSize = config.getInt("HttpClientFactory_socketBufferSize", 8192);

        HttpConnectionParams.setConnectionTimeout(params, connectionTimeout * 1000);
        HttpConnectionParams.setSoTimeout(params, socketTimeout * 1000);
        HttpConnectionParams.setSocketBufferSize(params, socketBufferSize);
        
        try{
            final String packageName = cfg.context.getPackageName();
            final PackageInfo packageInfo = cfg.context.getPackageManager().getPackageInfo(packageName, 0);
            String userAgent = "Apache-HttpClient/AwaryjniejszyGPX " + packageInfo.versionCode;
            HttpProtocolParams.setUserAgent(params, userAgent);
        }catch(NameNotFoundException nnfe){
            // should not happen ;)
        }
        RawSocketBuffSizeFactoryDecorator.decorateHttpClient(httpClient);
        
        boolean disableCompression = config.getBoolean("HttpClientFactory_disableCompression", false);
        if (!disableCompression){
            // setup compression
            httpClient.addRequestInterceptor(new RequestAcceptEncoding());
            httpClient.addResponseInterceptor(new ResponseContentEncoding());
        } 
        httpClient.addResponseInterceptor(new CountingEntityInterceptor());
        if (cfg.authorizeRequests){
            httpClient.addRequestInterceptor(new OAuthSigningInterceptor(cfg.context, cfg.okapi));
            httpClient.addResponseInterceptor(new OAuthRevocationDetectorInterceptor(cfg.context, cfg.okapi));
        }
        if (cfg.preventCaching){
            httpClient.addRequestInterceptor(new HttpRequestInterceptor(){
                @Override
                public void process(HttpRequest request, HttpContext context) throws HttpException, IOException
                {
                    request.setHeader("Cache-Control", "no-cache");
                    request.addHeader("Pragma", "no-cache");
                }});
        }
        return httpClient;
    }
    
    public static void closeHttpClient(HttpClient httpClient)
    {
        if (httpClient != null){
            try{
                httpClient.getConnectionManager().shutdown();
            }catch(Exception e){
                Log.d(LOG_TAG, "Failed to close HttpClient", e);
            }
        }
    }
    
    public static void closeSharingContext(SharingContext sharingContext)
    {
        if (sharingContext != null){
            try{
                final ClientConnectionManager ccm = sharingContext.ccm;
                if (ccm != null){
                    ccm.shutdown();
                }
            }catch(Exception e){
                Log.d(LOG_TAG, "Failed to close SharingContext", e);
            }
        }
    }

}
