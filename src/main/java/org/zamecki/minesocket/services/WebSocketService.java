package org.zamecki.minesocket.services;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.zamecki.minesocket.config.MineSocketConfiguration;

import java.net.InetSocketAddress;
import java.net.BindException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.zamecki.minesocket.ModData.logger;

public class WebSocketService {
    private final MineSocketConfiguration config;
    private final MessageService messageService;
    private InetSocketAddress address;
    private CustomWebSocketServer wsServer;
    private final Set<WebSocket> connections = Collections.synchronizedSet(new HashSet<>());

    // Enumeration to control server states
    public enum ServerState {
        STOPPED, STARTING, RUNNING, STOPPING
    }

    private volatile ServerState state = ServerState.STOPPED;

    public WebSocketService(MineSocketConfiguration config, MessageService messageService) {
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
            messageService.handleMessage(message);
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
