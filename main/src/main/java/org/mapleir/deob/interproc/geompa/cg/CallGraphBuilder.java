package org.mapleir.deob.interproc.geompa.cg;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.interproc.geompa.MapleMethod;
import org.mapleir.deob.interproc.geompa.MapleMethodOrMethodContext;
import org.mapleir.deob.interproc.geompa.PointsToAnalysis;
import org.mapleir.deob.interproc.geompa.util.QueueReader;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.locals.Local;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

public final class CallGraphBuilder {
	private PointsToAnalysis pa;
	private final ReachableMethods reachables;
	private final OnFlyCallGraphBuilder ofcgb;
	private final CallGraph cg;

	public CallGraph getCallGraph() {
		return cg;
	}

	public ReachableMethods reachables() {
		return reachables;
	}

	public static ContextManager makeContextManager(CallGraph cg) {
		return new ContextInsensitiveContextManager(cg);
	}

	/**
	 * This constructor builds a complete call graph using the given PointsToAnalysis to resolve virtual calls.
	 */
	public CallGraphBuilder(AnalysisContext cxt, PointsToAnalysis pa) {
		this.pa = pa;
		cg = new CallGraph();
		reachables = new ReachableMethods(cg, wrap(cxt.getApplicationContext().getEntryPoints()));
		ContextManager cm = makeContextManager(cg);
		ofcgb = new OnFlyCallGraphBuilder(cxt, cm, reachables);
	}

	private Set<MapleMethodOrMethodContext> wrap(Set<MethodNode> methods) {
		Set<MapleMethodOrMethodContext> ret = new HashSet<>();
		for (MethodNode m : methods) {
			ret.add(MapleMethod.get(m));
		}
		return ret;
	}

	public void build() {
		QueueReader<MapleMethodOrMethodContext> worklist = reachables.listener();
		while (true) {
			ofcgb.processReachables();
			reachables.update();
			if (!worklist.hasNext())
				break;
			final MapleMethodOrMethodContext momc = worklist.next();
			List<Expr> receivers = ofcgb.methodToReceivers().get(momc.method().getMethodNode());

			if (receivers != null) {
				for (Iterator<Expr> receiverIt = receivers.iterator(); receiverIt.hasNext();) {
					final Expr receiver = receiverIt.next();
					// final PointsToSet p2set = pa.reachingObjects(receiver);
					// for (Iterator<Type> typeIt = p2set.possibleTypes().iterator(); typeIt.hasNext();) {
					// 	final Type type = typeIt.next();
					// 	ofcgb.addType(receiver, momc.context(), type, null);
					// }
				}
			}
//			List<Local> bases = ofcgb.methodToInvokeArgs().get(momc.method());
//			if (bases != null) {
//				for (Local base : bases) {
//					PointsToSet pts = pa.reachingObjects(base);
//					for (Type ty : pts.possibleTypes()) {
//						ofcgb.addBaseType(base, momc.context(), ty);
//					}
//				}
//			}
//			List<Local> argArrays = ofcgb.methodToInvokeBases().get(momc.method());
//			if (argArrays != null) {
//				for (final Local argArray : argArrays) {
//					PointsToSet pts = pa.reachingObjects(argArray);
//					if (pts instanceof PointsToSetInternal) {
//						PointsToSetInternal ptsi = (PointsToSetInternal) pts;
//						ptsi.forall(new P2SetVisitor() {
//							@Override
//							public void visit(Node n) {
//								assert n instanceof AllocNode;
//								AllocNode an = (AllocNode) n;
//								Object newExpr = an.getNewExpr();
//								ofcgb.addInvokeArgDotField(argArray, an.dot(ArrayElement.v()));
//								if (newExpr instanceof NewArrayExpr) {
//									NewArrayExpr nae = (NewArrayExpr) newExpr;
//									Value size = nae.getSize();
//									if (size instanceof IntConstant) {
//										IntConstant arrSize = (IntConstant) size;
//										ofcgb.addPossibleArgArraySize(argArray, arrSize.value, momc.context());
//									} else {
//										ofcgb.setArgArrayNonDetSize(argArray, momc.context());
//									}
//								}
//							}
//						});
//					}
//					for (Type t : pa.reachingObjectsOfArrayElement(pts).possibleTypes()) {
//						ofcgb.addInvokeArgType(argArray, momc.context(), t);
//					}
//				}
//			}
//			List<Local> stringConstants = ofcgb.methodToStringConstants().get(momc.method());
//			if (stringConstants != null) {
//				for (Iterator<Local> stringConstantIt = stringConstants.iterator(); stringConstantIt.hasNext();) {
//					final Local stringConstant = stringConstantIt.next();
//					PointsToSet p2set = pa.reachingObjects(stringConstant);
//					Collection<String> possibleStringConstants = p2set.possibleStringConstants();
//					if (possibleStringConstants == null) {
//						ofcgb.addStringConstant(stringConstant, momc.context(), null);
//					} else {
//						for (Iterator<String> constantIt = possibleStringConstants.iterator(); constantIt.hasNext();) {
//							final String constant = constantIt.next();
//							ofcgb.addStringConstant(stringConstant, momc.context(), constant);
//						}
//					}
//				}
//			}
		}
	}
}
