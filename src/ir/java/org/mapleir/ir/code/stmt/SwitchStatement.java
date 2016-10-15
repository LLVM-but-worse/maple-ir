package org.mapleir.ir.code.stmt;

import java.util.*;
import java.util.Map.Entry;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class SwitchStatement extends Statement {

	private Expression expression;
	private LinkedHashMap<Integer, BasicBlock> targets;
	private BasicBlock defaultTarget;

	public SwitchStatement(Expression expr, LinkedHashMap<Integer, BasicBlock> targets, BasicBlock defaultTarget) {
		super(SWITCH_JUMP);
		setExpression(expr);
		this.targets = targets;
		this.defaultTarget = defaultTarget;
	}

	public Expression getExpression() {
		return expression;
	}

	public void setExpression(Expression expression) {
		this.expression = expression;
		overwrite(expression, 0);
	}

	public LinkedHashMap<Integer, BasicBlock> getTargets() {
		return targets;
	}

	public void setTargets(LinkedHashMap<Integer, BasicBlock> targets) {
		this.targets = targets;
	}

	public BasicBlock getDefaultTarget() {
		return defaultTarget;
	}

	public void setDefaultTarget(BasicBlock defaultTarget) {
		this.defaultTarget = defaultTarget;
	}

	@Override
	public void onChildUpdated(int ptr) {
		if (ptr == 0) {
			setExpression((Expression) read(ptr));
		}
	}
	
	private boolean needsSort() {
		if (targets.size() <= 1) {
			return false;
		}
		
		Iterator<Integer> it = targets.keySet().iterator();
		int last = it.next();
		while(it.hasNext()) {
			int i = it.next();
			if(last >= i) {
				return true;
			}
			
			last = i;
		}
		
		return false;
	}
	
	private boolean fitsIntoTableSwitch() {
		if (targets.isEmpty()) {
			return false;
		}
		
		Iterator<Integer> it = targets.keySet().iterator();
		int last = it.next();
		while(it.hasNext()) {
			int i = it.next();
			if(i != (last + 1)) {
				return false;
			}
			
			last = i;
		}
		
		return true;
	}

	
	private void sort() {
		List<Integer> keys = new ArrayList<>(targets.keySet());
		Collections.sort(keys);
		
		LinkedHashMap<Integer, BasicBlock> newMap = new LinkedHashMap<>();
		for(int key : keys) {
			BasicBlock targ = targets.get(keys);
			newMap.put(key, targ);
		}
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		if (needsSort()) {
			sort();
		}
		
		printer.print("switch ");
		printer.print('(');
		expression.toString(printer);
		printer.print(')');
		printer.print(" {");
		printer.tab();
		for(Entry<Integer, BasicBlock> e : targets.entrySet()) {
			printer.print("\ncase " + e.getKey() + ":\n\t goto\t#" + e.getValue().getId());

		}
		printer.print("\ndefault:\n\t goto\t#" + defaultTarget.getId());
		printer.untab();
		printer.print("\n}");		
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		if (needsSort()) {
			sort();
		}

		int[] cases = new int[targets.size()];
		Label[] labels = new Label[targets.size()];
		int j = 0;
		for (Entry<Integer, BasicBlock> e : targets.entrySet()) {
			cases[j] = e.getKey();
			labels[j++] = e.getValue().getLabel();
		}

		expression.toCode(visitor, cfg);
		int[] cast = TypeUtils.getPrimitiveCastOpcodes(expression.getType(), Type.INT_TYPE); // widen
		for (int i = 0; i < cast.length; i++) {
			visitor.visitInsn(cast[i]);
		}
		boolean fitsIntoTable = fitsIntoTableSwitch();
		if (fitsIntoTable) {
			visitor.visitTableSwitchInsn(cases[0], cases[cases.length - 1], defaultTarget.getLabel(), labels);
		} else {
			visitor.visitLookupSwitchInsn(defaultTarget.getLabel(), cases, labels);
		}
	}

	@Override
	public boolean canChangeFlow() {
		return true;
	}

	@Override
	public boolean canChangeLogic() {
		return expression.canChangeLogic();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return expression.isAffectedBy(stmt);
	}

	@Override
	public Statement copy() {
		return new SwitchStatement(expression.copy(), new LinkedHashMap<>(targets), defaultTarget);
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof SwitchStatement) {
			SwitchStatement sw = (SwitchStatement) s;
			if(defaultTarget.getNumericId() != sw.defaultTarget.getNumericId() || !expression.equivalent(sw.expression)) {
				return false;
			}
			if(targets.size() != sw.targets.size()) {
				return false;
			}
			Map<Integer, BasicBlock> otherTargets = sw.targets;
			Set<Integer> keys = new HashSet<>();
			keys.addAll(targets.keySet());
			keys.addAll(otherTargets.keySet());

			// i.e. different keys
			if (keys.size() != targets.size()) {
				return false;
			}
			
			for(Integer key : keys) {
				if(targets.get(key).getNumericId() != otherTargets.get(key).getNumericId()) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
}