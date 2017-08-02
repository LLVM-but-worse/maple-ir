package org.mapleir.deob.interproc.cxtsenscg;

import org.mapleir.deob.interproc.geompa.util.ChunkedQueue;
import org.mapleir.deob.interproc.geompa.util.QueueReader;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.objectweb.asm.tree.MethodNode;

public class ContextInsensitiveCallGraph extends FastDirectedGraph<MethodNode, FastGraphEdge<MethodNode>>{

    private final ChunkedQueue<MethodNode> stream;
    private final QueueReader<MethodNode> listener;
    
    public ContextInsensitiveCallGraph() {
    	stream = new ChunkedQueue<>();
    	listener = stream.reader();
	}
    
	@Override
	public boolean addVertex(MethodNode v) {
		if(super.addVertex(v)) {
			stream.add(v);
			return true;
		} else {
			return false;
		}
	}
    
    public QueueReader<MethodNode> getNodeListener() {
    	return listener;
    }
}