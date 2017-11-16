package org.mapleir.ir.antlr.analysis;

import static org.mapleir.ir.code.Opcode.*;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.ClassTree;
import org.mapleir.flowgraph.algorithms.TarjanDominanceComputor;
import org.mapleir.flowgraph.edges.FlowEdge;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.FastBlockGraph;
import org.mapleir.ir.cfg.builder.ssaopt.Constraint;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.*;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.ir.code.stmt.FieldStoreStmt;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.impl.VersionedLocal;
import org.mapleir.stdlib.collections.ClassHelper;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.GraphUtils;
import org.mapleir.stdlib.collections.graph.algorithms.ExtendedDfs;
import org.mapleir.stdlib.collections.graph.algorithms.TarjanSCC;
import org.mapleir.stdlib.collections.map.KeyedValueCreator;
import org.mapleir.stdlib.collections.map.NullPermeableHashMap;
import org.mapleir.stdlib.collections.map.SetCreator;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public class TypeAnalysis {

    public static Map<BasicBlock, Set<BasicBlock>> computeDominators(FastBlockGraph g) {
        ExtendedDfs<BasicBlock> dfs = new ExtendedDfs<>(g, ExtendedDfs.PRE | ExtendedDfs.POST);
        dfs.run(g.getEntries().iterator().next());

        TarjanDominanceComputor<BasicBlock> domComputer = new TarjanDominanceComputor<>(g,
                dfs.getPreOrder());

        NullPermeableHashMap<BasicBlock, Set<BasicBlock>> doms = new NullPermeableHashMap<>(new KeyedValueCreator<BasicBlock, Set<BasicBlock>>() {
            @Override
            public Set<BasicBlock> create(BasicBlock k) {
                Set<BasicBlock> set = new HashSet<>();
                set.add(k);
                return set;
            }
        });
        
        /* dfs the tree or dfs the cfg? */
        for (BasicBlock b : dfs.getPostOrder()) {
            BasicBlock idom = domComputer.idom(b);
            if (idom != null) {
                Set<BasicBlock> set = doms.getNonNull(idom);
                set.add(b);
                set.addAll(doms.getNonNull(b));
            }
        }
        
        return doms;
    }
    
	public static Map<VersionedLocal, Type> analyse(ApplicationClassSource source, ControlFlowGraph cfg, Map<VersionedLocal, Type> argTypes) {
	    Map<BasicBlock, Set<BasicBlock>> doms = computeDominators(cfg);
	    FastBlockGraph reverseGraph = new FastBlockGraph();
	    GraphUtils.reverse(cfg, reverseGraph);
	    BasicBlock dummyHead = reverseGraph.connectHead();
	    reverseGraph.getEntries().add(dummyHead);
	    Map<BasicBlock, Set<BasicBlock>> postDoms = computeDominators(reverseGraph);
	    
	    
		TypeAnalysis typeAnalysis = new TypeAnalysis(source, cfg, argTypes);
		// typeAnalysis.optimiseWorklist();
		typeAnalysis.populateWorklist();
		typeAnalysis.run();
		
		Map<VersionedLocal, Type> myResults = new HashMap<>();
		for(Entry<VersionedLocal, LocalType> e : typeAnalysis.results.entrySet()) {
			System.out.println(e);
			LocalType lt = e.getValue();
			if(!lt.isTop) {
				myResults.put(e.getKey(), lt.type);
			}
		}
		
		return myResults;
	}

	private final ApplicationClassSource source;
	private final ControlFlowGraph cfg;
	private final List<BasicBlock> topoOrder;
	private final Map<BasicBlock, List<BasicBlock>> components;
	private final Map<VersionedLocal, Type> argTypes;
	private final LocalsPool pool;
	private final LinkedList<Stmt> worklist;
	private final Map<VersionedLocal, LocalType> results;
	private final Map<Local, Set<Constraint>> constraints;
	
	private TypeAnalysis(ApplicationClassSource source, ControlFlowGraph cfg, Map<VersionedLocal, Type> argTypes) {
		this.source = source;
		this.cfg = cfg;
		this.argTypes = argTypes;
		pool = cfg.getLocals();
		// topo is probably optimal
		topoOrder = computeTopoOrder();
		components = new HashMap<>();
		
		// Set<VersionedLocal> ssaLocals = getSSALocals(pool);
		// this.worklist = new LinkedList<>(ssaLocals);
		worklist = new LinkedList<>();
		results = new HashMap<>();
		constraints = new HashMap<>();
	}
	
	private List<List<BasicBlock>> computeComponents() {
		BasicBlock entry = cfg.getEntries().iterator().next();
		TarjanSCC<BasicBlock> sccComputer = new TarjanSCC<>(cfg);
		sccComputer.search(entry);
		return sccComputer.getComponents();
	}
	
	private List<BasicBlock> computeTopoOrder() {
		BasicBlock entry = cfg.getEntries().iterator().next();
		
		ExtendedDfs<BasicBlock> dfs = new ExtendedDfs<>(cfg, ExtendedDfs.TOPO);
		dfs.run(entry);
		return dfs.getTopoOrder();
	}
	
	@SuppressWarnings("unchecked")
	private static Set<VersionedLocal> getSSALocals(LocalsPool pool) {
		@SuppressWarnings("rawtypes")
		Set ssaLocals = pool.getAll(p -> p instanceof VersionedLocal);
		return ssaLocals;
	}
	
	private void populateWorklist() {
	    for(BasicBlock b : topoOrder) {
	    	System.out.println(b.getDisplayName());
	        worklist.addAll(b);
	    }
	}
	
	/*private void optimiseWorklist() {
		Collections.sort(worklist, new Comparator<VersionedLocal>() {
			@Override
			public int compare(VersionedLocal o1, VersionedLocal o2) {
				AbstractCopyStmt copy1 = pool.defs.get(o1);
				AbstractCopyStmt copy2 = pool.defs.get(o2);
				
				BasicBlock b1 = copy1.getBlock();
				BasicBlock b2 = copy2.getBlock();
				
				if(b1 != b2) {
					return Integer.compare(topoOrder.indexOf(b1), topoOrder.indexOf(b2));
				} else {
					return Integer.compare(b1.indexOf(copy1), b1.indexOf(copy2));
				}
			}
		});
	}*/
	
	private boolean hasComputedType(VersionedLocal l) {
		return results.containsKey(l) && !results.get(l).isTop;
	}
	
	private void queueSuccessors(Stmt stmt) {
		BasicBlock block = stmt.getBlock();
		
		int idx = block.indexOf(stmt);
		
		if(idx == (block.size() - 1)) {
			for(FlowEdge<BasicBlock> succEdge : cfg.getEdges(block)) {
				BasicBlock succ = succEdge.dst();
				if(!succ.isEmpty()) {
					throw new IllegalArgumentException();
				}
				worklist.add(succ.get(0));
			}
		} else {
			worklist.add(block.get(idx + 1));
		}
	}
	
	private void run() {
        /* Due to the loss of type information in the IR textcode,
         * we need to type the code and fill in artifacts that are
         * context-dependent on these types.
         * 
         * We start off with the given facts:
         *   -The type of each parameter of the function is equal
         *    to the type given by the method descriptor.
         *   -If the method is virtual, the first local variable is
         *    a type representation of the class that the method is
         *    declared in.
         *   -Each variable that is assigned to the source expr
         *    of catch() is given the type indicated in the handler
         *    table.
         *    
         * Each expression that uses a variable must have that variable
         * declared before it is used. In SSA this could be either a phi
         * or a regular local variable. In either case, this variable
         * must be typed before the expression can be evaluated. Therefore
         * we visit the code in reverse postorder to ensure that the
         * 'parents' or predecessor statements of a statement are visited
         * before the statement itself is.
         * 
         * If it is determined at a point that a statement cannot be properly
         * typed due to a lack of information. - What do we do? TOP indicates
         * that one of the predecessors was untyped, but it can't be since they
         * must be declared before reaching a use? Maybe this can happen with phis?
         * 
         * 
         */
	    
	    worklistLoop: while(!worklist.isEmpty()) {
	        Stmt stmt = worklist.pop();
	        int opcode = stmt.getOpcode();
	        
	        switch(opcode) {
	            case LOCAL_LOAD: {
	                CopyVarStmt cvs = (CopyVarStmt) stmt;
	                VersionedLocal dst = (VersionedLocal) cvs.getVariable().getLocal();
	                
	                if(!hasComputedType(dst)) {
						if(argTypes.containsKey(dst)) {
							results.put(dst, new LocalType(argTypes.get(dst)));
						} else {
							Expr rhs = cvs.getExpression();
							LocalType computedType = computeStaticType(rhs);
							
							if(computedType.isTop) {
								worklist.add(stmt);
							} else {
								results.put(dst, computedType);
							}
						}
						
						queueSuccessors(stmt);
	                }
	                break;
	            }
	            case PHI_STORE: {
	                CopyPhiStmt cps = (CopyPhiStmt) stmt;
	                VersionedLocal dst = (VersionedLocal) cps.getVariable().getLocal();
	                
					PhiExpr phiExpr = cps.getExpression();
					List<VersionedLocal> srcLocals = new ArrayList<>();
					for(Expr e : phiExpr.getArguments().values()) {
						VarExpr varExpr = (VarExpr) e;
						VersionedLocal src = (VersionedLocal) varExpr.getLocal();
						srcLocals.add(src);
					}
					srcLocals.remove(dst);
					
					boolean fail = false;
					
					Set<Type> srcTypes = new HashSet<>();
					for(VersionedLocal src : srcLocals) {
						if(!hasComputedType(src)) {
							// worklist.addAll(srcLocals);
							fail = true;
						} else {
							srcTypes.add(results.get(src).type);
						}
					}
					
					if(!fail) {
						LocalType lub = computeLeastUpperBound(srcTypes);
						results.put(dst, lub);
					}
	                break;
	            }
	            case FIELD_STORE: {
	                FieldStoreStmt fss = (FieldStoreStmt) stmt;
	                
	                break;
	            }
	        }
	    }
		/*worklistLoop: while(!worklist.isEmpty()) {
			VersionedLocal l = worklist.pop();

			AbstractCopyStmt copyStmt = pool.defs.get(l);
			
			if(copyStmt.getOpcode() == Opcode.PHI_STORE) {
				PhiExpr phiExpr = (PhiExpr) copyStmt.getExpression();
				List<VersionedLocal> srcLocals = new ArrayList<>();
				for(Expr e : phiExpr.getArguments().values()) {
					VarExpr varExpr = (VarExpr) e;
					VersionedLocal src = (VersionedLocal) varExpr.getLocal();
					srcLocals.add(src);
				}
				srcLocals.remove(l);
				
				Set<Type> srcTypes = new HashSet<>();
				for(VersionedLocal src : srcLocals) {
					if(!hasComputedType(src)) {
						worklist.addAll(srcLocals);
						continue worklistLoop;
					} else {
						srcTypes.add(results.get(src).type);
					}
				}
				
				LocalType lub = computeLeastUpperBound(srcTypes);
				results.put(l, lub);
				
			} else if(copyStmt.getOpcode() == Opcode.LOCAL_STORE) {
				if(hasComputedType(l)) {
					results.put(l, results.get(l));
				} else {
					if(argTypes.containsKey(l)) {
						results.put(l, new LocalType(argTypes.get(l)));
					} else {
						Expr rhs = copyStmt.getExpression();
						LocalType computedType = computeStaticType(rhs);
						
						if(computedType.isTop) {
							worklist.add(l);
						} else {
							results.put(l, computedType);
						}
					}
				}
			} else {
				throw new IllegalStateException(String.format("unknown copystmt format: %s", copyStmt));
			}
		}*/
		
		System.out.println(worklist);
	}
	
	private LocalType computeStaticType(Expr e) {
		int opcode = e.getOpcode();
		
		switch(opcode) {
			case CONST_LOAD: {
				ConstantExpr constExpr = (ConstantExpr) e;
				return new LocalType(constExpr.getType());
			}
			case FIELD_LOAD: {
				FieldLoadExpr fieldLoadExpr = (FieldLoadExpr) e;
				return new LocalType(Type.getType(fieldLoadExpr.getDesc()));
			}
			case ARRAY_LOAD: {
				ArrayLoadExpr arrayLoadExpr = (ArrayLoadExpr) e;
				LocalType arrayType = computeStaticType(arrayLoadExpr.getArrayExpression());
				System.out.println("TODO: " + arrayLoadExpr);
				return arrayType;
			}
			case LOCAL_LOAD: {
				VarExpr varExpr = (VarExpr) e;
				VersionedLocal vl = (VersionedLocal) varExpr.getLocal();
				
				if(hasComputedType(vl)) {
					return results.get(vl);
				} else {
					return LocalType.TOP;
				}
			}
			case INVOKE:
			case DYNAMIC_INVOKE:
			case INIT_OBJ: {
				Invocation invocation = (Invocation) e;
				Type returnType = Type.getReturnType(invocation.getDesc());
				if(returnType.getSort() == Type.VOID) {
					return LocalType.BOTTOM;
				} else {
					return new LocalType(returnType);
				}
			}
			case ARITHMETIC: {
				ArithmeticExpr arithmeticExpr = (ArithmeticExpr) e;
				LocalType left = computeStaticType(arithmeticExpr.getLeft());
				LocalType right = computeStaticType(arithmeticExpr.getRight());
				
				if(left.isBottom || right.isBottom) {
					return LocalType.BOTTOM;
				} else if(left.isTop || right.isTop) {
					return LocalType.TOP;
				} else {
					return new LocalType(ArithmeticExpr.getType(left.type, right.type, arithmeticExpr.getOperator()));
				}
			}
			case NEGATE: {
				NegationExpr negationExpr = (NegationExpr) e;
				return computeStaticType(negationExpr.getExpression());
			}
			case NEW_ARRAY: {
				NewArrayExpr newArrayExpr = (NewArrayExpr) e;
				System.out.println("NAE: " + newArrayExpr);
				return new LocalType(newArrayExpr.getType());
			}
			case ARRAY_LEN: 
			case COMPARE: 
			case ALLOC_OBJ: 
			case CAST: 
			case CATCH: {
				return new LocalType(e.getType());
			}
			default: {
				throw new UnsupportedOperationException(String.format("Unknown expr, type=%s", opname(opcode)));
			}
		}
	}
	
	private LocalType computeLeastUpperBound(Set<Type> types) {
		if(types.size() == 0) {
			throw new IllegalStateException();
		}
		
		Type lub = null;
		
		for(Type t : types) {
			if(lub != null) {
				boolean prim1 = isPrimitive(t);
				boolean prim2 = isPrimitive(lub);
				
				/* primitive and reference slot at the same
				 * time, problem. */
				if(!(prim1 ^ prim2)) {
					return LocalType.BOTTOM;
				} else {
					if(prim1) {
						/* both prim */
						if(!t.equals(lub)) {
							return LocalType.BOTTOM;
						}
					} else {
						System.out.println(t);
						System.out.println(lub);
					}
				}
			} else {
				lub = t;
			}
		}

		return new LocalType(lub);
	}
	
	private String getCommonSuperType(String type1, String type2) {
    	ClassNode ccn = source.findClassNode(type1);
    	ClassNode dcn = source.findClassNode(type2);
    	
    	if(ccn == null) {
    		ClassNode c = ClassHelper.create(type1);
    		if(c == null) {
    			return "java/lang/Object";
    		}
    		throw new UnsupportedOperationException(c.toString());
    	}
    	
    	if(dcn == null) {
    		ClassNode c = ClassHelper.create(type2);
    		if(c == null) {
    			return "java/lang/Object";
    		}
    		throw new UnsupportedOperationException(c.toString());
    	}
    	
    	ClassTree tree = source.getClassTree();
    	Collection<ClassNode> c = tree.getAllParents(ccn);
    	Collection<ClassNode> d = tree.getAllParents(dcn);
        
        if(c.contains(dcn))
        	return type1;
        
        if(d.contains(ccn))
        	return type2;
        
        if(Modifier.isInterface(ccn.access) || Modifier.isInterface(dcn.access)) {
        	return "java/lang/Object";
        } else {
        	do {
        		ClassNode nccn = source.findClassNode(ccn.superName);
        		if(nccn == null)
        			break;
        		ccn = nccn;
        		c = tree.getAllParents(ccn);
        	} while(!c.contains(dcn));
        	return ccn.name;
        }
    }
	
	private static boolean isPrimitive(Type t) {
		int sort = t.getSort();
		return sort != Type.OBJECT && sort != Type.ARRAY;
	}
	
	private static class LocalType {
		static final LocalType TOP = new LocalType(true, false);
		static final LocalType BOTTOM = new LocalType(false, true);
		
		private boolean isTop;
		private boolean isBottom;
		private Type type;
		
		private LocalType(boolean isTop, boolean isBottom) {
			this.isTop = isTop;
			this.isBottom = isBottom;
			
			if(isTop == isBottom) {
				throw new IllegalStateException();
			}
		}
		
		public LocalType(Type type) {
			this.type = type;
			isTop = false;
			isBottom = false;
		}
		
		@Override
		public String toString() {
			return "LocalType [isTop=" + isTop + ", isBottom=" + isBottom + ", type=" + type + "]";
		}
	}
}