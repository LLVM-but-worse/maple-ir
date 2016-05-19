package org.rsdeob.stdlib.cfg;

import java.util.List;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.util.Printer;
import org.rsdeob.stdlib.cfg.util.LabelHelper;

public abstract class FlowEdge {

	public static abstract class InverseFlowEdge extends FlowEdge {
		protected InverseFlowEdge(BasicBlock src, BasicBlock dst) {
			super(src, dst);
		}
		
		@Override
		public FlowEdge getInverse() {
			throw new UnsupportedOperationException("Inverse of inverse? Use the real edge...");
		}
	}
	
	public final BasicBlock src;
	public final BasicBlock dst;
	private final InverseFlowEdge inverse;
	
	public FlowEdge(BasicBlock src, BasicBlock dst, InverseFlowEdge inverse) {
		this.src = src;
		this.dst = dst;
		this.inverse = inverse;
	}
	
	protected FlowEdge(BasicBlock src, BasicBlock dst) {
		this(src, dst, null);
	}
	
	public FlowEdge getInverse() {
		return inverse;
	}
	
	public abstract String toGraphString();
	
	@Override
	public abstract String toString();
	
	public abstract FlowEdge clone(BasicBlock src, BasicBlock dst);
	
	public static class TryCatchEdge extends FlowEdge {

		public static class InverseTryCatchEdge extends InverseFlowEdge {
			public final ExceptionRange erange;
			protected InverseTryCatchEdge(BasicBlock src, BasicBlock dst, ExceptionRange erange) {
				super(src, dst);
				this.erange = erange;
			}

			@Override
			public String toGraphString() {
				return "TODO";
			}

			@Override
			public String toString() {
				return String.format("TryCatch handler: %s <- range: %s from %s (%s)", dst.getId(), rangetoString(erange.getBlocks()), src.getId(), erange.getTypes());
			}

			@Override
			public InverseTryCatchEdge clone(BasicBlock src, BasicBlock dst) {
				return new InverseTryCatchEdge(src, dst, erange);
			}
		}
		
		public final ExceptionRange erange;
		private int hashcode;
		
		public TryCatchEdge(BasicBlock src, ExceptionRange erange) {
			super(src, erange.getHandler(), new InverseTryCatchEdge(src, erange.getHandler(), erange));
			this.erange = erange;
			recalcHashcode();
		}
		
		private void recalcHashcode() {
			hashcode = 31 + (erange == null ? 0 : erange.hashCode());
			hashcode += (src.getId() + " " + dst.getId()).hashCode();
		}

		@Override
		public String toGraphString() {
			return "Handler";
		}

		@Override
		public String toString() {
			return String.format("TryCatch range: %s -> handler: %s (%s)", rangetoString(erange.getBlocks()), dst.getId(), erange.getTypes());
		}

		@Override
		public TryCatchEdge clone(BasicBlock src, BasicBlock dst) {
			return new TryCatchEdge(src, erange);
		}
		
		public static String rangetoString(List<BasicBlock> set) {
			int last = LabelHelper.numeric(set.get(0).getId()) - 1;
			for(int i=0; i < set.size(); i++) {
				int num = LabelHelper.numeric(set.get(i).getId());
				if((last + 1) == num) {
					last++;
					continue;
				} else {
					return set.toString();
				}
			}
			
			return String.format("[#%s...#%s]", set.get(0).getId(), LabelHelper.createBlockName(last));
		}

		@Override
		public int hashCode() {
			return hashcode;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TryCatchEdge other = (TryCatchEdge) obj;
			if (erange == null) {
				if (other.erange != null)
					return false;
			} else if (!erange.equals(other.erange))
				return false;
			return true;
		}
	}
	
	@Deprecated
	public static class ExitEdge extends FlowEdge {
		public static class InverseExitEdge extends InverseFlowEdge {

			protected InverseExitEdge(BasicBlock src, BasicBlock dst) {
				super(src, dst);
			}

			@Override
			public String toString() {
				return String.format("Exit #%s <- #%s", src.getId(), dst.getId());
			}
			
			@Override
			public String toGraphString() {
				return "TODO";
			}

