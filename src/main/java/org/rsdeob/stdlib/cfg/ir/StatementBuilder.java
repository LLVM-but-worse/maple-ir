package org.rsdeob.stdlib.cfg.ir;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.expr.ArithmeticExpression;
import org.rsdeob.stdlib.cfg.ir.expr.ArithmeticExpression.Operator;
import org.rsdeob.stdlib.cfg.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.InvocationExpression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.ConditionalJumpStatement;
import org.rsdeob.stdlib.cfg.ir.stat.ConditionalJumpStatement.ComparisonType;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.stat.UnconditionalJumpStatement;
import org.rsdeob.stdlib.cfg.ir.stat.header.HeaderStatement;
import org.rsdeob.stdlib.cfg.ir.stat.header.StatementHeaderStatement;

public class StatementBuilder {

	private final RootStatement root;
	
	public StatementBuilder() {
		root = new RootStatement(null);
		root.write(newBlock(root));
	}
	
	public void add(Statement stmt) {
		root.write(stmt);
	}
	
	public InvocationExpression call(int op, String owner, String name, String desc, Expression[] args) {
		return new InvocationExpression(op, args, owner, name, desc);
	}
	
	public List<Statement> whileloop(Expression lhs, Expression rhs, ComparisonType op, List<Statement> body) {
		StatementHeaderStatement bodyHeader = newBlock(body.get(0));
		ConditionalJumpStatement cond = new ConditionalJumpStatement(lhs, rhs, bodyHeader, op);
		StatementHeaderStatement condHeader = newBlock(cond);
		
		List<Statement> stmts = new ArrayList<>();
		stmts.add(new UnconditionalJumpStatement(condHeader));
		stmts.add(bodyHeader);
		stmts.addAll(body);
		stmts.add(cond);
		
		return stmts;
	}
	
	public StatementHeaderStatement newBlock() {
		return new StatementHeaderStatement(null);
	}
	
	public StatementHeaderStatement newBlock(Statement stmt) {
		return new StatementHeaderStatement(stmt);
	}
	
	public ConditionalJumpStatement compare(Expression lhs, Expression rhs, HeaderStatement target, ComparisonType op) {
		return new ConditionalJumpStatement(lhs, rhs, target, op);
	}
	
	public CopyVarStatement assign(VarExpression var, Expression expr) {
		return new CopyVarStatement(var, expr);
	}
	
	public ArithmeticExpression arithmetic(Expression lhs, Expression rhs, Operator op) {
		return new ArithmeticExpression(rhs, lhs, op);
	}
	
	public void assign(int index, Type type, Expression expr) {
		VarExpression var = new VarExpression(index, type);
		assign(var, expr);
	}
	
	public ConstantExpression constant(Object o) {
		return new ConstantExpression(o);
	}

	public RootStatement getRoot() {
		return root;
	}
}