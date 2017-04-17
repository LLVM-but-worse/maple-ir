package org.mapleir.stdlib.collections.graph.algorithms;

import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

import java.util.*;

// TODO: Convert to stack-invariant
public class TarjanSCC <N extends FastGraphVertex> {
	
	final FastDirectedGraph<N, ? extends FastGraphEdge<N>> graph;
	final Map<N, Integer> index;
	final Map<N, Integer> low;
	final LinkedList<N> stack;
	final List<List<N>> comps;
	int cur;
	
	public TarjanSCC(FastDirectedGraph<N, ? extends FastGraphEdge<N>> graph) {
		this.graph = graph;
		
		index = new HashMap<>();
		low = new HashMap<>();
		stack = new LinkedList<>();
		comps = new ArrayList<>();
	}
	
	public int low(N n) {
		if(low.containsKey(n)) {
			return low.get(n);
		} else {
			return -1;
		}
	}
	
	public int index(N n) {
		if(index.containsKey(n)) {
			return index.get(n);
		} else {
			return -1;
		}
	}
	
	public List<List<N>> getComponents() {
		return comps;
	}
	
	public void search(N n) {
		index.put(n, cur);
		low.put(n, cur);
		cur++;
		
		stack.push(n);
		
		for(FastGraphEdge<N> e : graph.getEdges(n)) {
			N s = e.dst;
			if(low.containsKey(s)) {
				low.put(n, Math.min(low.get(n), index.get(s)));
			} else {
				search(s);
				low.put(n, Math.min(low.get(n), low.get(s)));
			}
		}
		
		if(low.get(n) == index.get(n)) {
			List<N> c = new ArrayList<>();
			
			N w = null;
			do {
				w = stack.pop();
				c.add(0, w);
			} while (w != n);
			
			comps.add(0, bfs(n, c));
		}
	}
	
	public List<N> bfs(N n, List<N> cand) {
		// TODO: reverse post order
		LinkedList<N> queue = new LinkedList<>();
		queue.add(n);
		
		List<N> bfs = new ArrayList<>();
		while(!queue.isEmpty()) {
			n = queue.pop();
			
			if(bfs.contains(n)) {
				continue;
			} else if(!cand.contains(n)) {
				// System.out.println(n.getId() + " jumps out of component: " + cand);
				continue;
			}
			
			bfs.add(n);
			
			for(FastGraphEdge<N> e : graph.getEdges(n)) {
				N s = e.dst;
				queue.addLast(s);
			}
		}
		
		return bfs;
	}
	
	/* static final Map<Class<?>, Integer> WEIGHTS = new HashMap<>();
	
	{
		WEIGHTS.put(ImmediateEdge.class, 10);
		WEIGHTS.put(ConditionalJumpEdge.class, 9);
		WEIGHTS.put(UnconditionalJumpEdge.class, 8);
		WEIGHTS.put(DefaultSwitchEdge.class, 7);
		WEIGHTS.put(SwitchEdge.class, 6);
		WEIGHTS.put(TryCatchEdge.class, 5);
	}  */

	/* List<FastGraphEdge<N>> weigh(Set<FastGraphEdge<N>> edges) {
		List<FastGraphEdge<N>> list = new ArrayList<>(edges);
		Collections.sort(list, new Comparator<FastGraphEdge<N>>() {
			@Override
			public int compare(FastGraphEdge<N> o1, FastGraphEdge<N> o2) {
				Class<?> c1 = o1.getClass();
				Class<?> c2 = o2.getClass();
				
				if(!WEIGHTS.containsKey(c1)) {
					throw new IllegalStateException(c1.toString());
				} else if(!WEIGHTS.containsKey(c2)) {
					throw new IllegalStateException(c2.toString());
				}
				
				int p1 = WEIGHTS.get(c1);
				int p2 = WEIGHTS.get(c2);
				
				// p2, p1 because higher weights are
				// more favoured.
				return Integer.compare(p2, p1);
			}
		});
		System.out.println("list: " + list);
		return list;
	} */
}