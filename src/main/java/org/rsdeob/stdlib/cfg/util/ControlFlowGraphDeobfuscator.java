package org.rsdeob.stdlib.cfg.util;

import static org.objectweb.asm.Opcodes.GOTO;

import java.util.*;
import java.util.Map.Entry;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.edge.ImmediateEdge;
import org.rsdeob.stdlib.cfg.edge.TryCatchEdge;
import org.rsdeob.stdlib.cfg.edge.UnconditionalJumpEdge;
import org.rsdeob.stdlib.collections.graph.flow.ExceptionRange;

public class ControlFlowGraphDeobfuscator {
	
	public int prunedGotos;
	public int removedEmpty;
	public int inlinedComponents;
	
	public List<BasicBlock> deobfuscate(ControlFlowGraph cfg) {
		SCCFinder finder = new SCCFinder();
		finder.scc(cfg);
		
		Map<SCC, List<SCC>> graph = createSuperNodeMap(cfg, finder);
		List<BasicBlock> newOrder = biblSort(cfg, finder, graph);
		GraphUtils.naturaliseGraph(cfg, newOrder);

		newOrder = pruneGotos(cfg, newOrder);
		
		return newOrder;
	}
	
	public void removeEmptyBlocks(ControlFlowGraph cfg, List<BasicBlock> blocks) {
		Map<LabelNode, LabelNode> labelTracking = new HashMap<>();
		for(BasicBlock b : blocks) {
			labelTracking.put(b.getLabel(), b.getLabel());
		}

		while(true) {
			boolean change = false;
			
			for(BasicBlock b : new ArrayList<>(blocks)) {
				if(b.getPredecessors().size() == 0 && !cfg.getEntries().contains(b)) {
					cfg.removeVertex(b);
					blocks.remove(b);
					change = true;
				} else if(b.cleanSize() == 0) {
					// TODO:
					// implies 1 immediate successor
					// transfer predecessor edges to its successor
				} else {
					FlowEdge<BasicBlock> incomingImmediate = b.getIncomingImmediateEdge();
					if(incomingImmediate != null && b.getPredecessors().size() == 1) {
						BasicBlock pred = incomingImmediate.src;
	
						if(!GraphUtils.isFlowBlock(pred)) {
							// check that the exceptions are the same
							List<ExceptionRange<BasicBlock>> predRanges = pred.getProtectingRanges();
							List<ExceptionRange<BasicBlock>> blockRanges = b.getProtectingRanges();
							if(predRanges.equals(blockRanges)) {
								// transfer instructions
								for(AbstractInsnNode ain : b.getInsns()) {
									pred.addInsn(ain);
								}
								
								// meta/debug
								b.cleanSize();
								b.addInsn(new LdcInsnNode("[deobber] destructed and merged " + b.getId() + " into " + pred.getId()));
								
								// transfer successor edges from b so
								// that they go from pred to succ
								for(FlowEdge<BasicBlock> e : b.getSuccessors(e -> !(e instanceof TryCatchEdge))) {
									BasicBlock target = e.dst;
									FlowEdge<BasicBlock> cloned = e.clone(pred, target);
									cfg.addEdge(pred, cloned);
								}
								
								// update the predecessors of b to point to predecessor
								//   only 1 predecessor of this block so we don't need to update
								//   the other predecessors
								
								for(ExceptionRange<BasicBlock> er : predRanges) {
									er.removeVertex(b);
								}
								
								// update the label
								// System.out.printf("%s <- %s, %d <- %d.%n", pred.getId(), b.getId(), labelTracking.get(pred.getLabel()).hashCode(), b.getLabel().hashCode());
								labelTracking.put(b.getLabel(), labelTracking.get(pred.getLabel()));
								
								cfg.removeVertex(b);
								blocks.remove(b);
								removedEmpty++;
								change = true;
								break;
							}
						}
					}
				}
			}
			
			if(!change) {
				break;
			}
		}
		
		for(BasicBlock b : blocks) {
			b.updateLabelRef(labelTracking);
		}
	}
	
