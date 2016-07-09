package org.rsdeob.stdlib.ir.transform;

import java.util.Iterator;
import java.util.Set;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.edge.TryCatchEdge;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;
import org.rsdeob.stdlib.collections.graph.flow.ExceptionRange;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;

public abstract class ForwardsFlowAnalyser<N extends FastGraphVertex, E extends FlowEdge<N>, S> extends DataAnalyser<N, E, S>{
	public boolean x;

	public ForwardsFlowAnalyser(FlowGraph<N, E> graph, boolean commit) {
		super(graph, commit);
	}
	
	public ForwardsFlowAnalyser(FlowGraph<N, E> graph) {
		super(graph);
	}
	
	protected void reset(N n) {
		if(graph.getEntries().contains(n)) {
			in.put(n, newEntryState());
			out.put(n, newState());
		} else {
			in.put(n, newState());
			out.put(n, newState());
		}
	}

	protected boolean queue(N n, boolean reset) {
		for(N u : graph.wanderAllTrails(graph.getEntries().iterator().next(), n)) {
			appendQueue(u);
			if(reset) reset(u);
		}
		return true;
	}
	
	@Override
	protected void init() {
		super.init();
		
		for(N entry : graph.getEntries()) {
			in.put(entry, newEntryState());
			out.put(entry, newState());
			appendQueue(entry);
		}
		
		for(ExceptionRange<N> er : graph.getRanges()) {
			N h = er.getHandler();
			in.put(h, newEntryState());
			out.put(h, newState());
			appendQueue(h);
		}
	}
	
	// for pre and post remove, we need to
	// assume for the time being that the
	// definition is dead :/s
	@Override
	public void preRemove(N n) {
		// System.out.println();
		// System.out.println();
		// if it didn't queue anything, queue
		// the successors
		if(!queue(n, true)) {
			for(E e : graph.getEdges(n)) {
//				if(!(e instanceof TryCatchEdge)) {
					appendQueue(e.dst);
//				}
			}
		}
		
		// System.out.println("Removed " + n);
		// System.out.println("   removeupdate " + queue);
		
		// System.out.println();
		// System.out.println();
	}

	@Override
	public void postRemove(N n) {
		remove(n);
	}

	@Override
	public void update(N n) {
		// System.out.println();
		// System.out.println();
		super.update(n);
		
		queue(n, false);
		
		if(graph.getEntries().contains(n)) {
			in.put(n, newEntryState());
			out.put(n, newState());
		} else {
			in.put(n, newState());
			out.put(n, newState());
		}
		
		// System.out.println("Updated " + n);
		// System.out.println("   And updated " + queue);
		// System.out.println();
		// System.out.println();
	}

	@Override
	public void replaced(N old, N n) {
		if("".equals(""))  {
			throw new RuntimeException();
		}
		super.replaced(old, n);
		if(graph.getEntries().contains(n)) {
			in.put(n, newEntryState());
			out.put(n, newState());
		} else {
			in.put(n, newState());
			out.put(n, newState());
		}
		queue(n, false);
	}

	@Override
	public void insert(N p, N s, N n) {
		if("".equals(""))  {
			throw new RuntimeException();
		}
		update(n);
		queue(p, false);
		queue(s, false);
	}
	
	@Override
	public void processImpl() {
		while(!queue.isEmpty()) {
			N n = queue.iterator().next();
			queue.remove(n);
			
			if(!graph.containsVertex(n)) {
				continue;
			}
						
			S oldOut = newState();
			S currentOut = out.get(n);
			copy(currentOut, oldOut);
			
			S currentIn = in.get(n);
			Set<E> preds = graph.getReverseEdges(n);
			
			if(preds.size() == 1) {
				E edge = preds.iterator().next();
				if(x) System.out.println("src: " + edge.src + ",  out=" + out.get(edge.src));
				// FIXME: in the future define the
				// exception in the state rather than
				// letting DFA discover the catch() statement.
				if(edge instanceof TryCatchEdge) {
					copyException(out.get(edge.src), currentIn);
				} else {
					copy(out.get(edge.src), currentIn);
				}
				
				
			} else if(preds.size() > 1) {
				Iterator<E> it = preds.iterator();
				
				boolean exc = false;
				for(E p : preds) {
					if(p instanceof TryCatchEdge) {
						exc = true;
						break;
					}
				}
				
				E edge = it.next();
				if(edge instanceof TryCatchEdge) {
					copyException(out.get(edge.src), currentIn);
				} else {
					if(exc) {
						throw new IllegalStateException("Natural flow into exception block?");
					}
					copy(out.get(edge.src), currentIn);
				}
				
				while(it.hasNext()) {
					edge = it.next();
					if(edge instanceof TryCatchEdge) {
						S newS = newState();
						copyException(out.get(edge.src), newS);
						copy(newS, currentIn);
					} else {
						if(exc) {
							throw new IllegalStateException("Natural flow into exception block?");
						}
						S merging = out.get(edge.src);
						merge(currentIn, merging);
					}
				}
			}
			
			execute(n, currentIn, currentOut);
			
			// if there was a change, enqueue the successors.
			if (!equals(currentOut, oldOut)) {
				for (E e : graph.getEdges(n)) {
					appendQueue(e.dst);
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
	protected abstract void copyException(S src, S dst);
	
	@Override
	protected abstract boolean equals(S s1, S s2);
	
	@Override
	protected abstract void execute(N n, S in, S out);
}