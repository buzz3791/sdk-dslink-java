package org.dsa.iot.dslink.link;

import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.DSLinkHandler;
import org.dsa.iot.dslink.methods.Request;
import org.dsa.iot.dslink.methods.requests.*;
import org.dsa.iot.dslink.methods.responses.*;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeManager;
import org.dsa.iot.dslink.node.SubscriptionManager;
import org.dsa.iot.dslink.util.NodePair;
import org.dsa.iot.dslink.util.StreamState;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles incoming responses and outgoing requests.
 *
 * @author Samuel Grenier
 */
public class Requester extends Linkable {

    private final Map<Integer, RequestWrapper> reqs;

    /**
     * Current request ID to send to the client
     */
    private final AtomicInteger currentReqID = new AtomicInteger();

    /**
     * Current subscription ID to send to the client
     */
    private final AtomicInteger currentSubID = new AtomicInteger();

    /**
     * Mapping of path->sid
     */
    private final Map<String, Integer> subs = new ConcurrentHashMap<>();

    /**
     * Constructs a requester
     *
     * @param handler Handler for callbacks and data handling
     */
    public Requester(DSLinkHandler handler) {
        super(handler);
        reqs = new ConcurrentHashMap<>();
    }

    public void subscribe(Set<String> paths, Handler<SubscribeResponse> onResponse) {
        if (paths == null) {
            throw new NullPointerException("paths");
        }
        Map<String, Integer> subs = new HashMap<>();
        int min = currentSubID.getAndAdd(paths.size());
        int max = min + paths.size();
        Iterator<String> it = paths.iterator();
        while (min < max) {
            String path = NodeManager.normalizePath(it.next(), true);
            subs.put(path, min++);
        }
        SubscribeRequest req = new SubscribeRequest(subs);
        this.subs.putAll(subs);

        RequestWrapper wrapper = new RequestWrapper(req);
        wrapper.setSubHandler(onResponse);
        sendRequest(wrapper, currentReqID.incrementAndGet());
    }

    public void unsubscribe(Set<String> paths, Handler<UnsubscribeResponse> onResponse) {
        if (paths == null) {
            throw new NullPointerException("paths");
        }
        List<Integer> subs = new ArrayList<>();
        for (String path : paths) {
            path = NodeManager.normalizePath(path, true);
            Integer sid = this.subs.remove(path);
            if (sid != null) {
                subs.add(sid);
            }
        }
        UnsubscribeRequest req = new UnsubscribeRequest(subs);
        RequestWrapper wrapper = new RequestWrapper(req);
        wrapper.setUnsubHandler(onResponse);
        sendRequest(wrapper, currentReqID.incrementAndGet());
    }

    /**
     * Sends a request to the responder to close the given stream.
     *
     * @param rid Stream to close.
     */
    public void closeStream(int rid, Handler<CloseResponse> onResponse) {
        CloseRequest req = new CloseRequest();
        RequestWrapper wrapper = new RequestWrapper(req);
        wrapper.setCloseHandler(onResponse);
        sendRequest(wrapper, rid);
    }

    /**
     * Sends an invocation request.
     *
     * @param request Invocation request.
     */
    public void invoke(InvokeRequest request, Handler<InvokeResponse> onResponse) {
        RequestWrapper wrapper = new RequestWrapper(request);
        wrapper.setInvokeHandler(onResponse);
        sendRequest(wrapper);
    }

    /**
     * Sends a list request.
     *
     * @param request List request.
     */
    public void list(ListRequest request, Handler<ListResponse> onResponse) {
        RequestWrapper wrapper = new RequestWrapper(request);
        wrapper.setListHandler(onResponse);
        sendRequest(wrapper);
    }

    /**
     * Sends a set request.
     *
     * @param request Set request.
     */
    public void set(SetRequest request, Handler<SetResponse> onResponse) {
        RequestWrapper wrapper = new RequestWrapper(request);
        wrapper.setSetHandler(onResponse);
        sendRequest(wrapper);
    }

    /**
     * Sends a remove request.
     *
     * @param request Remove request.
     * @param onResponse Called when a response is received.
     */
    public void remove(RemoveRequest request, Handler<RemoveResponse> onResponse) {
        RequestWrapper wrapper = new RequestWrapper(request);
        wrapper.setRemoveHandler(onResponse);
        sendRequest(wrapper);
    }

    /**
     * Sends a request to the client.
     *
     * @param wrapper Request to send to the client.
     */
    private void sendRequest(RequestWrapper wrapper) {
        int rid = currentReqID.incrementAndGet();
        sendRequest(wrapper, rid);
    }

    /**
     * Sends a request to the client with a given request ID.
     *
     * @param wrapper Request to send to the client
     * @param rid Request ID to use
     */
    private void sendRequest(RequestWrapper wrapper, int rid) {
        final DSLink link = getDSLink();
        if (link == null) {
            return;
        }
        Request request = wrapper.getRequest();
        JsonObject obj = new JsonObject();
        request.addJsonValues(obj);
        {
            obj.putNumber("rid", rid);
            reqs.put(rid, wrapper);
        }
        obj.putString("method", request.getName());

        final JsonObject top = new JsonObject();
        JsonArray requests = new JsonArray();
        requests.addObject(obj);
        top.putArray("requests", requests);
        link.getClient().write(top);
    }

