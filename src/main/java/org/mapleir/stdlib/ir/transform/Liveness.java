package org.mapleir.stdlib.ir.transform;

import java.util.Set;

import org.mapleir.stdlib.ir.locals.Local;

public interface Liveness<N> {

	Set<Local> in(N n);
	
	Set<Local> out(N n);
}