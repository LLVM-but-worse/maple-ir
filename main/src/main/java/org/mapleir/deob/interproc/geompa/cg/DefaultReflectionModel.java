package org.mapleir.deob.interproc.geompa.cg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.ir.locals.Local;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class DefaultReflectionModel implements ReflectionModel {

	public static boolean SAFE_FOR_NAME = false;
	
	private final ApplicationClassSource app;
	private final OnFlyCallGraphBuilder ofcgb;
	protected HashSet<MethodNode> warnedAlready = new HashSet<>();
	private final Map<MethodNode, List<Local>> methodToStringConstants = new HashMap<>();

	public DefaultReflectionModel(ApplicationClassSource app, OnFlyCallGraphBuilder ofcgb) {
		this.app = app;
		this.ofcgb = ofcgb;
	}

	public List<MethodNode> clinitsOf(ClassNode cl) {
		List<MethodNode> ret = new ArrayList<>();
		while (true) {
			if (cl == null || cl.name.equals("java/lang/Object")) {
				break;
			}
			for (MethodNode m : cl.methods) {
				if (m.name.equals("<clinit>")) {
					ret.add(m);
				}
				cl = app.findClassNode(cl.superName);
			}
		}
		return ret;
	}

	private void constantForName(String cls, MethodNode src, Invocation srcUnit) {
		if (cls.length() > 0 && cls.charAt(0) == '[') {
			if (cls.length() > 1 && cls.charAt(1) == 'L' && cls.charAt(cls.length() - 1) == ';') {
				cls = cls.substring(2, cls.length() - 1);
				constantForName(cls, src, srcUnit);
			}
		} else {
			if (!app.contains(cls)) {
				if (OnFlyCallGraphBuilder.VERBOSE) {
					System.out.println("Warning: Class " + cls + " is" + " a dynamic class, and you did not specify"
							+ " it as such; graph will be incomplete!");
				}
			} else {
				ClassNode sootcls = app.findClassNode(cls);
				for (MethodNode clinit : clinitsOf(sootcls)) {
					ofcgb.addEdge(src, srcUnit, clinit, Kind.CLINIT);
				}
			}
		}
	}

	@Override
	public void classForName(MethodNode source, Invocation s) {
		List<Local> stringConstants = methodToStringConstants.get(source);
		if (stringConstants == null)
			methodToStringConstants.put(source, stringConstants = new ArrayList<>());

		Expr arg0 = s.getArgumentExprs()[0];
		if (arg0 instanceof ConstantExpr) {
			Object val = ((ConstantExpr) arg0).getConstant();
			if (!(val instanceof String)) {
				throw new IllegalStateException(s.toString());
			} else {

			}

			constantForName((String) val, source, s);
		} else if (className instanceof Local) {
			Local constant = (Local) className;
			if (SAFE_FOR_NAME) {
				for (SootMethod tgt : EntryPoints.v().clinits()) {
					addEdge(source, s, tgt, Kind.CLINIT);
				}
			} else {
				for (SootClass cls : Scene.v().dynamicClasses()) {
					for (SootMethod clinit : EntryPoints.v().clinitsOf(cls)) {
						addEdge(source, s, clinit, Kind.CLINIT);
					}
				}
				VirtualCallSite site = new VirtualCallSite(s, source, null, null, Kind.CLINIT);
				List<VirtualCallSite> sites = stringConstToSites.get(constant);
				if (sites == null) {
					stringConstToSites.put(constant, sites = new ArrayList<VirtualCallSite>());
					stringConstants.add(constant);
				}
				sites.add(site);
			}
		}
	}

	@Override
	public void classNewInstance(SootMethod source, Stmt s) {
		if (options.safe_newinstance()) {
			for (SootMethod tgt : EntryPoints.v().inits()) {
				addEdge(source, s, tgt, Kind.NEWINSTANCE);
			}
		} else {
			for (SootClass cls : Scene.v().dynamicClasses()) {
				SootMethod sm = cls.getMethodUnsafe(sigInit);
				if (sm != null) {
					addEdge(source, s, sm, Kind.NEWINSTANCE);
				}
			}

			if (options.verbose()) {
				G.v().out.println("Warning: Method " + source + " is reachable, and calls Class.newInstance;"
						+ " graph will be incomplete!" + " Use safe-newinstance option for a conservative result.");
			}
		}
	}

	@Override
	public void contructorNewInstance(SootMethod source, Stmt s) {
		if (options.safe_newinstance()) {
			for (SootMethod tgt : EntryPoints.v().allInits()) {
				addEdge(source, s, tgt, Kind.NEWINSTANCE);
			}
		} else {
			for (SootClass cls : Scene.v().dynamicClasses()) {
				for (SootMethod m : cls.getMethods()) {
					if (m.getName().equals("<init>")) {
						addEdge(source, s, m, Kind.NEWINSTANCE);
					}
				}
			}
			if (options.verbose()) {
				G.v().out.println("Warning: Method " + source + " is reachable, and calls Constructor.newInstance;"
						+ " graph will be incomplete!" + " Use safe-newinstance option for a conservative result.");
			}
		}
	}

	@Override
	public void methodInvoke(SootMethod container, Stmt invokeStmt) {
		if (!warnedAlready(container)) {
			if (options.verbose()) {
				G.v().out.println("Warning: call to " + "java.lang.reflect.Method: invoke() from " + container
						+ "; graph will be incomplete!");
			}
			markWarned(container);
		}
	}

	private void markWarned(SootMethod m) {
		warnedAlready.add(m);
	}

	private boolean warnedAlready(SootMethod m) {
		return warnedAlready.contains(m);
	}
}