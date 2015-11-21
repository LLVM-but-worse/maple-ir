package me.polishcivi.cfg.graph.basicblock;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by polish on 21.11.15.
 */
public class BasicBlockVertex {
    private static final AtomicLong BASIC_BLOCK_UID = new AtomicLong();

    private final LinkedList<AbstractInsnNode> instructions = new LinkedList<>();
    private final long uid;

    /**
     */
    public BasicBlockVertex() {
        this.uid = BASIC_BLOCK_UID.incrementAndGet();
    }

    /**
     * @return
     */
    public LinkedList<AbstractInsnNode> getInstructions() {
        return this.instructions;
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

        for (AbstractInsnNode instruction : instructions) {
            instruction.accept(traceMethodVisitor);
        }

        traceMethodVisitor.p.print(new PrintWriter(out));
        return this.getUID() + " - " + out.toString().replace("\n", "\\l").replaceAll("\"", "'").trim();
    }
}