			@Override
			public InverseExitEdge clone(BasicBlock src, BasicBlock dst) {
				return new InverseExitEdge(dst, src);
			}
		}
		
		public ExitEdge(BasicBlock src, BasicBlock dst) {
			super(src, dst, new InverseExitEdge(dst, src));
		}

		@Override
		public String toGraphString() {
			return "Exit";
		}

		@Override
		public String toString() {
			return String.format("Exit #%s -> #%s", src.getId(), dst.getId());
		}

		@Override
		public ExitEdge clone(BasicBlock src, BasicBlock dst) {
			return new ExitEdge(src, dst);
		}
	}
	
	public static class FakeConnectorEdge extends ImmediateEdge {

		public static class InverseFakeConnectorEdge extends InverseFlowEdge {

			protected InverseFakeConnectorEdge(BasicBlock src, BasicBlock dst) {
				super(src, dst);
			}

			@Override
			public String toString() {
				return String.format("FakeImmediate #%s <- #%s", src.getId(), dst.getId());
			}
			
			@Override
			public String toGraphString() {
				return "TODO";
			}

			@Override
			public InverseImmediateEdge clone(BasicBlock src, BasicBlock dst) {
				return new InverseImmediateEdge(dst, src);
			}
		}
		
		public FakeConnectorEdge(BasicBlock src, BasicBlock dst) {
			super(src, dst, new InverseFakeConnectorEdge(dst, src));
		}

		@Override
		public String toGraphString() {
			return "FakeImmediate";
		}

		@Override
		public String toString() {
			return String.format("FakeImmediate #%s -> #%s", src.getId(), dst.getId());
		}

		@Override
		public FakeConnectorEdge clone(BasicBlock src, BasicBlock dst) {
			return new FakeConnectorEdge(src, dst);
		}
	}
	
	public static class ImmediateEdge extends FlowEdge {

		public static class InverseImmediateEdge extends InverseFlowEdge {

			protected InverseImmediateEdge(BasicBlock src, BasicBlock dst) {
				super(src, dst);
			}

			@Override
			public String toString() {
				return String.format("Immediate #%s <- #%s", src.getId(), dst.getId());
			}
			
			@Override
			public String toGraphString() {
				return "TODO";
			}

			@Override
			public InverseImmediateEdge clone(BasicBlock src, BasicBlock dst) {
				return new InverseImmediateEdge(dst, src);
			}
		}
		
		public ImmediateEdge(BasicBlock src, BasicBlock dst) {
			super(src, dst, new InverseImmediateEdge(dst, src));
		}

		public ImmediateEdge(BasicBlock src, BasicBlock dst, InverseFlowEdge inverse) {
			super(src, dst, inverse);
		}

		@Override
		public String toGraphString() {
			return "Immediate";
		}

		@Override
		public String toString() {
			return String.format("Immediate #%s -> #%s", src.getId(), dst.getId());
		}

		@Override
		public ImmediateEdge clone(BasicBlock src, BasicBlock dst) {
			return new ImmediateEdge(src, dst);
		}
	}
	
	public static class SwitchEdge extends FlowEdge {
		public static class InverseSwitchEdge extends InverseFlowEdge {
			public final int value;
			protected InverseSwitchEdge(BasicBlock src, BasicBlock dst, int value) {
				super(src, dst);
				this.value = value;
			}
			@Override
			public String toString() {
				return String.format("Switch[%d] #%s <- #%s", value, src.getId(), dst.getId());
			}
			
			@Override
			public String toGraphString() {
				return "TODO";
			}

			@Override
			public InverseSwitchEdge clone(BasicBlock src, BasicBlock dst) {
				return new InverseSwitchEdge(dst, src, value);
			}
		}
		
		public final AbstractInsnNode insn;
		public final int value;
		
		public SwitchEdge(BasicBlock src, BasicBlock dst, AbstractInsnNode insn, int value) {
			super(src, dst, new InverseSwitchEdge(dst, src, value));
			this.insn = insn;
			this.value = value;
		}
		
		public SwitchEdge(BasicBlock src, BasicBlock dst, InverseFlowEdge inverse, AbstractInsnNode insn, int value) {
			super(src, dst, inverse);
			this.insn = insn;
			this.value = value;
		}
		
