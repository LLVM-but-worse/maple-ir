package org.rsdeob.stdlib.cfg.stat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.expr.Expression;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

public class SwitchStatement extends Statement {

	private Expression expression;
	private LinkedHashMap<Integer, BasicBlock> targets;
	private BasicBlock defaultTarget;

	public SwitchStatement(Expression expr, LinkedHashMap<Integer, BasicBlock> targets, BasicBlock defaultTarget) {
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
		if (targets.size() < 1) {
			return false;
		}
		
		Iterator<Integer> it = targets.keySet().iterator();
		int last = it.next();
		while(it.hasNext()) {
			int i = it.next();
			if(i != (last + 1)) {
				return true;
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
	public void toCode(MethodVisitor visitor) {
		if (needsSort()) {
			sort();
		}

		int[] cases = new int[targets.size()];
		Label[] labels = new Label[targets.size()];
		int j = 0;
		for (Entry<Integer, BasicBlock> e : targets.entrySet()) {
			cases[j] = e.getKey();
			labels[j++] = e.getValue().getLabel().getLabel();
		}

		expression.toCode(visitor);
		int[] cast = TypeUtils.getPrimitiveCastOpcodes(expression.getType(), Type.INT_TYPE); // widen
		for (int i = 0; i < cast.length; i++) {
			visitor.visitInsn(cast[i]);
		}
		boolean fitsIntoTable = fitsIntoTableSwitch();
		if (fitsIntoTable) {
			visitor.visitTableSwitchInsn(cases[0], cases[cases.length - 1], defaultTarget.getLabel().getLabel(), labels);
		} else {
			visitor.visitLookupSwitchInsn(defaultTarget.getLabel().getLabel(), cases, labels);
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
}