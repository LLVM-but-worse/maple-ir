package org.rsdeob.stdlib.cfg.ir;

import java.util.Set;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.expr.ArithmeticExpression;
import org.rsdeob.stdlib.cfg.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.InvocationExpression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.transform.ForwardsFlowAnalyser;
import org.rsdeob.stdlib.cfg.ir.transform.impl.DefinitionAnalyser;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.SetCreator;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;

public class StatementVerifier extends ForwardsFlowAnalyser<Statement, FlowEdge<Statement>, Type> {

	private final DefinitionAnalyser defs;
	private final NullPermeableHashMap<Statement, Set<VarExpression>> uses;

	public StatementVerifier(FlowGraph<Statement, FlowEdge<Statement>> graph, DefinitionAnalyser defs) {
		super(graph);
		this.defs = defs;
		
		uses = new NullPermeableHashMap<>(new SetCreator<>());
		for(Statement stmt : graph.vertices()) {
			StatementVisitor vis = new StatementVisitor(stmt) {
				@Override
				public Statement visit(Statement s) {
					if(s instanceof VarExpression) {
						uses.getNonNull(stmt).add((VarExpression)s);
					}
					return s;
				}
			};
			vis.visit();
		}
	}

	@Override
	protected Type newState() {
		return Type.VOID_TYPE;
	}

	@Override
	protected Type newEntryState() {
		return Type.VOID_TYPE;
	}

	@Override
	protected void merge(Type in1, Type in2, Type out) {		
	}

	@Override
	protected void copy(Type src, Type dst) {		
	}

	@Override
	protected boolean equals(Type s1, Type s2) {
		return true;
	}

	@Override
	protected void propagate(Statement n, Type ___, Type ____) {
		if(n instanceof InvocationExpression) {
			InvocationExpression inv = (InvocationExpression) n;
			Expression inst = inv.getInstanceExpression();
			if(inst != null) {
				
			}
		}
	}
	
	private void checkObjectType(Expression expr) {
		if(expr instanceof ConstantExpression || expr instanceof ArithmeticExpression) {
			throw new IllegalStateException(expr.toString());
		} else if(TypeUtils.asSimpleType(expr.getType()))
	}
}