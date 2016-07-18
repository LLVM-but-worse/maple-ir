package org.rsdeob.stdlib.ir.gen;

import org.rsdeob.stdlib.ir.expr.Expression;

public class ExpressionStack {

	private Expression[] stack;
	private int size;

	public ExpressionStack() {
		this(8 * 8 * 8);
	}

	public ExpressionStack(int len) {
		stack = new Expression[len];
		size = 0;
	}

	public Expression pop() {
		return stack[--size];
	}

	public Expression peek() {
		return peek(0);
	}

	public Expression peek(int depth) {
		return stack[size - depth - 1];
	}

	public void push(Expression expr) {
		stack[size++] = expr;
	}

//	public int indexDepth(int sizedDepth) {
//		int exprCount = 0;
//		for (int stackIndex = 0; stackIndex < sizedDepth; exprCount++) {
//			stackIndex += peek(exprCount).getType().getSize();
//		}
//		return exprCount;
//	}
//
//	public void insertBelow(Expression expr, int depth) {
//		int endIndex = size - indexDepth(depth);
//		int j = size;
//		while (j > endIndex) {
//			stack[j] = stack[j - 1];
//			j--;
//		}
//		stack[j] = expr;
//		size++;
//	}

	public ExpressionStack copy() {
		ExpressionStack stack = new ExpressionStack(this.stack.length);
		stack.size = size;
		for (int i = 0; i < this.stack.length; i++)
			if (this.stack[i] != null) {
				stack.stack[i] = this.stack[i].copy();
				if(stack.stack[i] == null) {
					throw new RuntimeException(this.stack[i].getClass().getSimpleName());
				}
			}
		return stack;
	}
	
	public void assertHeights(int[] heights) {
		if(heights.length > size) {
			throw new UnsupportedOperationException(String.format("hlen=%d, size=%d", heights.length, size));
		} else {
			for(int i=0; i < heights.length; i++) {
				Expression e = peek(i);
				if(e.getType().getSize() != heights[i]) {
					throw new IllegalStateException(String.format("item at %d, len=%d, expected=%d, expr:%s", i, e.getType().getSize(), heights[i], e));
				}
			}
		}
	}
	
	public int height() {
		int count = 0;
		for(int i=0; i < size; i++) {
			count += stack[i].getType().getSize();
		}
		return count;
	}

	public void clear() {
		size = 0;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("top->btm[");
		for (int i = size - 1; i >= 0; i--) {
			Expression n = stack[i];
			if (n != null) {
				sb.append(n);
				sb.append(":").append(n.getType());
				if(i != 0 && stack[i - 1] != null) {
					sb.append(", ");
				}
			}
		}
		sb.append("]");
		return sb.toString();
	}
	
	public String toTypeString() {
		StringBuilder sb = new StringBuilder();
		sb.append("top->btm[");
		for (int i = size - 1; i >= 0; i--) {
			Expression n = stack[i];
			if (n != null) {
				sb.append(n.getType());
				if(i != 0 && stack[i - 1] != null) {
					sb.append(", ");
				}
			}
		}
		sb.append("]");
		return sb.toString();
	}
}