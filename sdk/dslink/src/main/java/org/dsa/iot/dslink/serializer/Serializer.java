package org.dsa.iot.dslink.serializer;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeManager;
import org.dsa.iot.dslink.node.Writable;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.node.value.ValueUtils;
import org.dsa.iot.dslink.util.StringUtils;
import org.vertx.java.core.json.JsonObject;

import java.util.Map;
import java.util.Set;

/**
 * @author Samuel Grenier
 */
public class Serializer {

    private final NodeManager manager;

    public Serializer(NodeManager manager) {
        this.manager = manager;
    }

    public JsonObject serialize() {
        JsonObject top = new JsonObject();

        Map<String, Node> rootChildren = manager.getChildren("/");
        if (rootChildren != null) {
            for (Node child : rootChildren.values()) {
                if (child.isSerializable()) {
                    JsonObject childOut = new JsonObject();
                    serializeChildren(childOut, child);
                    top.putObject(child.getName(), childOut);
                }
            }
        }
        return top;
    }

    private void serializeChildren(JsonObject out, Node parent) {
        String data = parent.getDisplayName();
        if (data != null) {
            out.putString("$name", data);
        }

        Set<String> set = parent.getInterfaces();
        if (set != null && set.size() > 0) {
            out.putString("$interface", StringUtils.join(set, "|"));
        }

        set = parent.getMixins();
        if (set != null && set.size() > 0) {
            out.putString("$mixin", StringUtils.join(set, "|"));
        }

        String profile = parent.getProfile();
        if (profile != null) {
            out.putString("$is", profile);
        }

        ValueType type = parent.getValueType();
        if (type != null) {
            out.putString("$type", type.toJsonString());
            Value value = parent.getValue();
            if (value != null) {
                ValueUtils.toJson(out, "?value", value);
            }
        }

        char[] password = parent.getPassword();
        if (password != null) {
            out.putString("$$password", new String(password));
        }

        Writable writable = parent.getWritable();
        if (!(writable == null || writable == Writable.NEVER)) {
            out.putString("$writable", writable.toJsonName());
        }

        addValues("$$", out, parent.getRoConfigurations());
        addValues("$", out, parent.getConfigurations());
        addValues("@", out, parent.getAttributes());

        Map<String, Node> children = parent.getChildren();
        if (children != null && children.size() > 0) {
            for (Node child : children.values()) {
                if (child.isSerializable()) {
                    JsonObject childOut = new JsonObject();
                    serializeChildren(childOut, child);
                    out.putObject(child.getName(), childOut);
                }
            }
        }
    }

    private void addValues(String prefix, JsonObject out, Map<String, Value> vals) {
        if (vals == null || vals.size() == 0) {
            return;
        }

        for (Map.Entry<String, Value> entry : vals.entrySet()) {
            String name = prefix + entry.getKey();
            Value value = entry.getValue();
            ValueUtils.toJson(out, name, value);
        }
    }
}
