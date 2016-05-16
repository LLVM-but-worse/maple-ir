package org.rsdeob.stdlib.cfg.util;

import org.rsdeob.stdlib.cfg.expr.Expression;

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

	public Expression pop1() {
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

	public int size() {
		return size;
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
				if(i != 0 && stack[i - 1] != null) {
					sb.append(", ");
				}
			}
		}
		sb.append("]");
		return sb.toString();
	}
}