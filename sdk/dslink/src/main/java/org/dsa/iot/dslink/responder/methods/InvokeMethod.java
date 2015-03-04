package org.dsa.iot.dslink.responder.methods;

import lombok.NonNull;
import lombok.val;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.util.StreamState;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 * @author Samuel Grenier
 */
public class InvokeMethod extends Method {

    @NonNull private final Node node;
    
    public InvokeMethod(Node node,
                        JsonObject request) {
        super(request);
        this.node = node;
    }

    @Override
    public JsonArray invoke() {
        val handler = node.getAction();
        if (handler != null && handler.hasPermission()) {
            handler.invoke(getRequest());
        } else {
            throw new RuntimeException("Not invokable");
        }
        setState(StreamState.CLOSED);
        return null;
    }
}