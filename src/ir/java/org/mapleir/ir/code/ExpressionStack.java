package org.mapleir.ir.code;

import java.util.Arrays;

import org.mapleir.ir.code.expr.Expression;

// top(0) -> bottom(size() - 1)
public class ExpressionStack {
	
	private Expression[] stack;
	private int size;
	
	public ExpressionStack() {
		this(8 * 8);
	}
	
	public ExpressionStack(int capacity) {
		capacity = Math.max(capacity, 1);
		stack = new Expression[capacity];
		size = 0;
	}
	
	private void expand() {
		Expression[] s = new Expression[size * 2];
		System.arraycopy(stack, 0, s, 0, size);
		stack = s;
	}
	
	public void push(Expression e) {
		int i = size++;
		if(stack.length == size) {
			expand();
		}
		stack[i] = e;
	}
	
	public Expression peek() {
		return stack[size - 1];
	}
	
	public Expression peek(int d) {
		return stack[size - d - 1];
	}
	
	public Expression pop() {
		Expression e = stack[--size];
		stack[size] = null;
		return e;
	}
	
	public Expression getAt(int i) {
		return stack[i];
	}
	
	public void copyInto(ExpressionStack other) {
		Expression[] news = new Expression[size];
		System.arraycopy(stack, 0, news, 0, size);
		other.stack = news;
	}
	
	public ExpressionStack copy() {
		ExpressionStack stack = new ExpressionStack(size());
		copyInto(stack);
		return stack;
	}
	
	public void assertHeights(int[] heights) {
		if(heights.length > size()) {
			throw new UnsupportedOperationException(String.format("hlen=%d, size=%d", heights.length, size()));
		} else {
			for(int i=0; i < heights.length; i++) {
				Expression e = peek(i);
				if(e.getType().getSize() != heights[i]) {
					throw new IllegalStateException(String.format("item at %d, len=%d, expected=%d, expr:%s", i, e.getType().getSize(), heights[i], e));
				}
			}
		}
	}
	
	public void clear() {
		for(int i=size-1; i >= 0; i--) {
			stack[i] = null;
		}
	}
	
	public boolean isEmpty() {
		return size <= 0;
	}
	
	public int size() {
		return size;
	}
	
	public int capacity() {
		return stack.length;
	}
	
	public int height() {
		int count = 0;
		for(int i=0; i < size(); i++) {
			count += peek(i).getType().getSize();
		}
		return count;
	}

	@Override
	public String toString() {
		System.out.println("s: " + Arrays.toString(stack));
		StringBuilder sb = new StringBuilder();
		sb.append("top->btm[");
		for (int i = size() - 1; i >= 0; i--) {
			Expression n = peek(i);
			if (n != null) {
				sb.append(n);
				sb.append(":").append(n.getType());
				if(i != 0 && peek(i - 1) != null) {
					sb.append(", ");
				}
			}
		}
		sb.append("]");
		return sb.toString();
	}
}