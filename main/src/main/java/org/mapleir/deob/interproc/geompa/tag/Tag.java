package org.mapleir.deob.interproc.geompa.tag;

public interface Tag {
	public String getName();

	public byte[] getValue() throws AttributeValueException;
}