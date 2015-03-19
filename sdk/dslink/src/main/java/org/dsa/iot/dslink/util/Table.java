package org.dsa.iot.dslink.util;

import org.dsa.iot.dslink.node.value.Value;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Samuel Grenier
 */
public class Table {

    private final List<List<Value>> rows = new LinkedList<>();

    public void updateRow(int i, LinkedList<Value> row) {
        rows.set(i, row);
    }

    public void addRow(LinkedList<Value> row) {
        rows.add(row);
    }

    public List<List<Value>> getRows() {
        return rows;
    }
}
