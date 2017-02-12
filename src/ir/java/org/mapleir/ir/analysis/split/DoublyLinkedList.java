package org.mapleir.ir.analysis.split;

import java.util.Iterator;

/**
 * Doubly-linked list.
 *
 * We can't use {@link java.util.LinkedList} because that doesn't allow direct deletion of arbitrary nodes.
 */
public class DoublyLinkedList<T> implements Iterable<T> {

	public class ListNode {
		final T val;
		ListNode prev, next;

		public ListNode(ListNode prev, T val, ListNode next) {
			this.prev = prev;
			this.val = val;
			this.next = next;
		}
	}

	private ListNode first;
	private ListNode last;
	private int size;

	public DoublyLinkedList() {
		this.first = null;
		this.last = null;
		size = 0;
	}

	public int size() {
		return this.size;
	}

	public ListNode prepend(T val) {
		assert this.size >= 0;
		this.first = new ListNode(null, val, this.first);
		if (this.first.next != null) {
			this.first.next.prev = this.first;
		} else {
			this.last = this.first;
		}
		++this.size;
		return this.first;
	}

	public DoublyLinkedList<T> copy() {
		DoublyLinkedList<T> dl = new DoublyLinkedList<>();
		ListNode node = this.last;
		while (node != null) {
			dl.prepend(node.val);
			node = node.prev;
		}
		return dl;
	}

	public void appendDestroying(DoublyLinkedList<T> other) {
		assert this.size >= 0;
		if (other.first != null) {
			if (this.first == null) {
				this.first = other.first;
			} else {
				this.last.next = other.first;
				other.first.prev = this.last;
			}
			this.last = other.last;
		}
		this.size += other.size;
		other.zap();
	}

	public T getFirst() {
		assert this.size > 0;
		return this.first.val;
	}

	public void deleteFirst() {
		assert this.size > 0;
		this.delete(this.first);
	}

	public void delete(ListNode node) {
		assert this.size > 0;
		if (this.first == node) {
			this.first = node.next;
		} else {
			node.prev.next = node.next;
		}
		if (this.last == node) {
			this.last = node.prev;
		} else {
			node.next.prev = node.prev;
		}
		--this.size;
	}

	private void zap() {
		this.first = null;
		this.last = null;
		this.size = -1;
	}

	@Override
	public Iterator<T> iterator() {
		class IteratorImpl implements Iterator<T> {
			private ListNode node;

			public IteratorImpl(ListNode node) {
				this.node = node;
			}

			@Override
			public boolean hasNext() {
				return node != null;
			}

			@Override
			public T next() {
				T val = node.val;
				node = node.next;
				return val;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		}
		;

		return new IteratorImpl(this.first);
	}

	@Override
	public String toString() {
		StringBuffer b = new StringBuffer();
		b.append("DoublyLinkedList[");
		b.append(size);
		b.append("]{");
		ListNode node = this.first;
		while (node != null) {
			b.append(node.val.toString());
			node = node.next;
			if (node != null) {
				b.append(", ");
			}
		}
		b.append("}");
		return b.toString();
	}
}