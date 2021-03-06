package org.dsa.iot.dslink.node;

import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.methods.responses.ListResponse;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueUtils;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles subscriptions for values and paths.
 * @author Samuel Grenier
 */
public class SubscriptionManager {

    private final Object valueLock = new Object();

    private final Map<Node, ListResponse> pathSubs = new ConcurrentHashMap<>();
    private final Map<Node, Integer> valueSubsNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Node> valueSubsSids = new ConcurrentHashMap<>();
    private final DSLink link;

    public SubscriptionManager(DSLink link) {
        this.link = link;
    }

    /**
     * Tests whether the node has a value subscription that a remote endpoint
     * is listening to.
     *
     * @param node Node to test.
     * @return Whether the node has a value subscription.
     */
    public boolean hasValueSub(Node node) {
        return valueSubsNodes.containsKey(node);
    }

    /**
     * Tests whether the node has a path subscription that a remote endpoint
     * is listening to.
     *
     * @param node Node to test.
     * @return Whether the onde has a path subscription.
     */
    public boolean hasPathSub(Node node) {
        return pathSubs.containsKey(node);
    }

    /**
     * Adds a value subscription to the designated node. This will allow a node
     * to publish a value update and have it updated to the remote endpoint if
     * it is subscribed.
     *
     * @param node Node to subscribe to
     * @param sid Subscription ID to send back to the client
     */
    public void addValueSub(Node node, int sid) {
        synchronized (valueLock) {
            valueSubsNodes.put(node, sid);
            valueSubsSids.put(sid, node);
        }
        postValueUpdate(node);
        node.getListener().postOnSubscription();
    }

    /**
     * Removes a value subscription from the designated node. The remote
     * endpoint will no longer receive updates when the value updates.
     *
     * @param sid Subscription ID to unsubscribe
     */
    public void removeValueSub(int sid) {
        Node node;
        synchronized (valueLock) {
            node = valueSubsSids.remove(sid);
            if (node != null) {
                valueSubsNodes.remove(node);
            }
        }
        if (node != null) {
            node.getListener().postOnUnsubscription();
        }
    }

    /**
     * Removes a value subscription from the designated node. The remote
     * endpoint will no longer receive updates when the value updates.
     *
     * @param node Subscribed node to unsubscribe
     */
    public void removeValueSub(Node node) {
        Integer sid;
        synchronized (valueLock) {
            sid = valueSubsNodes.remove(node);
            if (sid != null) {
                valueSubsSids.remove(sid);
            }
        }
        if (sid != null) {
            node.getListener().postOnUnsubscription();
        }
    }

    /**
     * Adds a path subscription to the designated node. This will allow a node
     * to publish a child update and have it updated to the remote endpoint if
     * it is subscribed.
     *
     * @param node Node to subscribe to
     * @param resp Response to send updates to
     */
    public void addPathSub(Node node, ListResponse resp) {
        pathSubs.put(node, resp);
    }

    /**
     * Removes the node from being listened to for children updates.
     *
     * @param node Node to unsubscribe to.
     */
    public void removePathSub(Node node) {
        ListResponse resp = pathSubs.remove(node);
        if (resp != null) {
            Map<String, Node> children = node.getChildren();
            if (children != null) {
                for (Node child : children.values()) {
                    resp = pathSubs.get(child);
                    if (resp != null) {
                        resp.getCloseResponse();
                    }
                }
            }
        }
    }

    /**
     * Posts a child update to notify all remote endpoints of an update.
     *
     * @param child Updated child.
     * @param removed Whether the child was removed or not.
     */
    public void postChildUpdate(Node child, boolean removed) {
        ListResponse resp = pathSubs.get(child.getParent());
        if (resp != null) {
            resp.childUpdate(child, removed);
        }
    }

    /**
     * Posts a value update to notify all the remote endpoints of a node
     * value update.
     *
     * @param node Updated node.
     */
    public void postValueUpdate(Node node) {
        Integer sid = valueSubsNodes.get(node);
        if (sid != null) {
            JsonArray updates = new JsonArray();
            {
                JsonArray update = new JsonArray();
                update.addNumber(sid);

                Value value = node.getValue();
                if (value != null) {
                    ValueUtils.toJson(update, value);
                    update.addString(value.getTimeStamp());
                } else {
                    update.add(null);
                }

                updates.addArray(update);
            }

            JsonObject resp = new JsonObject();
            resp.putNumber("rid", 0);
            resp.putArray("updates", updates);
            link.getWriter().writeResponse(resp);
        }
    }
}
