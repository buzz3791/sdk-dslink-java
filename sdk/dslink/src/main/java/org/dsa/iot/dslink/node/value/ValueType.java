package org.dsa.iot.dslink.node.value;

/**
 * @author Samuel Grenier
 */
public enum ValueType {

    /* Json values */
    NUMBER,
    STRING,
    BOOL,
    
    /* Internal values */
    MAP,
    ARRAY;
    
    public String toJsonString() {
        return this.toString().toLowerCase();
    }

    public static ValueType toEnum(String type) {
        type = type.toLowerCase();
        switch (type) {
            case "number":
                return NUMBER;
            case "string":
                return STRING;
            case "bool":
                return BOOL;
            case "map":
                return MAP;
            case "array":
                return ARRAY;
            default:
                throw new RuntimeException("Unsupported type: " + type);
        }
    }
}
