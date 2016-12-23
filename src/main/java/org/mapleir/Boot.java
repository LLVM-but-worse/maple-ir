package org.mapleir;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.Map.Entry;

import org.mapleir.byteio.CompleteResolvingJarDumper;
import org.mapleir.deobimpl2.CallgraphPruningPass;
import org.mapleir.deobimpl2.ConstantExpressionReorderPass;
import org.mapleir.deobimpl2.ConstantParameterPass;
import org.mapleir.ir.ControlFlowGraphDumper;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ArithmeticExpression;
import org.mapleir.ir.code.expr.ArithmeticExpression.Operator;
import org.mapleir.ir.code.expr.ConstantExpression;
import org.mapleir.ir.code.expr.FieldLoadExpression;
import org.mapleir.ir.code.stmt.FieldStoreStatement;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.call.CallTracer;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.mapleir.stdlib.klass.ClassTree;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;
import org.topdank.byteio.out.JarDumper;

public class Boot {

	private static Map<MethodNode, ControlFlowGraph> cfgs;
	private static long timer;
	private static Deque<String> sections;
	
	private static double lap() {
		long now = System.nanoTime();
		long delta = now - timer;
		timer = now;
		return (double)delta / 1_000_000_000L;
	}
	
	private static void section0(String endText, String sectionText) {
		if(sections.isEmpty()) {
			lap();
			System.out.println(sectionText);
		} else {
			/* remove last section. */
			sections.pop();
			System.out.printf(endText, lap());
			System.out.println("\n" + sectionText);
		}

		/* push the new one. */
		sections.push(sectionText);
	}
	
	private static void section(String text) {
		section0("...took %fs.%n", text);
	}
	
	static BigInteger inverse(BigInteger v) {
		return v.modInverse(BigInteger.ONE.shiftLeft(32));
	}
	
	static boolean large(int v) {
		if ((v & 0x80000000) != 0) {
			v = ~v + 1;
		}

		return (v & 0x7FF00000) != 0;
	}
	
	static boolean eq(BigInteger v, int c) {
		return v.intValue() == c;
	}
	
	static boolean valid(int i) {
		try {
			inverse(BigInteger.valueOf(i));
			return true;
		} catch(ArithmeticException e) {
			return false;
		}
	}
	
	static boolean resolves(List<Integer> multis, int i) {
		if(multis.contains(i)) {
			return true;
		} else {
			BigInteger inv = inverse(BigInteger.valueOf(i));
			for(Integer m : multis) {
				int r = (int) inv.intValue() * m;
				
				if(!large(r)) {
					return true;
				}
			}
			return false;
		}
	}
	
	static void assert_inv(int s1, int s2) {
		if((int)s1 * s2 != 1) {
			throw new IllegalStateException(String.format("s1 * s2 != 1 (s1:%d, s2:%d, r:%d)", s1, s2, (int)(s1*s2)));
		}
	}
	
	static int invert(int smallest, int c) {
		return (int) inverse(BigInteger.valueOf(smallest)).intValue() * c;
	}
	
	static int[] get_pair(List<Integer> encs, List<Integer> decs) {
		List<Integer> all = new ArrayList<>();
		all.addAll(encs);
		all.addAll(decs);
		
		/* p = encoder, c = const
		 * q = decoder, d = const
		 * 
		 * cp * dq (mod 2^32) == cd
		 * 
		 * to solve for p and q, find the closest, non zero
		 * value, to 1 for cp * dq (mod 2^32) where either c
		 * or d are 1.
		 */
		
		int smallest = 0, c1 = 0, c2 = 0;
		for(int p : all) {
			for(int q : all) {					
				/* find the closest inverse product to 1*/
				int r = (int) p * q;
				if(p == 0 || q == 0  || r == 0) {
					continue;
				}
				
				if(smallest == 0 /* no result yet*/ || r == 1 || Math.abs((int) r) < Math.abs((int) smallest) /* found a new smaller one */) {
					c1 = p;
					c2 = q;
					smallest = r;
				}
			}
		}

		if(smallest != 1) {
			if(valid(smallest)) {
				if(valid(c1)) {
					c2 = invert(smallest, c2);
					assert_inv(c1, c2);
				} else if(valid(c2)) {
					c1 = invert(smallest, c1);
					assert_inv(c1, c2);
				} else {
					/* can't. */
					return new int[0];
				}
			} else {
				if(valid(c1)) {
					int is1 = inverse(BigInteger.valueOf(c1)).intValue();
					if(is1 * smallest == c2) { 
						c2 = is1;
						assert_inv(c1, c2);
					}
				} else if(valid(c2)) {
					int is2 = inverse(BigInteger.valueOf(c2)).intValue();
					if(is2 * smallest == c1) {
						c1 = is2;
						assert_inv(c1, c2);
					}
				} else {
					return new int[0];
				}
			}
		}
		
		boolean b1 = resolves(decs, c1);
		boolean b2 = resolves(decs, c2);
		
		boolean b3;
		
		if(b1 == b2) {
			b3 = true;
			
			b1 = resolves(encs, c1);
			b2 = resolves(encs, c2);
		} else {
			b3 = false;
		}
		
		if(b1 != b2) {
			int enc, dec;
			if(b1 != b3) {
				enc = c2;
				dec = c1;
			} else {
				enc = c1;
				dec = c2;
			}
			return new int[] {enc, dec};
		} else {
			return new int[0];
		}
	}
	
