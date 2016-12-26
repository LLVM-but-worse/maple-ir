package org.mapleir.deobimpl2;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ArithmeticExpression;
import org.mapleir.ir.code.expr.CastExpression;
import org.mapleir.ir.code.expr.ConstantExpression;
import org.mapleir.ir.code.expr.FieldLoadExpression;
import org.mapleir.ir.code.stmt.FieldStoreStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.objectweb.asm.tree.MethodNode;

public class FieldRSADecryptionPass implements ICompilerPass, Opcode {

	@Override
	public String getId() {
		return "Field-Modulus-Pass";
	}


	Set<String> encrypted = new HashSet<>();
	
	NullPermeableHashMap<String, Set<Number>> encoders = new NullPermeableHashMap<>(new SetCreator<>());
	NullPermeableHashMap<String, Set<Number>> decoders = new NullPermeableHashMap<>(new SetCreator<>());
	
	NullPermeableHashMap<String, Set<Number>> cget = new NullPermeableHashMap<>(new SetCreator<>());
	NullPermeableHashMap<String, Set<Number>> cset = new NullPermeableHashMap<>(new SetCreator<>());
	
	static boolean field(Expr _e) {
		for(Expr e : _e.enumerateWithSelf()) {
			if(e.getOpcode() == FIELD_LOAD) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed) {
		
		for(MethodNode m : cxt.getActiveMethods()) {
			ControlFlowGraph cfg = cxt.getIR(m);
			
			for(BasicBlock b : cfg.vertices()) {
				for(Stmt stmt : b) {
					
					boolean b1 = false;
					
					for(Expr e : stmt.enumerateOnlyChildren()) {
						if(e.getOpcode() == ARITHMETIC) {
							ArithmeticExpression a = (ArithmeticExpression) e;
							
							if(a.getRight().getOpcode() == CONST_LOAD) {
								if(isIntField(a.getType().toString())) {
									Number n = getConstant(a, a.getType().toString().equals("J"));
									if(n != null) {
										System.out.println(a);
										
										if(a.toString().equals("[svar1_1 * 339378275]")) {
											Local v = cfg.getLocals().get(1, 1, true);
											System.out.println(">>> " + cfg.getLocals().defs.get(v));
											System.out.println(">> " + cfg.getLocals().uses.get(v));
											System.out.println(m);
										}
										b1 = true;
									}
								}
							}
						}
					}
					
					if(b1) {
						System.out.println(stmt);

						System.out.println();
						System.out.println();
					}
				}
			}
		}
		
		Set<String> keys = new HashSet<>();
		keys.addAll(encoders.keySet());
		keys.addAll(decoders.keySet());
				
//		for(String k : keys) {
//			boolean _longint = k.endsWith("J");
//			
//			Set<Number> encs = encoders.getNonNull(k);
//			Set<Number> decs = decoders.getNonNull(k);
//			
//			try {
//				Number[] pair = get_pair(encs, decs, _longint);
//				if(pair.length != 2) {
//					System.out.println("No pair for: " + k);
//					System.out.println(encs);
//					System.out.println(decs);
//				}
//			} catch(IllegalStateException e) {
//				System.err.println();
//				System.err.println(encs);
//				System.err.println(decs);
//				System.err.println(k);
//				throw e;
//			}
////			if(k.equals("dl.af I")) {
////				
////			}
//		}
	}
	
	static Expr reduceCasts(Expr e) {
		while(e.getOpcode() == CAST) {
			CastExpression c = (CastExpression) e;
			e = c.getExpression();
		}
		return e;
	}
	
	static boolean isIntField(String desc) {
		return desc.equals("I") || desc.equals("J");
	}
	
	static boolean containsConstant(ArithmeticExpression ae) {
		Expr r = ae.getRight();
		Expr l = ae.getLeft();
		
		boolean rc = r.getOpcode() == CONST_LOAD;
		boolean lc = l.getOpcode() == CONST_LOAD;
		
		return rc || lc;
	}

	static Number getConstant(ArithmeticExpression ae, boolean _longint) {		
		Number n = getConstant0(ae, _longint);
		if(n != null && large(n, _longint)) {
			return n;
		} else {
			return null;
		}
	}
	
	static Number getConstant0(ArithmeticExpression ae, boolean _longint) {		
		Expr r = ae.getRight();
		Expr l = ae.getLeft();
		
		boolean rc = r.getOpcode() == CONST_LOAD;
		boolean lc = l.getOpcode() == CONST_LOAD;
		
		if(rc || lc) {
			if(!lc) {
				ConstantExpression c = (ConstantExpression) r;
				return (Number) c.getConstant();
			} else if(!rc) {
				ConstantExpression c = (ConstantExpression) l;
				return (Number) c.getConstant();
			} else {
				// both constant, one should be a
				// multi const the other may be
				// a normal one.
				

				ConstantExpression cr = (ConstantExpression) r;
				ConstantExpression cl = (ConstantExpression) l;
				
				Number nr = (Number) cr.getConstant();
				Number nl = (Number) cl.getConstant();
				
				boolean rl = large(nr, _longint);
				boolean ll = large(nl, _longint);
				
				if(rl || ll) {
					if(!ll) {
						return nr;
					} else if(!rl) {
						return nl;
					} else {
						throw new UnsupportedOperationException("Const expr? : " + ae);
					}
				} else {
					// throw new UnsupportedOperationException("Non multi const expr: " + ae);
				}
			}
		} else {
			throw new UnsupportedOperationException("No consts: " + ae);
		}
		
		return null;
	}
	
	static String key(FieldStoreStatement fss) {
		return fss.getOwner() + "." + fss.getName() + " " + fss.getDesc();
	}
	
	static String key(FieldLoadExpression fle) {
		return fle.getOwner() + "." + fle.getName() + " " + fle.getDesc();
	}
	
	static Number __mul(Number n1, Number n2, boolean _longint) {
		if(_longint) {
			return (long) n1.longValue() * n2.longValue();
		} else {
			return (int) n1.intValue() * n2.intValue();
		}
	}
	
	static boolean __eq(Number n1, Number n2, boolean _longint) {
		if(_longint) {
			return n1.longValue() == n2.longValue();
		} else {
			return n1.intValue() == n2.intValue();
		}
	}
	
	static boolean __lt(Number n1, Number n2, boolean _longint) {
		if(_longint) {
			return Math.abs(n1.longValue()) < Math.abs(n2.longValue());
		} else {
			return Math.abs(n1.intValue()) < Math.abs(n2.intValue());
		}
	}
	
	static void assert_inv(Number s1, Number s2, boolean _longint) {
		if(_longint) {
			long r = (long) s1.longValue() * s2.longValue();
			if(r != 1L) {
				throw new IllegalStateException(String.format("s1 * s2 != 1 (s1:%s, s2:%s, r:%d)", s1, s2, r));
			}
		} else {
			int r = (int) s1.intValue() * s2.intValue();
			if(r != 1) {
				throw new IllegalStateException(String.format("s1 * s2 != 1 (s1:%s, s2:%s, r:%d)", s1, s2, r));
			}
		}
	}
	
	public static Number[] get_pair(Collection<Number> encs, Collection<Number> decs, boolean _longint) {
		List<Number> all = new ArrayList<>();
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
		
		Number smallest = 0;
		Number c1 = 0, c2 = 0;
		for(Number p : all) {
			for(Number q : all) {					
				/* find the closest inverse product to 1*/
				Number r = __mul(p, q, _longint);
				if(__eq(p, 0, _longint) || __eq(q, 0, _longint)  || __eq(r, 0, _longint)) {
					continue;
				}
				
				if(__eq(smallest, 0, _longint) /* no result yet*/ || __eq(r, 1, _longint) || __lt(r, smallest, _longint) /* found a new smaller one */) {
					c1 = p;
					c2 = q;
					smallest = r;
				}
			}
		}

		if(!__eq(smallest, 1, _longint)) {
			if(valid(smallest, _longint)) {
				if(valid(c1, _longint)) {
					c2 = invert(smallest, c2, _longint);
					assert_inv(c1, c2, _longint);
				} else if(valid(c2, _longint)) {
					c1 = invert(smallest, c1, _longint);
					assert_inv(c1, c2, _longint);
				} else {
					/* can't. */
					return new Number[0];
				}
			} else {
				if(valid(c1, _longint)) {
					Number is1 = asNumber(inverse(asBigInt(c1, _longint), _longint), _longint);
					if(__eq(__mul(is1, smallest, _longint), c2, _longint)) {
						c2 = is1;
						assert_inv(c1, c2, _longint);
					}
				} else if(valid(c2, _longint)) {
					Number is2 = asNumber(inverse(asBigInt(c2, _longint), _longint), _longint);
					if(__eq(__mul(is2, smallest, _longint), c1, _longint)) {
						c1 = is2;
						assert_inv(c1, c2, _longint);
					}
				} else {
					/* can't. */
					return new Number[0];
				}
			}
		}
		
		/* find out which one is the
		 * encoder and which is the
		 * decoder. */
		boolean b1 = resolves(decs, c1, _longint);
		boolean b2 = resolves(decs, c2, _longint);
		
		boolean b3;
		
		if(b1 == b2) {
			b3 = true;
			
			b1 = resolves(encs, c1, _longint);
			b2 = resolves(encs, c2, _longint);
		} else {
			b3 = false;
		}
		
		if(b1 != b2) {
			Number enc, dec;
			if(b1 != b3) {
				enc = c2;
				dec = c1;
			} else {
				enc = c1;
				dec = c2;
			}
			return new Number[] {enc, dec};
		} else {
			return new Number[0];
		}
	}
	
	static BigInteger asBigInt(Number n, boolean _longint) {
		return BigInteger.valueOf(_longint ? n.longValue() : n.intValue());
	}
	
	static Number asNumber(BigInteger n, boolean _longint) {
		if(_longint) {
			return n.longValue();
		} else {
			return n.intValue();
		}
	}
	
	public static BigInteger inverse(BigInteger v, boolean _longint) {
		return v.modInverse(BigInteger.ONE.shiftLeft(_longint ? 64 : 32));
	}
	
	static boolean valid(Number n, boolean _longint) {
		try {
			inverse(asBigInt(n, _longint), _longint);
			return true;
		} catch(ArithmeticException e) {
			return false;
		}
	}
	
	static Number invert(Number smallest, Number c, boolean _longint) {
		if(_longint) {
			return (int) inverse(asBigInt(smallest, _longint), _longint).intValue() * c.intValue();
		} else {
			return (long) inverse(asBigInt(smallest, _longint), _longint).longValue() * c.longValue();
		}
	}
	
	static boolean resolves(Collection<Number> multis, Number i, boolean _longint) {
		if(multis.contains(i)) {
			return true;
		} else {
			BigInteger inv = inverse(asBigInt(i, _longint), _longint);
			for(Number m : multis) {
				Number r = __mul(asNumber(inv, _longint), m, _longint);
				
				if(!large(r, _longint)) {
					return true;
				}
			}
			return false;
		}
	}
	
	static boolean large(Number n, boolean _longint) {
		if(_longint) {
			long v = n.longValue();
			if ((v & 0x8000000000000000L) != 0L) {
				v = ~v + 1L;
			}
			
			return (v & 0x7FF0000000000000L) != 0L;
		} else {
			int v = n.intValue();
			if ((v & 0x80000000) != 0) {
				v = ~v + 1;
			}

			return (v & 0x7FF00000) != 0;
		}
	}
}