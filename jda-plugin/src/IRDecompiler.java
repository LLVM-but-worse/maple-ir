import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import the.bytecode.club.jda.decompilers.Decompiler;

public class IRDecompiler extends Decompiler {
	@Override
	public String decompileClassNode(ClassNode cn, byte[] b) {
		if (cn.methods.isEmpty())
			return "";
		ControlFlowGraph cfg = ControlFlowGraphBuilder.build(cn.methods.iterator().next());
		return cfg.toString();
	}
	
	@Override
	public void decompileToZip(String zipName) {
	}
	
	@Override
	public String getName() {
		return "MapleIR";
	}
}
