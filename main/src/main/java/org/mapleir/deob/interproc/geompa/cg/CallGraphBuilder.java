package org.mapleir.deob.interproc.geompa.cg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.mapleir.deob.interproc.geompa.AllocNode;
import org.mapleir.deob.interproc.geompa.ArrayElement;
import org.mapleir.deob.interproc.geompa.PointsToAnalysis;
import org.mapleir.deob.interproc.geompa.util.QueueReader;
import org.mapleir.ir.code.expr.NewArrayExpr;
import org.mapleir.ir.locals.Local;
import org.objectweb.asm.tree.analysis.Value;

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
	 * This constructor builds a complete call graph using the given
	 * PointsToAnalysis to resolve virtual calls.
	 */
	public CallGraphBuilder(PointsToAnalysis pa) {
		this.pa = pa;
		cg = new CallGraph();
		Scene.v().setCallGraph(cg);
		reachables = Scene.v().getReachableMethods();
		ContextManager cm = makeContextManager(cg);
		ofcgb = new OnFlyCallGraphBuilder(cm, reachables);
	}

	/**
	 * This constructor builds the incomplete hack call graph for the Dava
	 * ThrowFinder. It uses all application class methods as entry points, and
	 * it ignores any calls by non-application class methods. Don't use this
	 * constructor if you need a real call graph.
	 */
	public CallGraphBuilder() {
		G.v().out.println("Warning: using incomplete callgraph containing "
				+ "only application classes.");
		pa = soot.jimple.toolkits.pointer.DumbPointerAnalysis.v();
		cg = new CallGraph();
		Scene.v().setCallGraph(cg);
		List<MethodOrMethodContext> entryPoints = new ArrayList<MethodOrMethodContext>();
		entryPoints.addAll(EntryPoints.v().methodsOfApplicationClasses());
		entryPoints.addAll(EntryPoints.v().implicit());
		reachables = new ReachableMethods(cg, entryPoints);
		ContextManager cm = new ContextInsensitiveContextManager(cg);
		ofcgb = new OnFlyCallGraphBuilder(cm, reachables, true);
	}

	public void build() {
		QueueReader<MethodOrMethodContext> worklist = reachables.listener();
		while (true) {
			ofcgb.processReachables();
			reachables.update();
			if (!worklist.hasNext())
				break;
			final MethodOrMethodContext momc = worklist
					.next();
			List<Local> receivers = ofcgb.methodToReceivers()
					.get(momc.method());
			if (receivers != null)
				for (Iterator<Local> receiverIt = receivers.iterator(); receiverIt
						.hasNext();) {
					final Local receiver = receiverIt.next();
					final PointsToSet p2set = pa.reachingObjects(receiver);
					for (Iterator<Type> typeIt = p2set.possibleTypes()
							.iterator(); typeIt.hasNext();) {
						final Type type = typeIt.next();
						ofcgb.addType(receiver, momc.context(), type, null);
					}
				}
			List<Local> bases = ofcgb.methodToInvokeArgs().get(momc.method());
			if(bases != null) {
				for(Local base : bases) {
					PointsToSet pts = pa.reachingObjects(base);
					for(Type ty : pts.possibleTypes()) {
						ofcgb.addBaseType(base, momc.context(), ty);
					}
				}
			}
			List<Local> argArrays = ofcgb.methodToInvokeBases().get(momc.method());
			if(argArrays != null) {
				for(final Local argArray : argArrays) {
					PointsToSet pts = pa.reachingObjects(argArray);
					if(pts instanceof PointsToSetInternal) {
						PointsToSetInternal ptsi = (PointsToSetInternal) pts;
						ptsi.forall(new P2SetVisitor() {
							@Override
							public void visit(Node n) {
								assert n instanceof AllocNode;
								AllocNode an = (AllocNode)n;
								Object newExpr = an.getNewExpr();
								ofcgb.addInvokeArgDotField(argArray, an.dot(ArrayElement.v()));
								if(newExpr instanceof NewArrayExpr) {
									NewArrayExpr nae = (NewArrayExpr) newExpr;
									Value size = nae.getSize();
									if(size instanceof IntConstant) {
										IntConstant arrSize = (IntConstant) size;
										ofcgb.addPossibleArgArraySize(argArray, arrSize.value, momc.context());
									} else {
										ofcgb.setArgArrayNonDetSize(argArray, momc.context());
									}
								}
							}
						});
					}
					for(Type t : pa.reachingObjectsOfArrayElement(pts).possibleTypes()) {
						ofcgb.addInvokeArgType(argArray, momc.context(), t);
					}
				}
			}
			List<Local> stringConstants = ofcgb.methodToStringConstants().get(
					momc.method());
			if (stringConstants != null)
				for (Iterator<Local> stringConstantIt = stringConstants
						.iterator(); stringConstantIt.hasNext();) {
					final Local stringConstant = stringConstantIt.next();
					PointsToSet p2set = pa.reachingObjects(stringConstant);
					Collection<String> possibleStringConstants = p2set
							.possibleStringConstants();
					if (possibleStringConstants == null) {
						ofcgb.addStringConstant(stringConstant, momc.context(),
								null);
					} else {
						for (Iterator<String> constantIt = possibleStringConstants
								.iterator(); constantIt.hasNext();) {
							final String constant = constantIt.next();
							ofcgb.addStringConstant(stringConstant,
									momc.context(), constant);
						}
					}
				}
		}
	}
}