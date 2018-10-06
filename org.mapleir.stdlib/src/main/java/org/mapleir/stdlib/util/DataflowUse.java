package org.mapleir.stdlib.util;

import java.util.Objects;

public class DataflowUse {
    public final JavaDesc target;
    public final IDataFlowElement flowElement;
    public final DataflowType flowType;

    public DataflowUse(JavaDesc target, IDataFlowElement flowElement, DataflowType flowType) {
        this.target = target;
        this.flowElement = flowElement;
        this.flowType = flowType;
    }

    public enum DataflowType {
        READ,
        WRITE,
        CALL,
        RETURN,
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DataflowUse that = (DataflowUse) o;
        return Objects.equals(target, that.target) &&
                Objects.equals(flowElement, that.flowElement) &&
                flowType == that.flowType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, flowElement, flowType);
    }

    @Override
    public String toString() {
        return target + " " + flowType + " " + flowElement;
    }
}
