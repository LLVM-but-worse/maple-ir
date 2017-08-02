package org.mapleir.deob.interproc.geompa;

public class MethodPAG {
	private PAG pag;
	MapleMethod method;
	protected MethodNodeFactory nodeFactory;
	
	public PAG pag() {
		return pag;
	}
	
	protected MethodPAG(PAG pag, MapleMethod m) {
		this.pag = pag;
		this.method = m;
		// this.nodeFacory = new MethodNodeFactory(pag, this);
	}
}