		@Override
		public String toGraphString() {
			return "Case: " + value;
		}
		
		@Override
		public String toString() {
			return String.format("Switch[%d] #%s -> #%s", value, src.getId(), dst.getId());
		}

		@Override
		public SwitchEdge clone(BasicBlock src, BasicBlock dst) {
			return new SwitchEdge(src, dst, insn, value);
		}
	}
	
	public static class DefaultSwitchEdge extends SwitchEdge {

		public static class InverseDefaultSwitchEdge extends InverseFlowEdge {
			protected InverseDefaultSwitchEdge(BasicBlock src, BasicBlock dst) {
				super(src, dst);
			}

			@Override
			public String toString() {
				return String.format("Default Switch #%s <- #%s", src.getId(), dst.getId());
			}
			
			@Override
			public String toGraphString() {
				return "Default";
			}

			@Override
			public FlowEdge clone(BasicBlock src, BasicBlock dst) {
				return new InverseDefaultSwitchEdge(dst, src);
			}
		}
		
		public DefaultSwitchEdge(BasicBlock src, BasicBlock dst, AbstractInsnNode insn) {
			super(src, dst, new InverseDefaultSwitchEdge(dst, src), insn, 0);
		}
		
		@Override
		public String toString() {
			return String.format("Default Switch #%s -> #%s", src.getId(), dst.getId());
		}

		@Override
		public SwitchEdge clone(BasicBlock src, BasicBlock dst) {
			return new DefaultSwitchEdge(src, dst, insn);
		}
	}
	
	public abstract static class JumpEdge extends FlowEdge {
		
		public static class InverseJumpEdge extends InverseFlowEdge {
			private final JumpInsnNode jump;
			protected InverseJumpEdge(BasicBlock src, BasicBlock dst, JumpInsnNode jump) {
				super(src, dst);
				this.jump = jump;
			}
			@Override
			public String toString() {
				return String.format("Jump[%s] #%s <- #%s", Printer.OPCODES[jump.opcode()], src.getId(), dst.getId());
			}
			
			@Override
			public String toGraphString() {
				return "TODO";
			}
			
			@Override
			public FlowEdge clone(BasicBlock src, BasicBlock dst) {
				return new InverseJumpEdge(dst, src, jump);
			}
		}
		
		public final JumpInsnNode jump;
		
		public JumpEdge(BasicBlock src, BasicBlock dst, JumpInsnNode jump, InverseJumpEdge inverse) {
			super(src, dst, inverse);
			this.jump = jump;
		}
		
		@Override
		public String toGraphString() {
			return Printer.OPCODES[jump.opcode()];
		}
		@Override
		public String toString() {
			return String.format("Jump[%s] #%s -> #%s", Printer.OPCODES[jump.opcode()], src.getId(), dst.getId());
		}
	}
	
	public static class UnconditionalJumpEdge extends JumpEdge {
		public UnconditionalJumpEdge(BasicBlock src, BasicBlock dst, JumpInsnNode jump) {
			super(src, dst, jump, new InverseJumpEdge(dst, src, jump) {
				@Override
				public String toString() {
					return "Unconditional" + super.toString();
				}
			});
		}
		
		@Override
		public String toString() {
			return "Unconditional" + super.toString();
		}
		
		@Override
		public FlowEdge clone(BasicBlock src, BasicBlock dst) {
			return new UnconditionalJumpEdge(src, dst, jump);
		}
	}
	
	public static class ConditionalJumpEdge extends JumpEdge {
		public ConditionalJumpEdge(BasicBlock src, BasicBlock dst, JumpInsnNode jump) {
			super(src, dst, jump, new InverseJumpEdge(dst, src, jump) {
				@Override
				public String toString() {
					return "Conditional" + super.toString();
				}
			});
		}
		
		@Override
		public String toString() {
			return "Conditional" + super.toString();
		}
		
		@Override
		public FlowEdge clone(BasicBlock src, BasicBlock dst) {
			return new ConditionalJumpEdge(src, dst, jump);
		}
	}
}