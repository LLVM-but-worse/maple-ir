/***
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.objectweb.asm.commons.blocksplit;

import java.util.Iterator;

/**
 * Doubly-linked list.
 *
 * We can't use {@link java.util.LinkedList} because that doesn't
 * allow direct deletion of arbitrary nodes.
 */
public class DList<A> implements Iterable<A> {
    
    public class Node {
        final A val;
        Node prev, next;
        public Node(Node prev, A val, Node next) {
            this.prev = prev;
            this.val = val;
            this.next = next;
        }
    }

    Node first;
    Node last;
    int size = 0;

    public DList() {
        this.first = null;
        this.last = null;
    }
    
    public int size() {
        return this.size;
    }

    public Node prepend(A val) {
        assert this.size >= 0;
        this.first = new Node(null, val, this.first);
        if (this.first.next != null) {
            this.first.next.prev = this.first;
        } else {
            this.last = this.first;
        }
        ++this.size;
        return this.first;
    }

    public DList<A> copy() {
        DList<A> dl = new DList<>();
        Node node = this.last;
        while (node != null) {
            dl.prepend(node.val);
            node = node.prev;
        }
        return dl;
    }

    public void appendDestroying(DList<A> other) {
        assert this.size >= 0;
        if (other.first != null) {
            if (this.first  == null) {
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

    public A getFirst() {
        assert this.size > 0;
        return this.first.val;
    }

    public void deleteFirst() {
        assert this.size > 0;
        this.delete(this.first);
    }

    public void delete(Node node) {
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
	public Iterator<A> iterator() {
        class It implements Iterator<A> {
            Node node;

            public It(Node node) {
                this.node = node;
            }

            @Override
			public boolean hasNext() {
                return node != null;
            }
            
            @Override
			public A next() {
                A val = node.val;
                node = node.next;
                return val;
            }
            
            @Override
			public void remove() {
                throw new UnsupportedOperationException();
            }
        };
        return new It(this.first);
    }
        
        

    @Override public String toString() {
        StringBuffer b = new StringBuffer();
        b.append("DList[");
        b.append(size);
        b.append("]{");
        Node node = this.first;
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