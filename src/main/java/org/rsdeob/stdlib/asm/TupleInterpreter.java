package org.rsdeob.stdlib.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;

import java.util.ArrayList;
import java.util.List;

public class TupleInterpreter<T1 extends Value, T2 extends Value, V extends TupleValue<T1, T2>> extends Interpreter<V> implements Opcodes {
	
	private final Interpreter<T1> i1;
	private final Interpreter<T2> i2;
	private final TupleCreationFactory<V, T1, T2> factory;
	
	public TupleInterpreter(Interpreter<T1> i1, Interpreter<T2> i2, TupleCreationFactory<V, T1, T2> factory) {
		super(ASM5);
		this.i1 = i1;
		this.i2 = i2;
		this.factory = factory;
	}

	@Override
	public V newValue(Type type) {
		T1 t1 = i1.newValue(type);
		T2 t2 = i2.newValue(type);
		if(t1 == null || t2 == null) {
			return null;
		}
		return factory.create(t1, t2);
	}

	@Override
	public V newOperation(AbstractInsnNode insn) throws AnalyzerException {
		T1 t1 = i1.newOperation(insn);
		T2 t2 = i2.newOperation(insn);
		return factory.create(t1, t2);
	}

	@Override
	public V copyOperation(AbstractInsnNode insn, V value) throws AnalyzerException {
		T1 t1 = i1.copyOperation(insn, value.getT1());
		T2 t2 = i2.copyOperation(insn, value.getT2());
		return factory.create(t1, t2);
	}

	@Override
	public V unaryOperation(AbstractInsnNode insn, V value) throws AnalyzerException {
		T1 t1 = i1.unaryOperation(insn, value.getT1());
		T2 t2 = i2.unaryOperation(insn, value.getT2());
		return factory.create(t1, t2);
	}

	@Override
	public V binaryOperation(AbstractInsnNode insn, V value1, V value2) throws AnalyzerException {
		T1 t1 = i1.binaryOperation(insn, value1.getT1(), value2.getT1());
		T2 t2 = i2.binaryOperation(insn, value1.getT2(), value2.getT2());
		return factory.create(t1, t2);
	}

	@Override
	public V ternaryOperation(AbstractInsnNode insn, V value1, V value2, V value3) throws AnalyzerException {
		T1 t1 = i1.ternaryOperation(insn, value1.getT1(), value2.getT1(), value3.getT1());
		T2 t2 = i2.ternaryOperation(insn, value1.getT2(), value2.getT2(), value3.getT2());
		return factory.create(t1, t2);
	}

	@Override
	public V naryOperation(AbstractInsnNode insn, List<? extends V> values) throws AnalyzerException {
		List<T1> t1values = new ArrayList<>();
		List<T2> t2values = new ArrayList<>();
		for(V e : values) {
			t1values.add(e.getT1());
			t2values.add(e.getT2());
		}
		T1 t1 = i1.naryOperation(insn, t1values);
		T2 t2 = i2.naryOperation(insn, t2values);
		return factory.create(t1, t2);
	}

	@Override
	public void returnOperation(AbstractInsnNode insn, V value, V expected) throws AnalyzerException {
		i1.returnOperation(insn, value.getT1(), expected.getT1());
		i2.returnOperation(insn, value.getT2(), expected.getT2());
	}

	@Override
	public V merge(V v, V w) {
		T1 t1 = i1.merge(v.getT1(), w.getT1());
		T2 t2 = i2.merge(v.getT2(), w.getT2());
		return factory.create(t1, t2);
	}
}