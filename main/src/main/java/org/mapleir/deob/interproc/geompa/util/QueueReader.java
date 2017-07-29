package org.mapleir.deob.interproc.geompa.util;

import java.util.NoSuchElementException;

/**
 * A queue of Object's. One can add objects to the queue, and they are later read by a QueueReader. One can create arbitrary numbers of QueueReader's for a queue, and each one receives all the Object's that are added. Only objects that have not been
 * read by all the QueueReader's are kept. A QueueReader only receives the Object's added to the queue <b>after</b> the QueueReader was created.
 * 
 * @author Ondrej Lhotak
 */
public class QueueReader<E> implements java.util.Iterator<E> {
	private E[] q;
	private int index;

	QueueReader(E[] q, int index) {
		this.q = q;
		this.index = index;
	}

	/**
	 * Returns (and removes) the next object in the queue, or null if there are none.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public final E next() {
		if (q[index] == null)
			throw new NoSuchElementException();
		if (index == q.length - 1) {
			q = (E[]) q[index];
			index = 0;
			if (q[index] == null)
				throw new NoSuchElementException();
		}
		E ret = q[index];
		if (ret == ChunkedQueue.NULL_CONST)
			ret = null;
		index++;
		return ret;
	}

	/** Returns true iff there is currently another object in the queue. */
	@Override
	@SuppressWarnings("unchecked")
	public final boolean hasNext() {
		if (q[index] == null)
			return false;
		if (index == q.length - 1) {
			q = (E[]) q[index];
			index = 0;
			if (q[index] == null)
				return false;
		}
		return true;
	}

	@Override
	public final void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public final QueueReader<E> clone() {
		return new QueueReader<>(q, index);
	}
}