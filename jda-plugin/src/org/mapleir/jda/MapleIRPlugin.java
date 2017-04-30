package org.mapleir.jda;

import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.IPass;
import org.mapleir.deob.PassGroup;
import org.mapleir.deob.passes.*;
import org.mapleir.deob.passes.constparam.ConstantExpressionEvaluatorPass;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.objectweb.asm.tree.MethodNode;
import the.bytecode.club.jda.FileContainer;
import the.bytecode.club.jda.api.Plugin;
import the.bytecode.club.jda.decompilers.Decompiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MapleIRPlugin implements Plugin {
	public static Decompiler MAPLEIR = new IRDecompiler();
	
	public MapleIRPlugin() {
		System.out.println("MapleIR plugin loaded");
	}
	
	public static void main(String[] args) {
		new MapleIRPlugin();
	}
	
	@Override
	public int onGUILoad() {
		cfgs = new HashMap<>();
		return 0;
	}
	
	@Override
	public int onExit() {
		return 0;
	}
	
	private void section(String s) {
		System.out.println(s);
	}
	
	public static Map<MethodNode, ControlFlowGraph> cfgs;
	
	@Override
	public int onAddFile(FileContainer fileContainer) {
		return 0;
	}
	
	private static void run(AnalysisContext cxt, PassGroup group) {
		group.accept(cxt, null, new ArrayList<>());
	}
	
	private static IPass[] getTransformationPasses() {
		return new IPass[] {
//				new CallgraphPruningPass(),
				new ConcreteStaticInvocationPass(),
//				new MethodRenamerPass(),
//				new ClassRenamerPass(),
//				new FieldRenamerPass(),
//				new PassGroup("Interprocedural Optimisations")
//					.add(new ConstantParameterPass())
				new ConstantExpressionReorderPass(),
				new FieldRSADecryptionPass(),
				new PassGroup("Interprocedural Optimisations")
					.add(new ConstantParameterPass())
					.add(new ConstantExpressionEvaluatorPass())
					.add(new DeadCodeEliminationPass())
				
		};
	}
}
