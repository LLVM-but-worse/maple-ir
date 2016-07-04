package org.rsdeob.stdlib.ir.transform;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;

import java.util.Iterator;
import java.util.Set;

public abstract class BackwardsFlowAnalyser<N extends FastGraphVertex, E extends FlowEdge<N>, S> extends DataAnalyser<N, E, S> {

	public boolean x, y;
	
	public BackwardsFlowAnalyser(FlowGraph<N, E> graph) {
		super(graph);
	}
	
	public BackwardsFlowAnalyser(FlowGraph<N, E> graph, boolean commit) {
		super(graph, commit);
	}
	
	@Override
	protected void init() {
		// since this is backwards analysis, we
		// set the initial flow states after the
		// exit points of the graph.
		
		// to increase efficiency, instead of
		// calling super.init(), we compute the
		// exits of the graph while inserting
		// the default flow states into our tables.
		
		for(N n : graph.vertices()) {
			appendQueue(n);
			in.put(n, newState());
			out.put(n, newState());
			
			if(graph.getEdges(n).size() == 0) {
				out.put(n, newEntryState());
			}
		}
	}
	
	public void queueNext(N _n) {
//		Set<E> edgeSet = graph.getEdges(n);
//		if (edgeSet != null) {
//			for (E e : edgeSet) {
//				appendQueue(e.dst);
//			}
//		}
		
		for(N n : graph.vertices()) {
			if(graph.getEdges(n).size() == 0) {
				appendQueue(n);
				in.put(n, newState());
				out.put(n, newEntryState());
			}
		}
	}
	
	
	
	@Override
	public void appendQueue(N n) {
		if(!queue.contains(n)) {
			if(x) {
				System.out.println("    Appending " + n + ", I was called from ");
				StackTraceElement[] trace = (new Throwable()).getStackTrace();
				for (int i = 1; i <= 10; i++) {
					String classname = trace[i].getClassName();
					System.out.println("        " + classname.substring(classname.lastIndexOf('.') + 1) + "#" + trace[i].getMethodName());
					if (classname.contains("CodeAnalytics"))
						break;
				}
			}
			queue.add(n);
		}
	}
	@Override
	public void removed(N n) {
		super.removed(n);
		queueNext(n);
	}

	@Override
	public void update(N n) {
		super.update(n);
		replaced(n, n);
	}
	
	@Override
	public void replaced(N old, N n) {
		super.replaced(old, n);
		if((graph.containsVertex(old) && graph.getEdges(old).size() == 0) || graph.getEdges(n).size() == 0) {
			in.put(n, newState());
			out.put(n, newEntryState());
		} else {
			in.put(n, newState());
			out.put(n, newState());
		}
		queueNext(n);
	}

	@Override
	public void insert(N p, N s, N n) {
		update(n);
		queueNext(p);
		queueNext(s);
	}
	
	@Override
	public void processImpl() {
		while(!queue.isEmpty()) {
			N n = queue.iterator().next();
			queue.remove(n);
			
			if(!graph.containsVertex(n)) {
				continue;
			}
			
//			System.out.println("  bexe " + n);

			// stored for checking if a change of state
			// happens during the analysis of this
			// instruction. (in because it's backwards).
			S oldIn = newState();
			S currentIn = in.get(n);
			copy(currentIn, oldIn);
			
			S currentOut = out.get(n);
			Set<E> succs = graph.getEdges(n);
			
//			System.out.println("   succ:");
//			for(E e : succs) {
//				System.out.println("    " + e.dst);
//			}
			
			if(succs.size() == 1) {
				N succ = succs.iterator().next().dst;
				S succIn = in.get(succ);
				copy(succIn, currentOut);
				if (y) System.out.println("copy " + n);
			} else if(succs.size() > 1) {
				Iterator<E> it = succs.iterator();

				N firstSucc = it.next().dst;
				copy(in.get(firstSucc), currentOut);

				if (y) System.out.println("merge " + n);
				while(it.hasNext()) {
					S merging = in.get(it.next().dst);
					merge(currentOut, merging);
				}
			}
			
			execute(n, currentOut, currentIn);
			
//			System.out.println("     curIn: " + currentIn);
//			System.out.println("     oldIn: " + oldIn);
//			System.out.println("     equals: " + equals(currentIn, oldIn));
			
			// if there was a change, enqueue the predecessors.
			if(!equals(currentIn, oldIn)) {
				
				if(y) {
					System.out.println("not eq: " + n);
					System.out.println(oldIn);
					System.out.println(currentIn);
				}
				
				for(E e : graph.getReverseEdges(n)) {
					if (y) System.out.println("    requeue: " + e.src);
					appendQueue(e.src);
				}
			}
		}
	}

	@Override
	protected abstract S newState();

	@Override
	protected abstract S newEntryState();

	@Override
	protected abstract void merge(S in1, S in2, S out);
	
	@Override
	protected abstract void copy(S src, S dst);
	
	@Override
	protected abstract boolean equals(S s1, S s2);
	
	@Override
	protected abstract void execute(N n, S out, S in);
}