package org.rsdeob.stdlib.ir;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.collections.graph.flow.ExceptionRange;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.header.BlockHeaderStatement;
import org.rsdeob.stdlib.ir.header.HeaderStatement;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class StatementWriter {
	
	private final CodeBody stmts;
	private final ControlFlowGraph cfg;
	private final Map<BasicBlock, HeaderStatement> headers;

	public StatementWriter(CodeBody stmts, ControlFlowGraph cfg) {
		this.stmts = stmts;
		this.cfg = cfg;

		this.headers = new HashMap<>();
		for (Statement stmt : stmts)
			if (stmt instanceof BlockHeaderStatement)
				headers.put(((BlockHeaderStatement) stmt).getBlock(), (HeaderStatement) stmt);
	}

	private int calculateBase() {
		final AtomicInteger max = new AtomicInteger(0);
		for (Statement stmt : stmts) {
			new StatementVisitor(stmt) {
				@Override
				public Statement visit(Statement s) {
					if (s instanceof VarExpression) {
						Local local = ((VarExpression) s).getLocal();
						if (!local.isStack() && local.getIndex() > max.get())
							max.set(local.getIndex());
					}
					return s;
				}
			}.visit();
		}
		return max.get() + 1;
	}

	public void dump(MethodNode m, CodeAnalytics analytics) {
		m.visitCode();
		m.instructions.clear();
		m.tryCatchBlocks.clear();
		stmts.getLocals().realloc(stmts);

		for(HeaderStatement header : headers.values())
			header.resetLabel();
		for (Statement stmt : stmts) {
			stmt.toCode(m, analytics);
		}

		for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
			String type = null;
			Set<String> typeSet = er.getTypes();
			if(typeSet.size() == 0 || typeSet.size() > 1) {
				// TODO: fix base exception
				type = Throwable.class.getCanonicalName().replace(".", "/");
			} else {
				// size == 1
				type = typeSet.iterator().next();
			}
			List<BasicBlock> range = er.get();
			Label start = headers.get(range.get(0)).getLabel();
			Label end = null;
			BasicBlock endBlock = range.get(range.size() - 1);
			BasicBlock im = endBlock.getImmediate();
			if(im == null) {
				// end of code?
				throw new RuntimeException(m.toString());
			} else {
				end = headers.get(im).getLabel();
			}
			Label handler = headers.get(er.getHandler()).getLabel();
			m.visitTryCatchBlock(start, end, handler, type);
		}
		m.visitEnd();
	}
}
