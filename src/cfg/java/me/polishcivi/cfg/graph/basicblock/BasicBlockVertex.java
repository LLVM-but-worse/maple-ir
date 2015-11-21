package me.polishcivi.cfg.graph.basicblock;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Created by polish on 21.11.15.
 */
public class BasicBlockVertex {
	private static final AtomicLong BASIC_BLOCK_UID = new AtomicLong();

	private final LinkedList<AbstractInsnNode> instructions = new LinkedList<>();
	private final long uid;

	public BasicBlockVertex() {
		this.uid = BASIC_BLOCK_UID.incrementAndGet();
	}

	public LinkedList<AbstractInsnNode> getInstructions() {
		return this.instructions;
	}

	public long getUID() {
		return this.uid;
	}

	@Override
	public String toString() {
		TraceMethodVisitor traceMethodVisitor = new TraceMethodVisitor(new Textifier());
		StringWriter out = new StringWriter();
		for (AbstractInsnNode instruction : instructions) {
			instruction.accept(traceMethodVisitor);
		}
		traceMethodVisitor.p.print(new PrintWriter(out));
		return this.getUID() + " - " + out.toString().replace("\n", "\\l").replaceAll("\"", "'").trim();
	}
}