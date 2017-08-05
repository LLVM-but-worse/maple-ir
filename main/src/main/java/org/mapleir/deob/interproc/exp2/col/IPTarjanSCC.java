package org.mapleir.deob.interproc.exp2.col;

import java.awt.Dimension;
import java.util.*;

import org.mapleir.deob.interproc.exp2.BlockCallGraph;
import org.mapleir.deob.interproc.exp2.CallEdge;
import org.mapleir.deob.interproc.exp2.CallGraphBasicBlockBridgeEdge;
import org.mapleir.deob.interproc.exp2.CallGraphBlock;
import org.mapleir.deob.interproc.exp2.ReturnEdge;
import org.mapleir.deob.interproc.exp2.context.CallingContext;
import org.mapleir.flowgraph.edges.FlowEdge;
import org.mapleir.stdlib.util.TabbedStringWriter;

import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;

// TODO: maybe generic interproc graph stuff
public class IPTarjanSCC {
	
	/* to handle edges between cfg nodes, we have a single key. */
	private static final CallingContext INTRAPROCEDURAL_CONTEXT = new CallingContext() {
		@Override
		public String toString() {
			return "{context::INTRAPROC}";
		}
	};
	
	protected final BlockCallGraph graph;
	protected final Map<CallGraphBlock, Integer> index;
	protected final Map<CallGraphBlock, Map<CallingContext, Integer>> low;
	protected final LinkedList<CallGraphBlock> stack;
	public final List<List<CallGraphBlock>> comps;
	protected int cur;
	
	JTree jtree;
	DefaultMutableTreeNode root;
	
	public IPTarjanSCC(BlockCallGraph graph) {
		this.graph = graph;
		
		index = new HashMap<>();
		low = new HashMap<>();
		stack = new LinkedList<>();
		comps = new ArrayList<>();
		
		JFrame frame = new JFrame("view");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setPreferredSize(new Dimension(800, 600));
		frame.setSize(new Dimension(800, 600));
		
		root = new DefaultMutableTreeNode("root");
		jtree = new JTree(root);
		frame.add(jtree);
		
		
//		frame.pack();
//		frame.setLocationRelativeTo(null);
//		frame.setVisible(true);
	}
	
//	public int low(N n) {
//		return low.getOrDefault(n, -1);
//	}
//	
//	public int index(N n) {
//		return index.getOrDefault(n, -1);
//	}
//	
//	public List<List<N>> getComponents() {
//		return comps;
//	}
	
	private Map<CallingContext, Integer> computeCallContextSensitiveLowMap(CallGraphBlock b, Integer cur, TabbedStringWriter sw) {
		Map<CallingContext, Integer> contextSensitiveLowMap = new HashMap<>();
		
		sw.print("\n|-" + graph.getReverseEdges(b));
		/* incoming CallEdges == possible invocation contexts */
		

//		for(FlowEdge<CallGraphBlock> e : graph.getEdges(b)) {
//			if(e.getType() == CallEdge.TYPE_ID) {
//				CallEdge callEdge = (CallEdge) e;
//				sw.print("\n|-adding(3) " + callEdge.getContext());
//				contextSensitiveLowMap.put(callEdge.getContext(), cur);
//			}
//		}
		
		for(FlowEdge<CallGraphBlock> e : graph.getReverseEdges(b)) {
			if(e.getType() == CallEdge.TYPE_ID) {
				CallEdge callEdge = (CallEdge) e;
				sw.print("\n|-adding(1) " + callEdge.getContext());
				contextSensitiveLowMap.put(callEdge.getContext(), cur);
			} else if(e.getType() == ReturnEdge.TYPE_ID) {
				ReturnEdge returnEdge = (ReturnEdge) e;
				sw.print("\n|-adding(2) " + returnEdge.getContext());
				contextSensitiveLowMap.put(returnEdge.getContext(), cur);
			} else {
				contextSensitiveLowMap.put(INTRAPROCEDURAL_CONTEXT, cur);
			}
		}
		
		return contextSensitiveLowMap;
	}
	