	public List<BasicBlock> biblSort(ControlFlowGraph cfg, SCCFinder finder, Map<SCC, List<SCC>> componentGraph) {
		List<SCC> sccOrder = topological(componentGraph);
		List<BasicBlock> order = new ArrayList<>();
		for(SCC scc : sccOrder) {
			order.addAll(scc.getBlocks());
		}
		Map<BasicBlock, SCC> sccMap = new HashMap<>();
		for(SCC scc : finder.components) {
			for(BasicBlock b : scc.getBlocks()) {
				sccMap.put(b, scc);
			}
		}

		while(true) {
			boolean change = false;

			orderFor: for(BasicBlock b : order) {
				int index = order.indexOf(b);
				for(FlowEdge<BasicBlock> e : b.getSuccessors()) {
					if(e instanceof UnconditionalJumpEdge) {
						BasicBlock dst = e.dst;
						if(dst.getPredecessors().size() == 1) {
							SCC scc = sccMap.get(dst);
							SCC otherSCC = sccMap.get(b);
							if(scc.getFirst() == dst && otherSCC != scc) {
								// ie. jumps to the start of a component
								//  inline it
								
								int dstIndex = order.indexOf(dst);
								// the dst isn't next to it
								if(dstIndex != (index + 1)) {
									List<ExceptionRange<BasicBlock>> targRanges = dst.getProtectingRanges();
									List<ExceptionRange<BasicBlock>> blockRanges = b.getProtectingRanges();
									if(!targRanges.equals(blockRanges)) {
										continue;
									}
									
									// inline the component
									order.removeAll(scc.getBlocks());
									order.addAll(index + 1, scc.getBlocks());
									inlinedComponents++;
									
									// remove the unconditional jump and
									// swap the unconditional edge to an immediate
									// we could let the goto fixer do this instead
									//    actually we will
									/* b.removeInsn(b.last());
									ImmediateEdge im = new ImmediateEdge(b, dst);
									cfg.removeEdge(b, e);
									cfg.addEdge(b, im); */
									
									change = true;
									break orderFor;
								}
							}
						}
					}
				}
			}

			if(!change) {
				break;
			}
		}

		return order;
	}
	
	public List<BasicBlock> pruneGotos(ControlFlowGraph cfg, List<BasicBlock> blocks) {
		List<BasicBlock> newOrder = new ArrayList<>(blocks);
		
		while(true){
			boolean change = false;
			
			ListIterator<BasicBlock> lit = newOrder.listIterator();
			while(lit.hasNext()) {
				BasicBlock b = lit.next();
				
				AbstractInsnNode last = b.realLast();
				if(last != null && last.opcode() == GOTO) {
					int index = blocks.indexOf(b);

					Set<FlowEdge<BasicBlock>> jumps = b.getSuccessors(s -> s instanceof UnconditionalJumpEdge);
					if(jumps.size() > 1 || jumps.size() <= 0) {
						throw new IllegalStateException("goto with misplaced branches, " + b.getId() + jumps);
					}
					
					FlowEdge<BasicBlock> im = jumps.iterator().next();
					BasicBlock target = im.dst;
					int targetIndex = blocks.indexOf(target);
					// System.out.printf("%s, t=%d, b=%d.%n", b.getId(), targetIndex, index);
					System.out.println("blocks: " + blocks);
					System.out.println("index of " + target + " , " + targetIndex);
					System.out.println("index of current: " + index + ", " + b);
					if((index + 1) == targetIndex) {
						change = true;
						
						b.removeInsn(last);
						// goto implies 1 real successor
						//  the real one being the jump
						//  and possibly some exception edges as well
						
						// if the block now has no real instructions (meta):
						//   remove it from the graph
						//   transfer predecessor edges to the target
						//   remove it from try ranges
						
						cfg.removeEdge(b, im);
						im = new ImmediateEdge<>(b, target);
						cfg.addEdge(b, im);
//						System.out.println("pruning " + b.getId());

						if(!b.isHandler() && b.cleanSize() == 0) {
							// if we have anything other than an immediate
							// or handler edges, something seriously fucked
							// up is going on...
							for(FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
								if(e instanceof ImmediateEdge) {
									if(e != im) {
										throw new IllegalStateException("two immediates for " + b.getId() + ", im:" + im + ", e:" + e);
									}
								} else if(!(e instanceof TryCatchEdge)) {
									throw new UnsupportedOperationException("edge for " + b.getId() + "???: " + cfg.getEdges(b));
								}
							}
							
							// now the block has no important instructions
							// and only 1 immediate successor, so we can
							// transfer edges from the predecessors to
							// the successor, 
							//   we don't need to recreate handler edges
							//   because it can't throw an exception (assumption)
							
							for(FlowEdge<BasicBlock> pe : cfg.getReverseEdges(b)) {
								BasicBlock src = pe.src;
								FlowEdge<BasicBlock> ce = pe.clone(src, target);
								cfg.addEdge(src, ce);
							}
							cfg.removeVertex(b);
							
							lit.remove();
							
							prunedGotos++;
						}
					}
				}
			}
			
			if(!change) {
				break;
			}
		}
		
		return newOrder;
	}
	
