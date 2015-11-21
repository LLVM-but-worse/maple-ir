package me.polishcivi.cfg.graph.bytecode;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicLong;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Created by polish on 21.11.15.
 */
public class InstructionVertex {
	private static final AtomicLong INSTRUCTION_UID = new AtomicLong();

	private final AbstractInsnNode instruction;
	private final long uid;

	public InstructionVertex(AbstractInsnNode instruction) {
		this.instruction = instruction;
		this.uid = INSTRUCTION_UID.incrementAndGet();
	}

	public AbstractInsnNode getInstruction() {
		return this.instruction;
	}

	public long getUID() {
		return this.uid;
	}

	@Override
	public String toString() {
		TraceMethodVisitor traceMethodVisitor = new TraceMethodVisitor(new Textifier());
		StringWriter out = new StringWriter();
		this.instruction.accept(traceMethodVisitor);
		traceMethodVisitor.p.print(new PrintWriter(out));
		return this.getUID() + " - " + out.toString().replace("\n", "\\l").replaceAll("\"", "'").trim();
	}
}
