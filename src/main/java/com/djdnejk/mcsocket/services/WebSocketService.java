package com.djdnejk.mcsocket.services;

import com.djdnejk.mcsocket.config.MCsocketConfiguration;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.djdnejk.mcsocket.ModData.logger;

public class WebSocketService {
    private final MCsocketConfiguration config;
    private final MessageService messageService;
    private InetSocketAddress address;
    private CustomWebSocketServer wsServer;
    private final Set<WebSocket> connections = Collections.synchronizedSet(new HashSet<>());

    // Enumeration to control server states
    public enum ServerState {
        STOPPED, STARTING, RUNNING, STOPPING
    }

    private volatile ServerState state = ServerState.STOPPED;

    public WebSocketService(MCsocketConfiguration config, MessageService messageService) {
        this.config = config;
        this.messageService = messageService;
        this.address = new InetSocketAddress(config.host, config.port);
    }

    public boolean isRunning() {
        return state == ServerState.RUNNING;
    }

    public void broadcast(String message) {
        synchronized (connections) {
            for (WebSocket conn : connections) {
                if (conn.isOpen()) {
                    conn.send(message);
                }
            }
        }
    }

    /**
     * Starts the WebSocket server
     *
     * @return true if started successfully, false otherwise
     */
    public synchronized boolean tryToStart() {
        if (state != ServerState.STOPPED) {
            logger.error("Cannot start WebSocket server: current state is {}", state);
            return false;
        }

        try {
            state = ServerState.STARTING;
            wsServer = new CustomWebSocketServer(address);
            wsServer.setReuseAddr(true); // Allows address reuse immediately
            wsServer.setConnectionLostTimeout(30); // Timeout to detect lost connections
            configureSsl(wsServer);

            // Start in a separate thread
            Thread serverThread = new Thread(() -> {
                try {
                    wsServer.run();
                } catch (Exception e) {
                    logger.error("WebSocket server thread error: ", e);
                    state = ServerState.STOPPED;
                }
            });

            serverThread.setDaemon(true);
            serverThread.start();

            // Wait up to 5 seconds for the server to start
        for (int i = 0; i < 50; i++) {
            if (wsServer.isRunning()) {
                state = ServerState.RUNNING;
                logger.info("WebSocket server started on {}:{}", address.getHostString(), address.getPort());
                return true;
                }
                Thread.sleep(100);
            }

            // If not started after timeout, try to close
            if (wsServer != null) {
                wsServer.stop();
                wsServer = null;
            }

            state = ServerState.STOPPED;
            logger.error("Failed to start WebSocket server: timeout");
            return false;

        } catch (Exception e) {
            state = ServerState.STOPPED;
            if (e instanceof BindException) {
                logger.error("Failed to start WebSocket server: port {} is already in use", address.getPort());
            } else {
                logger.error("Failed to start WebSocket server: ", e);
            }
            return false;
        }
    }

    private void configureSsl(CustomWebSocketServer server) {
        if (!config.sslEnabled) {
            return;
        }

        SSLContext sslContext = createSslContext();
        if (sslContext == null) {
            logger.error("TLS was requested but SSL context could not be initialized; continuing without encryption.");
            return;
        }

        server.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
        logger.info("TLS enabled for WebSocket server (wss://)");
    }