	public List<BasicBlock> dfs(ControlFlowGraph cfg) {
		List<BasicBlock> visited = new ArrayList<>();
		Stack<BasicBlock> stack = new Stack<>();
		
		for(BasicBlock entry : cfg.getEntries()) {
			stack.push(entry);
			visited.add(entry);
		}
		
		while(!stack.isEmpty()) {
			BasicBlock b = stack.pop();
			
			for(FlowEdge<BasicBlock> succE : b.getSuccessors()) {
				BasicBlock succ = succE.dst;
				if(!visited.contains(succ)) {
					stack.push(succ);
					visited.add(succ);
				}
			}
		}
		
		return visited;
	}
	
	public List<SCC> topological(Map<SCC, List<SCC>> graph) {
		Map<SCC, Integer> count = new HashMap<>();
		for(SCC node : graph.keySet()) {
			count.put(node, 0);
		}
		for(SCC node : graph.keySet()) {
			for(SCC succ : graph.get(node)) {
				count.put(succ, count.get(succ).intValue() + 1);
			}
		}
		Stack<SCC> ready = new Stack<>();
		for(Entry<SCC, Integer> e : count.entrySet()) {
			if(e.getValue().intValue() == 0) {
				ready.push(e.getKey());
			}
		}
		List<SCC> result = new ArrayList<>();
		while(!ready.isEmpty()) {
			SCC node = ready.pop();
			result.add(node);
			for(SCC succ : graph.get(node)) {
				count.put(succ, count.get(succ).intValue() - 1);
				if(count.get(succ).intValue() == 0) {
					ready.add(succ);
				}
			}
		}
		
		return result;
	}

	public Map<SCC, List<SCC>> createSuperNodeMap(ControlFlowGraph cfg, SCCFinder finder) {
		Map<SuperNode, SCC> nodeComponents = new HashMap<>();
		for (SCC scc : finder.components) {
			for (SuperNode node : scc) {
				nodeComponents.put(node, scc);
			}
		}

		Map<SCC, List<SCC>> componentGraph = new HashMap<>();
		for (SCC scc : finder.components) {
			componentGraph.put(scc, new ArrayList<SCC>());
		}
		
		/* for(SuperNode _sn : finder.list) {
			SCC comp = nodeComponents.get(_sn);
			if(comp != null) {
				for(SuperNode sn : comp) {
					for(SuperNode succ : sn.successors) {
						SCC succComp = nodeComponents.get(succ);
						if(comp != succComp) {
							componentGraph.get(comp).add(succComp);
						}
					}
				}
			}
		} */

//		for (BasicBlock _node : cfg.blocks()) {
//
//		}

		for (SuperNode _sv : finder.list) {
			// get the SuperNode for the block
//			if (_sv.vertices.contains(_node)) {
				
				// get the SCC for the SuperNode
				SCC nodeComponent = nodeComponents.get(_sv);
				if (nodeComponent != null) {
					// get the contained SuperNodes in the component
					for (SuperNode sv : nodeComponent) {
						// get the successors of each SuperNode of the component
						for (SuperNode succ : sv.successors) {
							SCC succComponent = nodeComponents.get(succ);
							// the SuperNode is not the successor SuperNode
							if (nodeComponent != succComponent) {
								List<SCC> list = componentGraph.get(nodeComponent);
								if(!list.contains(succComponent))
									list.add(succComponent);
							}
						}
					}
				}
//			}
		}
		return componentGraph;
	}

