package org.rsdeob.stdlib.ir;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.collections.graph.flow.ExceptionRange;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.stat.header.HeaderStatement;
import org.rsdeob.stdlib.ir.stat.header.StatementHeaderStatement;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

public class RootStatement extends Statement {

	private final MethodNode method;
	private final LocalsHandler locals;
	private final Map<BasicBlock, HeaderStatement> headers;
	private final AtomicInteger maxLocals;
	
	public RootStatement(MethodNode method) {
		this.method = method;
		maxLocals = new AtomicInteger(method.maxLocals + 1);
		locals = new LocalsHandler(maxLocals);
		headers = new HashMap<>();
	}

	public void updateBase() {
		final AtomicInteger max = new AtomicInteger(0);
		new StatementVisitor(this) {
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
		maxLocals.set(max.get() + 1);
		System.out.println("New maxlocals: " + maxLocals.get());
	}
	
	public Map<BasicBlock, HeaderStatement> getHeaders() {
		return headers;
	}
	
	public LocalsHandler getLocals() {
		return locals;
	}

	public MethodNode getMethod() {
		return method;
	}
	
	@Override
	public void onChildUpdated(int ptr) {
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		for (int addr = 0; read(addr) != null; addr++) {
			Statement stmt = read(addr);
			if(!(stmt instanceof StatementHeaderStatement)) {
				printer.print(stmt.getId() + ". ");
				stmt.toString(printer);
			}
			
			Statement next = read(addr + 1);
			if(next != null) {
				if(!(stmt instanceof StatementHeaderStatement)) {
					printer.print('\n', !next.changesIndentation());
				}
			}
		}
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		for(HeaderStatement hs : headers.values()) {
			hs.resetLabel();
		}
		for (int addr = 0; read(addr) != null; addr++) {
			read(addr).toCode(visitor, analytics);
		}
	}

	public void dump(MethodNode m, CodeAnalytics analytics) {
		m.visitCode();
		m.instructions.clear();
		m.tryCatchBlocks.clear();
		updateBase();
		locals.pack(this);
		toCode(m, analytics);
		for(ExceptionRange<BasicBlock> er : analytics.blockGraph.getRanges()) {
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

	@Override
	public boolean canChangeFlow() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean canChangeLogic() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Statement copy() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean equivalent(Statement s) {
		throw new UnsupportedOperationException();
	}
}