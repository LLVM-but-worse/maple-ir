package org.mapleir.ir.analysis;

import java.util.List;

public interface DepthFirstSearch<N> {

	List<N> getPreOrder();
	
	List<N> getPostOrder();
}