	public static void main(String[] args) throws IOException {		
		List<Integer> encs = new ArrayList<>();
		encs.add(32444200);
		List<Integer> decs = new ArrayList<>();
		decs.add(495743981);
		decs.add(1982975924);
		
		int[] arr = get_pair(encs, decs);
		System.out.println(Arrays.toString(arr));
		
		if("".equals("")) {
			return;
		}
		
		cfgs = new HashMap<>();
		sections = new LinkedList<>();
		/* if(args.length < 1) {
			System.err.println("Usage: <rev:int>");
			System.exit(1);
			return;
		} */
		
		File f = locateRevFile(129);
		
		section("Preparing to run on " + f.getAbsolutePath());
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		dl.download();
		
		section("Building jar class hierarchy.");
		ClassTree tree = new ClassTree(dl.getJarContents().getClassContents());
		
		section("Initialising context.");

		InvocationResolver resolver = new InvocationResolver(tree);
		IContext cxt = new IContext() {
			@Override
			public ClassTree getClassTree() {
				return tree;
			}

			@Override
			public ControlFlowGraph getIR(MethodNode m) {
				if(cfgs.containsKey(m)) {
					return cfgs.get(m);
				} else {
					ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
					cfgs.put(m, cfg);
					return cfg;
				}
			}

			@Override
			public Set<MethodNode> getActiveMethods() {
				return cfgs.keySet();
			}

			@Override
			public InvocationResolver getInvocationResolver() {
				return resolver;
			}
		};
		
		section("Expanding callgraph and generating cfgs.");
		CallTracer tracer = new IRCallTracer(cxt) {
			@Override
			protected void processedInvocation(MethodNode caller, MethodNode callee, Expr call) {
				/* the cfgs are generated by calling IContext.getIR()
				 * in IRCallTracer.traceImpl(). */
			}
		};
		for(MethodNode m : findEntries(tree)) {
			tracer.trace(m);
		}
		
		section0("...generated " + cfgs.size() + " cfgs in %fs.%n", "Preparing to transform.");
		
		runPasses(cxt, getTransformationPasses());
		
		for(MethodNode m : cxt.getActiveMethods()) {
			ControlFlowGraph cfg = cfgs.get(m);
			
			for(BasicBlock b : cfg.vertices()) {
				for(Stmt stmt : b) {
					for(Expr e : stmt.enumerateOnlyChildren()) {
						if(e.getOpcode() == Opcode.ARITHMETIC) {
							ArithmeticExpression ae = (ArithmeticExpression) e;
							if(ae.getOperator() == Operator.MUL) {
								Expr l = ae.getLeft();
								Expr r = ae.getRight();
								
								if(r.getOpcode() == Opcode.CONST_LOAD && l.getOpcode() == Opcode.FIELD_LOAD) {
									FieldLoadExpression fl = (FieldLoadExpression) l;
									if(fl.getOwner().equals("r") && fl.getName().equals("dd")) {
										System.out.println("Decoder: " + r);
									}
								}
							}
						}
					}
					
					if(stmt.getOpcode() == Opcode.FIELD_STORE) {
						FieldStoreStatement fs = (FieldStoreStatement) stmt;
						if(fs.getOwner().equals("r") && fs.getName().equals("dd")) {
							ArithmeticExpression ae = (ArithmeticExpression) fs.getValueExpression();
							System.out.println("Encoder: " + ((ConstantExpression) ae.getRight()));
						}
					}
				}
			}
		}
		
		section("Retranslating SSA IR to standard flavour.");
		for(Entry<MethodNode, ControlFlowGraph> e : cfgs.entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			
			BoissinotDestructor.leaveSSA(cfg);
			cfg.getLocals().realloc(cfg);
			ControlFlowGraphDumper.dump(cfg, mn);
		}
		
		section("Rewriting jar.");
		JarDumper dumper = new CompleteResolvingJarDumper(dl.getJarContents());
		dumper.dump(new File("out/osb.jar"));
		
		section("Finished.");
	}
	
	private static void runPasses(IContext cxt, ICompilerPass[] passes) {
		List<ICompilerPass> completed = new ArrayList<>();
		ICompilerPass last = null;
		
		for(int i=0; i < passes.length; i++) {
			ICompilerPass p = passes[i];
			section0("...took %fs." + (i == 0 ? "%n" : ""), "Running " + p.getId());
			p.accept(cxt, last, completed);
			
			completed.add(p);
			last = p;
		}
	}
	
	private static ICompilerPass[] getTransformationPasses() {
		return new ICompilerPass[] {
				new CallgraphPruningPass(),
				new ConstantParameterPass(),
				new ConstantExpressionReorderPass()
		};
	}
	
	private static File locateRevFile(int rev) {
		return new File("res/gamepack" + rev + ".jar");
	}
	
	private static Set<MethodNode> findEntries(ClassTree tree) {
		Set<MethodNode> set = new HashSet<>();
		for(ClassNode cn : tree.getClasses().values())  {
			for(MethodNode m : cn.methods) {
				if(m.name.length() > 2) {
					set.add(m);
				}
			}
		}
		return set;
	}
}