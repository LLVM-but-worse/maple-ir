package org.mapleir.deob.interproc.geompa;

import org.objectweb.asm.Type;

// either fieldnode needs to extend this or impl with node as member
public interface SparkField {
	Type getType();
}