    /**
     * Handles incoming responses.
     *
     * @param in Incoming response.
     */
    public void parse(JsonObject in) {
        DSLink link = getDSLink();
        if (link == null) {
            return;
        }
        Integer rid = in.getInteger("rid");
        RequestWrapper wrapper = reqs.get(rid);
        Request request = wrapper.getRequest();
        String method = request.getName();
        NodeManager manager = link.getNodeManager();

        final String stream = in.getString("stream");
        boolean closed = StreamState.CLOSED.getJsonName().equals(stream);

        switch (method) {
            case "list":
                ListRequest listRequest = (ListRequest) request;
                Node node = manager.getNode(listRequest.getPath(), true).getNode();
                SubscriptionManager subs = link.getSubscriptionManager();
                ListResponse resp = new ListResponse(link, subs, rid, node);
                resp.populate(in);
                if (wrapper.getListHandler() != null) {
                    wrapper.getListHandler().handle(resp);
                }
                break;
            case "set":
                SetRequest setRequest = (SetRequest) request;
                NodePair pair = manager.getNode(setRequest.getPath(), true);
                SetResponse setResponse = new SetResponse(rid, pair);
                setResponse.populate(in);
                if (wrapper.getSetHandler() != null) {
                    wrapper.getSetHandler().handle(setResponse);
                }
                break;
            case "remove":
                RemoveRequest removeRequest = (RemoveRequest) request;
                pair = manager.getNode(removeRequest.getPath(), true);
                RemoveResponse removeResponse = new RemoveResponse(rid, pair);
                removeResponse.populate(in);
                if (wrapper.getRemoveHandler() != null) {
                    wrapper.getRemoveHandler().handle(removeResponse);
                }
                break;
            case "close":
                CloseResponse closeResponse = new CloseResponse(rid, null);
                closeResponse.populate(in);
                if (wrapper.getCloseHandler() != null) {
                    wrapper.getCloseHandler().handle(closeResponse);
                }
                break;
            case "subscribe":
                SubscribeResponse subResp = new SubscribeResponse(rid, link);
                subResp.populate(in);
                if (wrapper.getSubHandler() != null) {
                    wrapper.getSubHandler().handle(subResp);
                }
                break;
            case "unsubscribe":
                UnsubscribeResponse unsubResp = new UnsubscribeResponse(rid, link);
                unsubResp.populate(in);
                if (wrapper.getUnsubHandler() != null) {
                    wrapper.getUnsubHandler().handle(unsubResp);
                }
                break;
            case "invoke":
                InvokeRequest inReq = (InvokeRequest) request;
                node = manager.getNode(inReq.getPath(), true).getNode();
                InvokeResponse inResp = new InvokeResponse(rid, node);
                inResp.populate(in);
                if (wrapper.getInvokeHandler() != null) {
                    wrapper.getInvokeHandler().handle(inResp);
                }
                break;
            default:
                throw new RuntimeException("Unsupported method: " + method);
        }

        if (closed) {
            reqs.remove(rid);
        }
    }

    private static class RequestWrapper {

        private final Request request;

        private Handler<CloseResponse> closeHandler;
        private Handler<InvokeResponse> invokeHandler;
        private Handler<ListResponse> listHandler;
        private Handler<RemoveResponse> removeHandler;
        private Handler<SetResponse> setHandler;
        private Handler<SubscribeResponse> subHandler;
        private Handler<UnsubscribeResponse> unsubHandler;

        public RequestWrapper(Request request) {
            this.request = request;
        }

        public Request getRequest() {
            return request;
        }

        public Handler<CloseResponse> getCloseHandler() {
            return closeHandler;
        }

        public void setCloseHandler(Handler<CloseResponse> closeHandler) {
            this.closeHandler = closeHandler;
        }

        public Handler<InvokeResponse> getInvokeHandler() {
            return invokeHandler;
        }

        public void setInvokeHandler(Handler<InvokeResponse> invokeHandler) {
            this.invokeHandler = invokeHandler;
        }

        public Handler<ListResponse> getListHandler() {
            return listHandler;
        }

        public void setListHandler(Handler<ListResponse> listHandler) {
            this.listHandler = listHandler;
        }

        public Handler<RemoveResponse> getRemoveHandler() {
            return removeHandler;
        }

        public void setRemoveHandler(Handler<RemoveResponse> removeHandler) {
            this.removeHandler = removeHandler;
        }

        public Handler<SetResponse> getSetHandler() {
            return setHandler;
        }

        public void setSetHandler(Handler<SetResponse> setHandler) {
            this.setHandler = setHandler;
        }

        public Handler<SubscribeResponse> getSubHandler() {
            return subHandler;
        }

        public void setSubHandler(Handler<SubscribeResponse> subHandler) {
            this.subHandler = subHandler;
        }

        public Handler<UnsubscribeResponse> getUnsubHandler() {
            return unsubHandler;
        }

        public void setUnsubHandler(Handler<UnsubscribeResponse> unsubHandler) {
            this.unsubHandler = unsubHandler;
        }
    }
}