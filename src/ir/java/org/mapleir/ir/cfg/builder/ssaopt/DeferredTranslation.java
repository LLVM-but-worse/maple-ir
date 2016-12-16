package org.mapleir.ir.cfg.builder.ssaopt;

import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.locals.VersionedLocal;

public class DeferredTranslation {

	private final VersionedLocal local;
	private final AbstractCopyStatement def;
	private final Statement use;
	private final Statement tail;
	private final LatestValue value;
	
	public DeferredTranslation(VersionedLocal local, AbstractCopyStatement def, Statement use, Statement tail,
			LatestValue value) {
		this.local = local;
		this.def = def;
		this.use = use;
		this.tail = tail;
		this.value = value;
	}

	public VersionedLocal getLocal() {
		return local;
	}

	public AbstractCopyStatement getDef() {
		return def;
	}

	public Statement getUse() {
		return use;
	}

	public Statement getTail() {
		return tail;
	}

	public LatestValue getValue() {
		return value;
	}
}