package org.mapleir.deob.dataflow.graph;

import org.mapleir.stdlib.collections.graph.FastGraphEdgeImpl;
import org.mapleir.stdlib.util.JavaDesc;

public class JavaDescEdge extends FastGraphEdgeImpl<JavaDescVertex> {
    public JavaDesc via; // function the flow happens in
    public JavaDescEdge(JavaDescVertex src, JavaDescVertex dst, JavaDesc via) {
        super(src, dst);
        this.via = via;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JavaDescEdge))
            return false;
        JavaDescEdge dfe = (JavaDescEdge) o;
        if (super.equals(o) && via.equals(dfe.via)) return true;
        return src.equals(dfe.src) && dst.equals(dfe.dst) && via.equals(dfe.via);
    }
}
