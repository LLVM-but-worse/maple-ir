package org.mapleir.deob.dataflow.graph;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.util.JavaDesc;

public class JavaDescVertex implements FastGraphVertex {
    private static int descId = 0;

    public final JavaDesc desc;
    private final int id;

    public JavaDescVertex(JavaDesc desc) {
        this.desc = desc;
        id = descId++;
    }

    @Override
    public int getNumericId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return desc.toString();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JavaDescVertex v = (JavaDescVertex) o;
        return v.desc.equals(desc);
    }

    @Override
    public int hashCode() {
        return desc.hashCode();
    }
}
