package org.dsa.iot.dslink.requester;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;
import net.engio.mbassy.bus.MBassador;

import org.dsa.iot.core.event.Event;
import org.dsa.iot.dslink.connection.Client;
import org.dsa.iot.dslink.events.ResponseEvent;
import org.dsa.iot.dslink.node.value.ValueUtils;
import org.dsa.iot.dslink.requester.requests.CloseRequest;
import org.dsa.iot.dslink.requester.requests.InvokeRequest;
import org.dsa.iot.dslink.requester.requests.ListRequest;
import org.dsa.iot.dslink.requester.requests.RemoveRequest;
import org.dsa.iot.dslink.requester.requests.Request;
import org.dsa.iot.dslink.requester.requests.SetRequest;
import org.dsa.iot.dslink.requester.requests.SubscribeRequest;
import org.dsa.iot.dslink.requester.requests.UnsubscribeRequest;
import org.dsa.iot.dslink.requester.responses.CloseResponse;
import org.dsa.iot.dslink.requester.responses.InvokeResponse;
import org.dsa.iot.dslink.requester.responses.ListResponse;
import org.dsa.iot.dslink.requester.responses.RemoveResponse;
import org.dsa.iot.dslink.requester.responses.Response;
import org.dsa.iot.dslink.requester.responses.SetResponse;
import org.dsa.iot.dslink.requester.responses.SubscriptionResponse;
import org.dsa.iot.dslink.requester.responses.UnsubscribeResponse;
import org.dsa.iot.dslink.util.Linkable;
import org.dsa.iot.dslink.util.StreamState;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Samuel Grenier
 */
public class Requester extends Linkable {

    private Map<Client, Map<Integer, Response<?>>> responseCache = new ConcurrentHashMap<>();

    public Requester(MBassador<Event> bus) {
        super(bus);
    }

    public int sendRequest(Client client, Request req) {
        return sendRequest(client, req, true);
    }

    @SneakyThrows
    public int sendRequest(@NonNull Client client, @NonNull Request req,
            boolean autoTrack) {
        ensureConnected();

        val obj = new JsonObject();
        int rid;
        {
            if (autoTrack) {
                rid = client.getRequestTracker().track(req);
            } else {
                rid = client.getRequestTracker().getNextID();
            }
            obj.putNumber("rid", rid);
            obj.putString("method", req.getName());
            req.addJsonValues(obj);
        }

        val top = new JsonObject();
        val requests = new JsonArray();
        requests.add(obj);
        top.putArray("requests", requests);
        client.write(top);
        System.out.println(client.getDsId() + " => " + top.encode());
        return rid;
    }

    @Override
    public void parse(Client client, JsonArray responses) {
        val it = responses.iterator();
        for (JsonObject o; it.hasNext();) {
            o = (JsonObject) it.next();
            handleResponse(client, o);
        }
    }

    public Response<?> handleResponse(Client client, JsonObject obj) {
        val rid = obj.getNumber("rid").intValue();
        val request = client.getRequestTracker().getRequest(rid);
        Response<?> resp = null;
        boolean doPublish = true;
        String name;
        if (rid != 0) {
            Map<Integer, Response<?>> map = responseCache.get(client);
            if (map == null) {
                map = new HashMap<>();
                responseCache.put(client, map);
            } else {
                resp = map.get(rid);
                if (resp instanceof InvokeResponse) {
                    ((InvokeResponse) resp).updateMeta(obj.getObject("meta"));
                }
            }

            // Response
            val state = obj.getString("stream");
            if (StreamState.CLOSED.jsonName.equals(state)) {
                client.getRequestTracker().untrack(rid);
                doPublish = false;
                if (resp != null) {
                    map.remove(rid);
                }
            } else if (resp == null) {
                // Stream is open and the response is not cached yet
                resp = getResponse(request, obj);
                map.put(rid, resp);
            }

            // Stream is closed and the response was not cached
            if (resp == null) {
                resp = getResponse(request, obj);
            }

            name = request.getName();
        } else {
            // Subscription update
            val req = new SubscribeRequest("/");
            resp = new SubscriptionResponse(req, getManager());
            name = req.getName();
        }
        if (doPublish) {
            JsonArray populate = obj.getArray("updates");
            if (populate != null) {
                resp.populate(populate);
            }
            synchronized (this) {
                val ev = new ResponseEvent(client, rid, name, resp);
                getBus().publish(ev);
            }
        }
        return resp;
    }

    public Request getRequest(JsonObject obj) {
        val name = obj.getString("method");
        if (name == null)
            return null;
        switch (name) {
            case "list":
                String path = obj.getString("path");
                if (path == null)
                    return null;
                return new ListRequest(path);
            case "set":
                path = obj.getString("path");
                Object value = obj.getField("value");
                if (path == null || value == null)
                    return null;
                return new SetRequest(path, ValueUtils.toValue(value));
            case "remove":
                path = obj.getString("path");
                if (path == null)
                    return null;
                return new RemoveRequest(path);
            case "invoke":
                path = obj.getString("path");
                val params = obj.getObject("params");
                if (path == null)
                    return null;
                return new InvokeRequest(path, params);
            case "subscribe":
                JsonArray paths = obj.getArray("paths");
                if (paths == null)
                    return null;
                String[] built = new String[paths.size()];
                for (int i = 0; i < paths.size(); i++) {
                    built[i] = paths.get(i);
                }
                return new SubscribeRequest(built);
            case "unsubscribe":
                paths = obj.getArray("paths");
                if (paths == null)
                    return null;
                built = new String[paths.size()];
                for (int i = 0; i < paths.size(); i++) {
                    built[i] = paths.get(i);
                }
                return new UnsubscribeRequest(built);
            case "close":
                val rid = obj.getNumber("rid");
                if (rid == null)
                    return null;
                return new CloseRequest(rid.intValue());
            default:
                return null;
        }
    }

    public Response<?> getResponse(Request req, JsonObject top) {
        val man = getManager();
        switch (req.getName()) {
            case "list":
                return new ListResponse((ListRequest) req, man);
            case "set":
                return new SetResponse((SetRequest) req);
            case "remove":
                return new RemoveResponse((RemoveRequest) req);
            case "invoke":
                val resp = new InvokeResponse((InvokeRequest) req, top.getArray("columns"));
                resp.updateMeta(top.getObject("meta"));
                return resp;
            case "subscribe":
                return new SubscriptionResponse((SubscribeRequest) req, man);
            case "unsubscribe":
                return new UnsubscribeResponse((UnsubscribeRequest) req);
            case "close":
                return new CloseResponse((CloseRequest) req);
            default:
                throw new RuntimeException("Unknown method");
        }
    }
}
