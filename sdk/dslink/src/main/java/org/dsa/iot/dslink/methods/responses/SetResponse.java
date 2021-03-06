package org.dsa.iot.dslink.methods.responses;

import org.dsa.iot.dslink.methods.Response;
import org.dsa.iot.dslink.methods.StreamState;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodePair;
import org.dsa.iot.dslink.node.Writable;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.node.value.ValueUtils;
import org.vertx.java.core.json.JsonObject;

/**
 * Handles a set response
 *
 * @author Samuel Grenier
 */
public class SetResponse implements Response {

    private final int rid;
    private final NodePair pair;

    public SetResponse(int rid, NodePair pair) {
        this.rid = rid;
        this.pair = pair;
    }

    @Override
    public int getRid() {
        return rid;
    }

    @Override
    public void populate(JsonObject in) {
    }

    @Override
    public JsonObject getJsonResponse(JsonObject in) {
        updateNode(in);

        JsonObject obj = new JsonObject();
        obj.putNumber("rid", rid);
        obj.putString("stream", StreamState.CLOSED.getJsonName());
        return obj;
    }

    @Override
    public JsonObject getCloseResponse() {
        return null;
    }

    private void updateNode(JsonObject in) {
        Node node = pair.getNode();
        Writable writable = node.getWritable();
        if (writable == null || writable == Writable.NEVER) {
            throw new RuntimeException("Not writable");
        }

        String ref = pair.getReference();
        Value value = ValueUtils.toValue(in.getField("value"));
        Value current = node.getValue();

        if (ref != null) {
            if (ref.startsWith("$")) {
                ref = ref.substring(1);
                node.setConfig(ref, value);
            } else if (ref.startsWith("@")) {
                ref = ref.substring(1);
                node.setAttribute(ref, value);
            } else {
                throw new RuntimeException("Not a valid reference: " + ref);
            }
        } else {
            checkValue(current, value);
            node.setValue(value);
        }
    }

    private void checkValue(Value current, Value other) {
        if (current == null) {
            return;
        }

        ValueType currType = current.getType();
        ValueType otherType = other.getType();
        if (currType != otherType) {
            String expected = currType.toJsonString();
            String got = otherType.toJsonString();
            String error = "Type mismatch ";
            error += "(got: " + got + ", expected: " + expected + ")";
            throw new RuntimeException(error);
        }
    }
}