	public List<BasicBlock> collectEntryNodes(Collection<BasicBlock> blocks) {
		List<BasicBlock> entries = new ArrayList<>(blocks);
		ListIterator<BasicBlock> lit = entries.listIterator();
		while (lit.hasNext()) {
			BasicBlock b = lit.next();
			BasicBlock incoming = b.getIncomingImmediate();
			if (incoming != null) {
				// if its got an incoming immediate edge
				// remove it because it cant be a top/start
				lit.remove();
			}
		}
		return entries;
	}

	public SuperNodeList findSuperNodes(List<BasicBlock> blocks, BasicBlock start) {
		List<BasicBlock> entries = collectEntryNodes(blocks);
		SuperNodeList superNodeList = new SuperNodeList(entries, blocks, start);
		
		for (BasicBlock v : entries) {
			SuperNode node = new SuperNode(v);
			superNodeList.add(node);

			while (true) {
				// BasicBlock _v = v;
				v = v.getImmediate();
				
				/* if(v == null) {
					Set<FlowEdge> unconds = _v.getSuccessors(s -> s instanceof UnconditionalJumpEdge);
					if(unconds.size() == 1) {
						UnconditionalJumpEdge uncond = (UnconditionalJumpEdge) unconds.iterator().next();
						BasicBlock dst = uncond.dst;
						if((blocks.indexOf(dst)) == (blocks.indexOf(_v) + 1)) {
							v = uncond.dst; // ie next block
						} else {
							break; // no immediate
						}
						// instruction doesnt matter, fallthrough to addblock
					} else if(unconds.size() < 1) {
						break; // no immediate
					} else {
						throw new IllegalStateException("too many unconditional jumps from " + _v.getId() + " " + unconds);
					}
				} else */
				if (v == null || !blocks.contains(v) || superNodeList.find(v) != null) {
					break; // no immediate
				}

				node.vertices.add(v);
			}
		}

		for (SuperNode node : superNodeList) {
			for (BasicBlock b = node.entry; b != null; b = b.getImmediate()) {
				b.getJumpEdges().stream().filter(edge -> superNodeList.find(edge) != null).map(superNodeList::find).forEach(node::addEdge);
				node.successors.removeAll(node.vertices);
			}
		}
		
		
		superNodeList.cacheFirst();
		return superNodeList;
	}

	public SuperNodeList findSuperNodes(ControlFlowGraph graph) {
		if(graph.getEntries().size() != 1) {
			throw new IllegalStateException();
		}
		return findSuperNodes(new ArrayList<>(graph.vertices()), graph.getEntries().iterator().next());
	}

	public class SCCFinder {
		Map<SuperNode, Integer> low;
		Map<SuperNode, Integer> index;
		Stack<SuperNode> stack;
		List<SCC> components;
		int size;
		SuperNodeList list;

		SCCFinder() {
			low = new HashMap<>();
			index = new HashMap<>();
			stack = new Stack<>();
			components = new ArrayList<>();
		}

		void scc(ControlFlowGraph graph) {
			low.clear();
			index.clear();
			stack.clear();
			components.clear();
			size = graph.vertices().size();

			list = findSuperNodes(graph);

			visit(list.first);
		}

		void visit(SuperNode node) {
			if (low.containsKey(node)) {
				return;
			}

			int num = low.size();
			low.put(node, num);
			index.put(node, num);
			stack.push(node);

			List<SuperNode> succs = node.successors;
			for (SuperNode succ : succs) {
				visit(succ);
				low.put(node, Math.min(low.get(node), low.get(succ)));
			}
			if (num == low.get(node).intValue()) {
				SCC scc = new SCC();
				SuperNode w;
				do {
					w = stack.pop();
					scc.add(0, w);
				} while (w != node);

				// store it
				components.add(0, scc);

				if (scc.size() != 1) {
					for (BasicBlock b : scc.getBlocks()) {
						visit(list.find(b));
						low.put(node, Math.min(low.get(node), low.get(list.find(b))));
					}
				}

				for (SuperNode item : scc) {
					low.put(item, size);
				}
			}
		}
	}

	@SuppressWarnings("serial")
	public static class SCC extends ArrayList<SuperNode> {
		public BasicBlock getFirst() {
			SuperNode sn1 = get(0);
			return sn1.entry;
		}
		
