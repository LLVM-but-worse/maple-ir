package org.mapleir.stdlib.ir.transform;

import java.util.Set;

import org.mapleir.ir.locals.Local;

public interface Liveness<N> {

	Set<Local> in(N n);
	
	Set<Local> out(N n);
}