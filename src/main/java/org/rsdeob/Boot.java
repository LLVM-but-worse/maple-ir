package org.rsdeob;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.objectweb.asm.tree.ClassNode;
import org.rsdeob.byteio.CompleteResolvingJarDumper;
import org.rsdeob.deobimpl.ConstantComparisonReordererPhase;
import org.rsdeob.deobimpl.ConstantOperationReordererPhase;
import org.rsdeob.deobimpl.EmptyParameterFixerPhase;
import org.rsdeob.deobimpl.OldschoolDummyMethodPhase;
import org.rsdeob.deobimpl.OpaquePredicateRemoverPhase;
import org.rsdeob.deobimpl.RTECatchBlockRemoverPhase;
import org.rsdeob.deobimpl.UnusedFieldsPhase;
import org.rsdeob.stdlib.IContext;
import org.rsdeob.stdlib.collections.NodeTable;
import org.rsdeob.stdlib.deob.IPhase;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

public class Boot {

	public static void main(String[] args) throws IOException {
		IPhase[] phases = loadPhases();
		if(phases.length <= 0) {
			System.err.println("No passes to complete.");
			return;
		}
		
		int rev = 104;
		if(args.length > 0) {
			rev = Integer.parseInt(args[0]);
		}
		
		NodeTable<ClassNode> nt = new NodeTable<ClassNode>();
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<ClassNode>(new JarInfo(new File(String.format("res/gamepack%s.jar", rev))));
		dl.download();
		nt.putAll(dl.getJarContents().getClassContents().namedMap());
		IContext cxt = new IContext() {
			@Override
			public NodeTable<ClassNode> getNodes() {
				return nt;
			}
		};
		
		List<IPhase> completed = new ArrayList<IPhase>();
		IPhase prev = null;
		for(IPhase p : phases) {
			System.out.println("Running " + p.getId());
			try {
				p.accept(cxt, prev, Collections.unmodifiableList(completed));
				prev = p;
				completed.add(p);
				System.out.println("Completed " + p.getId());
			} catch(RuntimeException e) {
				System.err.println("Error: " + p.getId());
				System.err.flush();
				e.printStackTrace(System.err);
				prev = null;
			}
		}
		
		CompleteResolvingJarDumper dumper = new CompleteResolvingJarDumper(dl.getJarContents());
		File outFile = new File(String.format("out/%d/%d.jar", rev, rev));
		outFile.mkdirs();
		dumper.dump(outFile);
	}

	private static IPhase[] loadPhases() {
		return new IPhase[] { new OldschoolDummyMethodPhase(), new UnusedFieldsPhase(), new RTECatchBlockRemoverPhase(), new ConstantComparisonReordererPhase(), new OpaquePredicateRemoverPhase(), new EmptyParameterFixerPhase(), new ConstantOperationReordererPhase() };
	}
}