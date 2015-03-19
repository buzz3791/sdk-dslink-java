package org.dsa.iot.dslink.connection.connector.client;

import net.engio.mbassy.bus.MBassador;

import org.dsa.iot.core.URLInfo;
import org.dsa.iot.core.Utils;
import org.dsa.iot.core.event.Event;
import org.dsa.iot.dslink.connection.ClientConnector;
import org.dsa.iot.dslink.connection.handshake.HandshakePair;
import org.dsa.iot.dslink.events.AsyncExceptionEvent;
import org.dsa.iot.dslink.events.ConnectedToServerEvent;
import org.dsa.iot.dslink.events.IncomingDataEvent;
import org.dsa.iot.dslink.requester.RequestTracker;
import org.dsa.iot.dslink.responder.ResponseTracker;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.json.JsonObject;

/**
 * @author Samuel Grenier
 */
public class WebSocketConnector extends ClientConnector {

    protected HttpClient client;
    protected WebSocket socket;

    private boolean connecting = false;
    private boolean connected = false;

    public WebSocketConnector(MBassador<Event> bus,
                              URLInfo info,
                              HandshakePair pair,
                              RequestTracker reqTracker,
                              ResponseTracker respTracker) {
        super(bus, info, pair, reqTracker, respTracker);
    }

    @Override
    public synchronized void connect(final boolean sslVerify) {
        connecting = true;
        client = Utils.VERTX.createHttpClient();
        client.setHost(getDataEndpoint().host).setPort(getDataEndpoint().port);
        client.setMaxWebSocketFrameSize(Integer.MAX_VALUE);
        if (getDataEndpoint().secure) {
            client.setSSL(true);
            client.setVerifyHost(sslVerify);
        }

        client.exceptionHandler(new Handler<Throwable>() {
            @Override
            public void handle(Throwable event) {
                getBus().publish(new AsyncExceptionEvent(event));
                connecting = false;
                connected = false;
            }
        });

        client.connectWebsocket(getPath(), new Handler<WebSocket>() {
            @Override
            public void handle(WebSocket event) {
                connected = true;
                connecting = false;
                socket = event;

                getBus().publish(new ConnectedToServerEvent(WebSocketConnector.this));
                event.dataHandler(new Handler<Buffer>() {
                    @Override
                    public void handle(Buffer event) {
                        JsonObject data = new JsonObject(event.toString());
                        getBus().publish(new IncomingDataEvent(WebSocketConnector.this, data));
                    }
                });

                event.endHandler(getDisconnectHandler());
                event.closeHandler(getDisconnectHandler());
            }
        });
    }

    @Override
    public synchronized void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public synchronized boolean write(JsonObject obj) {
        if (socket != null) {
            socket.writeTextFrame(obj.encode());
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean isConnecting() {
        return connecting;
    }

    @Override
    public synchronized boolean isConnected() {
        return connected;
    }

    private Handler<Void> getDisconnectHandler() {
        return new Handler<Void>() {
            @Override
            public void handle(Void event) {
                synchronized (WebSocketConnector.this) {
                    connecting = false;
                    connected = false;
                }
            }
        };
    }
}
