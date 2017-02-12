package org.mapleir.ir.analysis;

import java.util.*;

import org.mapleir.ir.analysis.split.StrongComponent;
import org.mapleir.ir.analysis.split.UDEdge;
import org.mapleir.ir.analysis.split.UDNode;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.FlowEdge;

public class FlowGraphUtils {

	private static UDNode makeNode(BasicBlock b, Map<BasicBlock, UDNode> blockNodes) {
		if (blockNodes.containsKey(b)) {
			return blockNodes.get(b);
		} else {
			UDNode n = new UDNode(b, b.getId());
			blockNodes.put(b, n);
			return n;
		}
	}

	public static UDNode computeUndigraph(ControlFlowGraph cfg, Set<UDEdge> terminalEdges) {
		Map<BasicBlock, UDNode> blockNodes = new HashMap<>();

		for (BasicBlock b : cfg.vertices()) {
			makeNode(b, blockNodes);
		}

		BasicBlock first = cfg.getEntries().iterator().next();
		UDNode start = new UDNode(null, "start");
		UDNode firstNode = blockNodes.get(first);
		start.addEdge(firstNode);

		UDNode end = new UDNode(null, "end");
		end.addEdge(start);

		for (Map.Entry<BasicBlock, UDNode> entry : blockNodes.entrySet()) {
			BasicBlock block = entry.getKey();
			UDNode n = entry.getValue();
			for (FlowEdge<BasicBlock> e : cfg.getEdges(block)) {
				UDNode other = blockNodes.get(e.dst);
				n.addEdge(other);
			}
			if (cfg.getEdges(block).isEmpty()) {
				// leaf
				UDEdge terminal = n.addEdge(end);
				if (terminalEdges != null) {
					terminalEdges.add(terminal);
				}
			}
		}

		return start;
	}

	public static class IdSource {
		int n;

		public IdSource() {
			n = 0;
		}

		public int getNew() {
			return n++;
		}
	}

	private static void computeCycleEquivalence(List<UDNode> nodes) {
		IdSource idSource = new IdSource();
		int i = nodes.size() - 1;
		while (i >= 0) {
			nodes.get(i).computeCycleEquivalence(idSource);
			--i;
		}
	}

	private static void clearEdgeSeens(Collection<UDNode> nodes) {
		for (UDNode node : nodes) {
			for (UDEdge edge : node.allEdges) {
				edge.seen = false;
			}
		}
	}

	private static void computeSESERegions(List<UDNode> nodes) {
		clearEdgeSeens(nodes);
		nodes.get(0).computeSESE();
	}

	public static List<UDNode> compute(UDNode start) {
		List<UDNode> nodes = new ArrayList<>();
		start.computeSpanningTree(nodes);
		computeCycleEquivalence(nodes);
		computeSESERegions(nodes);
		return nodes;
	}
	
	public static class TCNode implements Comparable<TCNode> {
		public final BasicBlock block;
		final Set<TCNode> succs;
		TreeSet<TCNode> splitPointSuccessors;
		
		TCNode strongRoot;
		StrongComponent strongComponent;
		int dfsIndex;
		
		public TCNode(BasicBlock block) {
			this.block = block;
			succs = new HashSet<>();
			dfsIndex = -1;
		}
		
	    private void computeSplitPointSuccessors(TreeSet<TCNode> sps, TreeSet<TCNode> seen) {
	        if (seen.contains(this))
	            return;
	        seen.add(this);
	        if (strongComponent.splitPoint == this) {
	            sps.add(this);
	            sps = new TreeSet<>();
	            splitPointSuccessors = sps;
	        }
	        for (TCNode n : succs) {
	            n.computeSplitPointSuccessors(sps, seen);
	        }
	    }

	    public void computeSplitPointSuccessors() {
	        splitPointSuccessors = new TreeSet<>();
	        computeSplitPointSuccessors(splitPointSuccessors, new TreeSet<>());
	    }
		
		public int computeTransitiveClosures(
				int dfsIndex, 
				Stack<TCNode> nstack, 
				Stack<StrongComponent> cstack,
				TreeSet<StrongComponent> components) {
			
			strongRoot = this;
			strongComponent = null;
			this.dfsIndex = dfsIndex++;
			
			nstack.push(this);
			int hsaved = cstack.size();
			
			for (TCNode w : succs) {
				// no self-loops
				if (w == this) {
					continue;
				}
				if (w.dfsIndex == -1) {
					dfsIndex = w.computeTransitiveClosures(dfsIndex, nstack, cstack, components);
				}
				if (w.strongComponent == null) {
					if (w.strongRoot.dfsIndex < this.dfsIndex) {
						strongRoot = w.strongRoot;
					}
				} else if (!nstack.contains(w)) {
					cstack.push(w.strongComponent);
				}
			}
			if (strongRoot == this) {
				StrongComponent c = new StrongComponent(this);
				components.add(c);
				while (cstack.size() != hsaved) {
					StrongComponent x = cstack.pop();
					if (!c.transitiveClosure.contains(x)) {
						c.transitiveClosure.addAll(x.transitiveClosure);
					}
				}
				TCNode w;
				do {
					w = nstack.pop();
					w.strongComponent = c;
					c.members.add(w);
				} while (w != this);
			}
			
			return dfsIndex;
		}

		@Override
		public int compareTo(TCNode o) {
			return block.compareTo(o.block);
		}
		
		@Override
		public String toString() {
			return block.toString();
		}
	}

	public static SortedSet<StrongComponent> computeTransitiveClosures(ControlFlowGraph cfg, Map<BasicBlock, TCNode> map) {		
		for(BasicBlock b : cfg.vertices()) {
			TCNode n = new TCNode(b);
			map.put(b, n);
		}
		
		for(TCNode n : map.values()) {
			for(FlowEdge<BasicBlock> e : cfg.getEdges(n.block)) {
				n.succs.add(map.get(e.dst));
			}
		}

        Stack<TCNode> nstack = new Stack<>();
        Stack<StrongComponent> cstack = new Stack<>();
        TreeSet<StrongComponent> components = new TreeSet<>();
        
		int dfsIndex = 0;
		for(TCNode n : map.values()) {
			if(n.dfsIndex == -1) {
				dfsIndex = n.computeTransitiveClosures(dfsIndex, nstack, cstack, components);
			}
		}
		
		return components;
	}
	
	public static Collection<TCNode> computeSplitPoints(Map<BasicBlock, TCNode> map, Set<BasicBlock> handlers, Collection<UDEdge> terminalEdges) {
        LinkedList<TCNode> splitBlocks = new LinkedList<>();
        
        for (UDEdge terminal : terminalEdges) {
            for (UDEdge e : terminal.equivClass.edges) {
                UDNode block1 = e.src;
                UDNode block2 = e.dst;
                UDNode entry = null;
                if (block1.block == null) {
                    entry = block2;
                } else if (block2.block == null) {
                    entry = block1;
                } else {
                    int c = block1.block.compareTo(block2.block);
                    if (c < 0) {
                        entry = block2;
                    } else {
                        assert c > 0;
                        entry = block1;
                    }
                }
                if ((entry != null)
                    && !handlers.contains(entry)
                    /* && entry.hasFullyDefinedFrame() */) {
                	TCNode tcn = map.get(entry.block);
                    tcn.strongComponent.splitPoint = tcn;
                    splitBlocks.add(tcn);
                }
            }
        } 
        return splitBlocks;
	}
}