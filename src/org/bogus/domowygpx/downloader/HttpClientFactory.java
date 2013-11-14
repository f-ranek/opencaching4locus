package org.bogus.domowygpx.downloader;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.http.client.HttpClient;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionManagerFactory;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.bogus.domowygpx.apache.http.impl.client.DecompressingHttpClient;

import android.net.http.AndroidHttpClient;

public class HttpClientFactory
{
    public static final String RAW_SOCKET_RECEIVE_BUFFER_SIZE = "http.raw-socket.receive-buffer-size";
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
    
    public static HttpClient createHttpClient(boolean shared)
    {
        final DefaultHttpClient httpClient = new DefaultHttpClient();

        final HttpParams params = httpClient.getParams();
       
        if (shared){
            // params.setLongParameter(ClientPNames.CONN_MANAGER_TIMEOUT, 5 * 60 * 1000);
            
            params.setParameter(ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE, new ConnPerRoute(){
                @Override
                public int getMaxForRoute(HttpRoute route)
                {
                    // TODO: should be in accordance with number of worker threads
                    return 4;
                }});
            
            params.setParameter(ClientPNames.CONNECTION_MANAGER_FACTORY, new ClientConnectionManagerFactory(){
            @Override
            public ClientConnectionManager newInstance(HttpParams params, SchemeRegistry schemeRegistry)
            {
                return new ThreadSafeClientConnManager(params, schemeRegistry)/*{
                    
                }*/;
            }});
        }

        // TAKEN FROM ANDROID SOURCE CODE
        // Default connection and socket timeout of 20 seconds.  Tweak to taste.
        HttpConnectionParams.setConnectionTimeout(params, 20 * 1000);
        HttpConnectionParams.setSoTimeout(params, 20 * 1000);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        // END
        
        RawSocketBuffSizeFactoryDecorator.decorateHttpClient(httpClient);
        
        final DecompressingHttpClient decompressingHttpClient = new DecompressingHttpClient(httpClient) ;
        return decompressingHttpClient;
    }

    /**
     * Creates and returns individual (non-shared) {@link HttpClient}. The returned client must be 
     * @return
     */
    /*public static HttpClient createIndividualHttpClient()
    {
        return createHttpClientImpl(false);
    }*/

    
    public static void closeHttpClient(HttpClient httpClient)
    {
        try{
            httpClient.getConnectionManager().shutdown();
            if (httpClient instanceof AndroidHttpClient){
                ((AndroidHttpClient)httpClient).close();
            }
        }catch(Exception e){
            
        }
    }
}