	public void search(CallGraphBlock n, TabbedStringWriter sw, DefaultMutableTreeNode parentNode) {
		if(parentNode == null) {
			parentNode = root;
			
			sw.setTabString("|  ");
		}
		
		sw.print("\n\nvis " + n + " @" + System.identityHashCode(n));
		/* need to initialise data, in order to initialise low
		 * data properly, we collect all calling contexts for
		 * this node (basically parallelising this for all
		 * contexts at once) */
		index.put(n, cur);
		Map<CallingContext, Integer> map = computeCallContextSensitiveLowMap(n, cur, sw);
		sw.print("\n|-made lowMap: " + map);
		
		low.put(n, map);
		cur++;
		
		stack.push(n);
		
		for(FlowEdge<CallGraphBlock> e : weigh(graph.getEdges(n))) {
			CallGraphBlock s = e.dst();
			
			DefaultMutableTreeNode sn = new DefaultMutableTreeNode(e);
			parentNode.add(sn);
			
//			sw.print("\nprocess1: " + e);
//			sw.print("\n src: @" + System.identityHashCode(e.src));
//			sw.print("\n dst: @" + System.identityHashCode(e.dst));
			
			CallingContext cxt = null;
			if(e instanceof CallEdge) {
				cxt = ((CallEdge) e).getContext();
			} else if(e instanceof ReturnEdge) {
				cxt = ((ReturnEdge) e).getContext();
			} else if(e instanceof CallGraphBasicBlockBridgeEdge) {
				cxt = INTRAPROCEDURAL_CONTEXT;
			} else {
				throw new RuntimeException(e.getClass().toString());
			}
			
//			sw.print("\n cxt: " + cxt);
			
			if(low.containsKey(s)) {
				if(index.get(s) < index.get(n) && stack.contains(s)) {
					low.get(n).put(cxt, Math.min(low.get(n).get(cxt), index.get(s)));
				}
//				sw.print("\n");
			} else {
				sw.tab();
				search(s, sw, sn);
				sw.untab();

				sw.print("\nprocess2: " + e);
				Map<CallingContext, Integer> nlm = low.get(n);
				sw.print("\n|-nlm: " + nlm);
				sw.print("\n|-with " + cxt);
//				sw.print("\nm2: " + map);
				
				int nl = nlm.get(cxt);
				int rsl = low.get(s).get(cxt);
				low.get(n).put(cxt, Math.min(nl, rsl));
			}
		}
		
		sw.print("\n|________");
		
		if(Objects.equals(low.get(n), index.get(n))) {
			List<CallGraphBlock> c = new ArrayList<>();
			
			CallGraphBlock w = null;
			do {
				w = stack.pop();
				c.add(w);
			} while (w != n);
			
			comps.add(c);
//			ExtendedDfs<N> dfs = new ExtendedDfs<>(graph, ExtendedDfs.POST).setMask(c).run(n);
//			Collections.reverse(dfs.getPostOrder());
//			comps.add(0, dfs.getPostOrder());
		}
	}

	private Iterable<FlowEdge<CallGraphBlock>> weigh(Set<FlowEdge<CallGraphBlock>> edges) {
		List<FlowEdge<CallGraphBlock>> lst = new ArrayList<>(edges);
		Collections.sort(lst, new Comparator<FlowEdge<CallGraphBlock>>() {
			@Override
			public int compare(FlowEdge<CallGraphBlock> o1, FlowEdge<CallGraphBlock> o2) {
				boolean n1 = (o1 instanceof ReturnEdge) || (o1 instanceof CallEdge);
				boolean n2 = (o2 instanceof ReturnEdge) || (o2 instanceof CallEdge);
				
				if(n1 && !n2) {
					return 1;
				} else if(!n1 && n2) {
					return -1;
				} else {
					return 0;
				}
			}
		});
		return lst;
	}
}