    private SSLContext createSslContext() {
        try {
            Path keystorePath = Path.of(config.sslKeyStorePath);
            if (!Files.exists(keystorePath)) {
                logger.error("Keystore not found at {}", keystorePath.toAbsolutePath());
                return null;
            }

            char[] password = config.sslKeyStorePassword.toCharArray();
            KeyStore keyStore = null;
            try (InputStream inputStream = Files.newInputStream(keystorePath)) {
                keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(inputStream, password);
            } catch (Exception primary) {
                // Fallback to JKS if PKCS12 failed
                try (InputStream inputStream = Files.newInputStream(keystorePath)) {
                    keyStore = KeyStore.getInstance("JKS");
                    keyStore.load(inputStream, password);
                }
            }

            if (keyStore == null) {
                logger.error("Unable to load keystore from {}", keystorePath);
                return null;
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);
            return sslContext;
        } catch (Exception e) {
            logger.error("Failed to initialize SSL context: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Stops the WebSocket server with timeout
     *
     * @return true if initiated shutdown process successfully, false otherwise
     */
    public synchronized boolean tryToStop() {
        if (state != ServerState.RUNNING) {
            if (state == ServerState.STOPPED) {
                return true; // Already stopped
            }
            logger.warn("Cannot stop WebSocket server: current state is {}", state);
            return false;
        }

        if (wsServer == null) {
            state = ServerState.STOPPED;
            return true;
        }

        try {
            state = ServerState.STOPPING;

            // Create shutdown thread
            Thread shutdownThread = new Thread(() -> {
                try {
                    // Use a latch to wait for the shutdown
                    CountDownLatch closeLatch = new CountDownLatch(1);
                    wsServer.setCloseLatch(closeLatch);

                    // Start the shutdown process
                    wsServer.stop(1000);

                    // Wait up to 5 seconds for the shutdown
                    boolean closed = closeLatch.await(5, TimeUnit.SECONDS);

                    synchronized (WebSocketService.this) {
                        if (!closed) {
                            logger.warn("WebSocket server did not close gracefully within timeout");
                        } else {
                            logger.info("WebSocket server closed gracefully");
                        }

                        wsServer = null;
                        state = ServerState.STOPPED;
                        logger.info("WebSocket server stopped");
                    }
                } catch (Exception e) {
                    logger.error("Error stopping WebSocket server: ", e);
                    // Still consider the server as stopped
                    synchronized (WebSocketService.this) {
                        wsServer = null;
                        state = ServerState.STOPPED;
                    }
                }
            }, "WebSocket-Shutdown-Thread");

            shutdownThread.setDaemon(true);
            shutdownThread.start();

            // Return immediately while shutdown happens in background
            return true;
        } catch (Exception e) {
            logger.error("Error initiating WebSocket server shutdown: ", e);
            // Still consider the server as stopped
            wsServer = null;
            state = ServerState.STOPPED;
            return false;
        }
    }

    /**
     * Reloads the server with new configuration
     */
    public synchronized boolean tryToReload() {
        this.address = new InetSocketAddress(config.host, config.port);

        // First try to stop the server
        boolean stopped = tryToStop();

        // Wait a moment to ensure the port is released
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Try to start again
        boolean started = tryToStart();

        return stopped && started;
    }

    private boolean isAuthorized(WebSocket conn, ClientHandshake handshake) {
        if (!config.requireAuthToken) {
            return true;
        }

        String token = extractToken(handshake.getFieldValue("Authorization"));
        if (token.isEmpty()) {
            token = extractTokenFromQuery(handshake.getResourceDescriptor());
        }

        if (!token.equals(config.authToken)) {
            conn.close(1008, "Unauthorized");
            return false;
        }
        return true;
    }

    private String extractToken(String header) {
        if (header == null) {
            return "";
        }
        String trimmed = header.trim();
        if (trimmed.toLowerCase().startsWith("bearer ")) {
            return trimmed.substring(7);
        }
        return trimmed;
    }

    private String extractTokenFromQuery(String descriptor) {
        try {
            URI uri = new URI(descriptor);
            String query = uri.getQuery();
            if (query == null || query.isEmpty()) {
                return "";
            }

            for (String entry : query.split("&")) {
                String[] keyValue = entry.split("=", 2);
                if (keyValue.length == 2 && keyValue[0].equalsIgnoreCase("token")) {
                    return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Custom WebSocketServer implementation for better control
     */
    private class CustomWebSocketServer extends WebSocketServer {
        private volatile ServerState internalState;
        private CountDownLatch closeLatch;

        public CustomWebSocketServer(InetSocketAddress address) {
            super(address);
            this.internalState = ServerState.STOPPED;
        }

        public void setCloseLatch(CountDownLatch latch) {
            this.closeLatch = latch;
            this.internalState = ServerState.STOPPING;
        }

        public boolean isRunning() {
            return internalState == ServerState.RUNNING;
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            String clientId = conn.getRemoteSocketAddress().toString();
            if (!isAuthorized(conn, handshake)) {
                logger.warn("Rejected unauthorized connection from {}", clientId);
                return;
            }
            logger.info("New connection from {}", clientId);
            connections.add(conn);
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            String clientId = conn.getRemoteSocketAddress().toString();
            logger.info("Closed connection to {}: code={}, reason={}, remote={}",
                clientId, code, reason, remote);
            connections.remove(conn);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            String clientId = conn.getRemoteSocketAddress().toString();
            logger.info("Received message from {}: {}", clientId, message);
            messageService.handleMessage(conn, message);
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            String clientId = conn != null ? conn.getRemoteSocketAddress().toString() : "unknown";

            if (ex instanceof BindException) {
                logger.error("Port {} is already in use", address.getPort());
                this.internalState = ServerState.STOPPED;
            } else {
                logger.error("Error on connection to {}: {}", clientId, ex.getMessage());
            }

            if (conn != null) {
                conn.close();
            }
        }

        @Override
        public void onStart() {
            this.internalState = ServerState.RUNNING;
            logger.info("WebSocket server is starting...");
        }

        @Override
        public void start() {
            this.internalState = ServerState.STARTING;
            super.start();
        }

        @Override
        public void stop() throws InterruptedException {
            this.internalState = ServerState.STOPPING;
            super.stop();
            if (closeLatch != null) {
                closeLatch.countDown();
            }
            this.internalState = ServerState.STOPPED;
        }

        @Override
        public void stop(int timeout) throws InterruptedException {
            this.internalState = ServerState.STOPPING;
            super.stop(timeout);
            if (closeLatch != null) {
                closeLatch.countDown();
            }
            this.internalState = ServerState.STOPPED;
        }
    }
}
