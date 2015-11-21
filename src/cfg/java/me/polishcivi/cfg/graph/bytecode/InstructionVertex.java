package me.polishcivi.cfg.graph.bytecode;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by polish on 21.11.15.
 */
public class InstructionVertex {
    private static final AtomicLong INSTRUCTION_UID = new AtomicLong();

    private final AbstractInsnNode instruction;
    private final long uid;

    /**
     * @param instruction
     */
    public InstructionVertex(AbstractInsnNode instruction) {
        this.instruction = instruction;
        this.uid = INSTRUCTION_UID.incrementAndGet();
    }

    /**
     * @return
     */
    public AbstractInsnNode getInstruction() {
        return this.instruction;
    }

    /**
     * @return
     */
    public long getUID() {
        return this.uid;
    }

    @Override
    public String toString() {
        final TraceMethodVisitor traceMethodVisitor = new TraceMethodVisitor(new Textifier());
        final StringWriter out = new StringWriter();
        this.instruction.accept(traceMethodVisitor);
        traceMethodVisitor.p.print(new PrintWriter(out));
        return this.getUID() + " - " + out.toString().replace("\n", "\\l").replaceAll("\"", "'").trim();
    }
}
