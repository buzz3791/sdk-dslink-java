package org.dsa.iot.dslink.methods.requests;

import org.dsa.iot.dslink.methods.Request;
import org.vertx.java.core.json.JsonObject;

/**
 * Request used to remove attributes or configurations.
 *
 * @author Samuel Grenier
 */
public class RemoveRequest implements Request {

    private final String path;

    public RemoveRequest(String path) {
        if (path == null) {
            throw new NullPointerException("path");
        }
        this.path = path;
    }

    @Override
    public String getName() {
        return "remove";
    }

    public String getPath() {
        return path;
    }

    @Override
    public void addJsonValues(JsonObject out) {
        out.putString("path", path);
    }
}
