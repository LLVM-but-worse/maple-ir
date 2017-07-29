package org.mapleir.deob.interproc.geompa.util;

/**
 * A queue of Object's. One can add objects to the queue, and they are later read by a QueueReader. One can create arbitrary numbers of QueueReader's for a queue, and each one receives all the Object's that are added. Only objects that have not been
 * read by all the QueueReader's are kept. A QueueReader only receives the Object's added to the queue <b>after</b> the QueueReader was created.
 * 
 * @author Ondrej Lhotak
 */
@SuppressWarnings("unchecked")
public final class ChunkedQueue<E> {
	static Object NULL_CONST = new Object();
	private static final int LENGTH = 60;
	private Object[] q;
	private int index;

	public ChunkedQueue() {
		q = new Object[LENGTH];
		index = 0;
	}

	/** Add an object to the queue. */
	public void add(E o) {
		if (o == null)
			o = (E) NULL_CONST;
		if (index == LENGTH - 1) {
			Object[] temp = new Object[LENGTH];
			q[index] = temp;
			q = temp;
			index = 0;
		}
		q[index++] = o;
	}

	/** Create reader which will read objects from the queue. */
	public QueueReader<E> reader() {
		return new QueueReader<E>( (E[])q, index );
	}
}