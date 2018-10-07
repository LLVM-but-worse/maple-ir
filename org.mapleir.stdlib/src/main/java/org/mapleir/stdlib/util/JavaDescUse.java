package org.mapleir.stdlib.util;

import java.util.Objects;

public class JavaDescUse {
    public final JavaDesc target;
    public final IJavaDescUse flowElement;
    public final UseType flowType;

    public JavaDescUse(JavaDesc target, IJavaDescUse flowElement, UseType flowType) {
        this.target = target;
        this.flowElement = flowElement;
        this.flowType = flowType;
    }

    public enum UseType {
        READ,
        WRITE,
        CALL,
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        JavaDescUse that = (JavaDescUse) o;
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