		public List<BasicBlock> getBlocks() {
			List<BasicBlock> list = new ArrayList<>();
			for (SuperNode sn : this) {
				list.addAll(sn.vertices);
			}
			return list;
		}
	}

	@SuppressWarnings("serial")
	public static class SuperNodeList extends ArrayList<SuperNode> {
		private final Collection<BasicBlock> allBlocks;
		private final BasicBlock start;
		public final List<BasicBlock> graphNodes;
		public final List<BasicBlock> entryNodes;
		public SuperNode first;

		public SuperNodeList(List<BasicBlock> entryNodes, Collection<BasicBlock> allBlocks, BasicBlock start) {
			this.allBlocks = allBlocks;
			this.entryNodes = entryNodes;
			this.start = start;
			graphNodes = new ArrayList<>();
		}

		public void cacheFirst() {
			BasicBlock firstBlock = start != null ? start : findFirst();
			first = find(firstBlock);
		}

		public SuperNode find(BasicBlock block) {
			for (SuperNode node : this) {
				if (node.vertices.contains(block)) {
					return node;
				}
			}
			return null;
		}

		private BasicBlock findFirst() {
			for (BasicBlock b : entryNodes) {
				if (b.getSuccessors().size() == 0)
					continue;

				if ((b.getIncomingImmediate() != null) && !allBlocks.contains(b.getIncomingImmediate()))
					return b;

				if (!b.getPredecessors().stream().anyMatch(pred -> allBlocks.contains(pred)))
					return b;
			}

			if (entryNodes.size() > 0)
				return entryNodes.get(0);

			throw new IllegalStateException("couldn't find first block for supernode");
		}
	}
	
	public static class SuperNode {
		public final BasicBlock entry;
		private final int hashCode;
		public final List<BasicBlock> vertices;
		public final List<SuperNode> successors;
		public final List<BasicBlock> predecessors;

		public SuperNode(BasicBlock entry) {
			this.entry = entry;
			hashCode = entry.hashCode();
			vertices = new ArrayList<>();
			successors = new ArrayList<>();
			predecessors = new ArrayList<>();
			vertices.add(entry);
		}
		
		public void addEdge(SuperNode n) {
			successors.add(n);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			
			sb.append("start=").append(entry.getId());
			sb.append(",\n");
			sb.append("   blocks=").append(GraphUtils.toBlockArray(vertices));
			
			sb.append(",\n   ");
			List<BasicBlock> succs = new ArrayList<>();
			for(SuperNode s : successors) {
				succs.addAll(s.vertices);
			}
			sb.append("succs=").append(GraphUtils.toBlockArray(succs));
			
			sb.append(",\n   ");
			sb.append("preds=").append(GraphUtils.toBlockArray(predecessors));
			
			return sb.toString();
			
			/* sb.append("{start=").append(entry.getId());
			sb.append(", blocks=[");
			ListIterator<BasicBlock> lit = vertices.listIterator();
			while(lit.hasNext()) {
				sb.append(lit.next().getId());
				if(lit.hasNext()) {
					sb.append(", ");
				}
			}

			sb.append("], preds=[");
			ListIterator<SuperNode> lit2 = predecessors.listIterator();
			while(lit2.hasNext()) {
				sb.append(lit2.next().entry.getId());
				if(lit2.hasNext()) {
					sb.append(", ");
				}
			}
			
			sb.append("], succs=[");
			lit2 = successors.listIterator();
			while(lit2.hasNext()) {
				sb.append(lit2.next().entry.getId());
				if(lit2.hasNext()) {
					sb.append(", ");
				}
			}
			
			sb.append("]}"); */
		}

		@Override
		public int hashCode() {
//			int result = 1;
//			result = 31 * result + ((entry == null) ? 0 : entry.hashCode());
			// result = prime * result + ((vertices == null) ? 0 : vertices.hashCode());
//			return result;
//			return entry.hashCode();
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof SuperNode))
				return false;
			SuperNode other = (SuperNode) obj;
			if (entry == null) {
				if (other.entry != null)
					return false;
			} else if (!entry.equals(other.entry))
				return false;
			if (vertices == null) {
				if (other.vertices != null)
					return false;
			} else if (!vertices.equals(other.vertices))
				return false;
			return true;
		}
	}
}