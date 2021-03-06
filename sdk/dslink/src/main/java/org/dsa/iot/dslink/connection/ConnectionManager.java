package org.dsa.iot.dslink.connection;

import org.dsa.iot.dslink.config.Configuration;
import org.dsa.iot.dslink.connection.connector.WebSocketConnector;
import org.dsa.iot.dslink.handshake.LocalHandshake;
import org.dsa.iot.dslink.handshake.RemoteHandshake;
import org.dsa.iot.dslink.util.Objects;
import org.dsa.iot.dslink.util.URLInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Samuel Grenier
 */
public class ConnectionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionManager.class);

    private final Configuration configuration;
    private final LocalHandshake localHandshake;

    private Handler<ClientConnected> preInitHandler;
    private DataHandler handler;
    private NetworkClient client;
    private int delay = 1;

    private ScheduledFuture<?> future;
    private boolean running;

    public ConnectionManager(Configuration configuration,
                             LocalHandshake localHandshake) {
        this.configuration = configuration;
        this.localHandshake = localHandshake;
    }

    public DataHandler getHandler() {
        return handler;
    }

    /**
     * The pre initialization handler allows the dslink to be configured
     * before the link is actually connected to the server.
     *
     * @param onClientInit Client initialization handler
     */
    public void setPreInitHandler(Handler<ClientConnected> onClientInit) {
        this.preInitHandler = onClientInit;
    }

    public synchronized void start(final Handler<ClientConnected> onClientConnected) {
        stop();
        running = true;

        final ScheduledThreadPoolExecutor stpe = Objects.getDaemonThreadPool();
        stpe.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (ConnectionManager.this) {
                    if (!running) {
                        return;
                    }
                }
                LOGGER.debug("Initiating connection sequence");
                RemoteHandshake currentHandshake = generateHandshake(new Handler<Exception>() {
                    @Override
                    public void handle(Exception event) {
                        LOGGER.error("Failed to complete handshake: {}", event.getMessage());
                        reconnect();
                    }
                });

                if (currentHandshake == null) {
                    return;
                }

                int updateInterval = currentHandshake.getUpdateInterval();
                if (handler == null) {
                    handler = new DataHandler(updateInterval);
                }

                boolean req = localHandshake.isRequester();
                boolean resp = localHandshake.isResponder();
                final ClientConnected cc = new ClientConnected(req, resp);
                cc.setHandler(handler);

                if (preInitHandler != null) {
                    preInitHandler.handle(cc);
                }

                ConnectionType type = configuration.getConnectionType();
                switch (type) {
                    case WEB_SOCKET:
                        WebSocketConnector connector = new WebSocketConnector(handler);
                        connector.setEndpoint(configuration.getAuthEndpoint());
                        connector.setRemoteHandshake(currentHandshake);
                        connector.setLocalHandshake(localHandshake);
                        connector.setOnConnected(new Handler<Void>() {
                            @Override
                            public void handle(Void event) {
                                if (onClientConnected != null) {
                                    onClientConnected.handle(cc);
                                }
                                cc.connected();
                            }
                        });

                        connector.setOnDisconnected(new Handler<Void>() {
                            @Override
                            public void handle(Void event) {
                                if (running) {
                                    LOGGER.warn("WebSocket connection failed");
                                    reconnect();
                                }
                            }
                        });

                        connector.setOnException(new Handler<Throwable>() {
                            @Override
                            public void handle(Throwable event) {
                                LOGGER.error("Connector exception", event);
                            }
                        });

                        connector.setOnData(new Handler<JsonObject>() {
                            @Override
                            public void handle(JsonObject event) {
                                getHandler().processData(event);
                            }
                        });

                        client = connector;
                        handler.setClient(connector);
                        connector.start();
                        break;
                    default:
                        throw new RuntimeException("Unhandled type: " + type);
                }
            }
        });
    }

    public synchronized void stop() {
        running = false;

        if (future != null) {
            future.cancel(false);
            future = null;
        }

        if (client != null) {
            client.close();
            client = null;
        }
    }

    private RemoteHandshake generateHandshake(Handler<Exception> errorHandler) {
        try {
            synchronized (this) {
                if (!running) {
                    return null;
                }
            }
            URLInfo auth = configuration.getAuthEndpoint();
            return RemoteHandshake.generate(localHandshake, auth);
        } catch (Exception e) {
            if (errorHandler != null) {
                errorHandler.handle(e);
            }
        }
        return null;
    }

    private synchronized void reconnect() {
        if (!running) {
            return;
        }

        LOGGER.info("Reconnecting in {} seconds", delay);
        future = Objects.getDaemonThreadPool().schedule(new Runnable() {
            @Override
            public void run() {
                start(new Handler<ClientConnected>() {
                    @Override
                    public void handle(ClientConnected event) {
                        LOGGER.info("Connection established");
                        delay = 1;
                    }
                });
                delay *= 2;
                int cap = 60;
                if (delay > cap) {
                    delay = cap;
                }

                future = null;
            }
        }, delay, TimeUnit.SECONDS);
    }

    public static class ClientConnected {

        private final boolean isRequester;
        private final boolean isResponder;

        private Handler<ClientConnected> onRequesterConnected;
        private Handler<ClientConnected> onResponderConnected;
        private DataHandler handler;

        public ClientConnected(boolean isRequester,
                               boolean isResponder) {
            this.isRequester = isRequester;
            this.isResponder = isResponder;
        }

        public DataHandler getHandler() {
            return handler;
        }

        void setHandler(DataHandler handler) {
            this.handler = handler;
        }

        public boolean isRequester() {
            return isRequester;
        }

        public boolean isResponder() {
            return isResponder;
        }

        public void setRequesterOnConnected(Handler<ClientConnected> handler) {
            this.onRequesterConnected = handler;
        }

        public void setResponderOnConnected(Handler<ClientConnected> handler) {
            this.onResponderConnected = handler;
        }

        void connected() {
            if (onRequesterConnected != null) {
                onRequesterConnected.handle(this);
            }

            if (onResponderConnected != null) {
                onResponderConnected.handle(this);
            }
        }
    }
}
