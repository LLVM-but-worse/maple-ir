package org.rsdeob.stdlib.cfg;

import static org.objectweb.asm.Opcodes.GOTO;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.rsdeob.stdlib.cfg.FlowEdge.ImmediateEdge;
import org.rsdeob.stdlib.cfg.FlowEdge.TryCatchEdge;
import org.rsdeob.stdlib.cfg.FlowEdge.UnconditionalJumpEdge;
//import org.rsdeob.stdlib.cfg.util.
import org.rsdeob.stdlib.cfg.util.GraphUtils;

public class ControlFlowGraphDeobfuscator {
	
	public int prunedGotos;
	public int removedEmpty;
	public int inlinedComponents;
	
	public List<BasicBlock> deobfuscate(ControlFlowGraph cfg) {
//		Collection<BasicBlock> blocks = cfg.blocks();
		
		SCCFinder finder = new SCCFinder();
		finder.scc(cfg);
		
		Map<SCC, List<SCC>> graph = createSuperNodeMap(cfg, finder);
		List<BasicBlock> newOrder = biblSort(cfg, finder, graph);

//		if(blocks.size() != newOrder.size()) {
//			List<BasicBlock> missing;
//			if(blocks.size() > newOrder.size()) {
//				missing = new ArrayList<>(blocks);
//				missing.removeAll(newOrder);
//			} else {
//				// newOrder > blocks
//				missing = new ArrayList<>(newOrder);
//				missing.removeAll(blocks);
//			}
//			throw new IllegalStateException("block mismatch: " + GraphUtils.toBlockArray(missing));
//		}
		
		newOrder = pruneGotos(cfg, newOrder);
		
//		System.out.println("order: " + GraphUtils.toBlockArray(newOrder));
//		System.out.println(GraphUtils.toString(cfg, newOrder));
		
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
				if((b.getPredecessors().size() == 0 && b != cfg.getEntry())) {
					cfg.removeVertex(b);
					blocks.remove(b);
					change = true;
				} else if(b.cleanSize() == 0) {
					// implies 1 immediate successor
					// transfer predecessor edges to its successor
//					ImmediateEdge succ = b.getImmediateEdge();
//					Set<FlowEdge> allSuccs = b.getSuccessors(e -> !(e instanceof TryCatchEdge));
//					if(succ == null || allSuccs.size() != 1) {
//						throw new IllegalStateException(succ + " " + allSuccs);
//					}
//					
//					cfg.removeEdge(b, succ);
//					
//					Set<FlowEdge> preds = b.getPredecessors();
//					for(FlowEdge e : preds) {
//						FlowEdge cloned = e.clone(e.src, succ.dst);
//						cfg.addEdge(e.src, cloned);
//					}
//					
//					cfg.removeVertex(b);
//					change = true;
//					break;
				} else {
					FlowEdge incomingImmediate = b.getIncomingImmediateEdge();
					if(incomingImmediate != null && b.getPredecessors().size() == 1) {
						BasicBlock pred = incomingImmediate.src;
						
						if(!GraphUtils.isFlowBlock(pred)) {
							// check that the exceptions are the same
						
							List<ExceptionRange> predRanges = pred.getProtectingRanges();
							List<ExceptionRange> blockRanges = b.getProtectingRanges();
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
								for(FlowEdge e : b.getSuccessors(e -> !(e instanceof TryCatchEdge))) {
									BasicBlock target = e.dst;
									FlowEdge cloned = e.clone(pred, target);
									cfg.addEdge(pred, cloned);
								}
								
								// update the predecessors of b to point to predecessor
								//   only 1 predecessor of this block so we don't need to update
								//   the other predecessors
								
								/* for(FlowEdge e : b.getPredecessors(e -> (e.src != pred))) {
									BasicBlock p = e.src;
									FlowEdge cloned = e.clone(p, pred);
									cfg.addEdge(p, cloned);
									p.updateLabelRef(b, pred);
								} */
								
								for(ExceptionRange er : predRanges) {
									er.removeBlock(b);
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
				for(FlowEdge e : b.getSuccessors()) {
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
									List<ExceptionRange> targRanges = dst.getProtectingRanges();
									List<ExceptionRange> blockRanges = b.getProtectingRanges();
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

					Set<FlowEdge> jumps = b.getSuccessors(s -> s instanceof UnconditionalJumpEdge);
					if(jumps.size() > 1 || jumps.size() <= 0) {
						throw new IllegalStateException("goto with misplaced branches, " + b.getId() + jumps);
					}
					
					FlowEdge im = jumps.iterator().next();
					BasicBlock target = im.dst;
					int targetIndex = blocks.indexOf(target);
					// System.out.printf("%s, t=%d, b=%d.%n", b.getId(), targetIndex, index);
					
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
						im = new ImmediateEdge(b, target);
						cfg.addEdge(b, im);
//						System.out.println("pruning " + b.getId());

						if(!b.isHandler() && b.cleanSize() == 0) {
							// if we have anything other than an immediate
							// or handler edges, something seriously fucked
							// up is going on...
							for(FlowEdge e : cfg.getEdges(b)) {
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
							
							for(FlowEdge pe : cfg.getReverseEdges(b)) {
								BasicBlock src = pe.src;
								FlowEdge ce = pe.clone(src, target);
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
		
		stack.push(cfg.getEntry());
		visited.add(cfg.getEntry());
		
		while(!stack.isEmpty()) {
			BasicBlock b = stack.pop();
			
			for(FlowEdge succE : b.getSuccessors()) {
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
		Map<SCC, Integer> count = new HashMap<SCC, Integer>();
		for(SCC node : graph.keySet()) {
			count.put(node, 0);
		}
		for(SCC node : graph.keySet()) {
			for(SCC succ : graph.get(node)) {
				count.put(succ, count.get(succ).intValue() + 1);
			}
		}
		Stack<SCC> ready = new Stack<SCC>();
		for(Entry<SCC, Integer> e : count.entrySet()) {
			if(e.getValue().intValue() == 0) {
				ready.push(e.getKey());
			}
		}
		List<SCC> result = new ArrayList<SCC>();
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
		Map<SuperNode, SCC> nodeComponents = new HashMap<SuperNode, SCC>();
		for (SCC scc : finder.components) {
			for (SuperNode node : scc) {
				nodeComponents.put(node, scc);
			}
		}

		Map<SCC, List<SCC>> componentGraph = new HashMap<SCC, List<SCC>>();
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
		return findSuperNodes(new ArrayList<>(graph.blocks()), graph.getEntry());
	}

	public class SCCFinder {
		Map<SuperNode, Integer> low;
		Map<SuperNode, Integer> index;
		Stack<SuperNode> stack;
		List<SCC> components;
		int size;
		SuperNodeList list;

		SCCFinder() {
			low = new HashMap<SuperNode, Integer>();
			index = new HashMap<SuperNode, Integer>();
			stack = new Stack<SuperNode>();
			components = new ArrayList<SCC>();
		}

		void scc(ControlFlowGraph graph) {
			low.clear();
			index.clear();
			stack.clear();
			components.clear();
			size = graph.blocks().size();

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
}