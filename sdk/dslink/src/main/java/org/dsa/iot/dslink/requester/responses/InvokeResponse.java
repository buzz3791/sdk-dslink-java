package org.dsa.iot.dslink.requester.responses;

import lombok.Getter;

import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueUtils;
import org.dsa.iot.dslink.requester.requests.InvokeRequest;
import org.dsa.iot.dslink.util.Table;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.LinkedList;

/**
 * @author Samuel Grenier
 */
@Getter
public class InvokeResponse extends Response<InvokeRequest> {

    private final JsonArray columns;
    private Integer from;
    private Table table;

    public InvokeResponse(InvokeRequest request, JsonArray columns) {
        super(request);
        this.columns = columns;
    }

    public void updateMeta(JsonObject meta) {
        this.from = meta.getInteger("from");
    }

    @Override
    public void populate(JsonArray array) {
        if (table == null) {
            table = new Table();
            mutateTable(array, null);
        } else {
            if (from == null) {
                mutateTable(array, null);
            } else {
                mutateTable(array, from);
            }
        }
    }

    private void mutateTable(JsonArray array, Integer start) {
        for (Object arrayObj : array) {
            LinkedList<Value> row = createRow((JsonArray) arrayObj);
            if (start == null) {
                table.addRow(row);
            } else {
                table.updateRow(start++, row);
            }
        }
    }

    private LinkedList<Value> createRow(JsonArray array) {
        if (columns.size() != array.size()) {
            String err = "columns and row size do not match";
            err += "{ columns: "  + columns.encode() + ", ";
            err += "rows: " + array.encode() + " }";
            throw new RuntimeException(err);
        }

        LinkedList<Value> row = new LinkedList<>();
        for (int i = 0; i < columns.size(); i++) {
            row.add(ValueUtils.toValue(array.get(i)));
        }
        return row;
    }
}
