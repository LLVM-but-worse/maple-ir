package org.mapleir.deob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.mapleir.Boot;

public class PassGroup implements IPass {

	private final String name;
	private final List<IPass> passes;
	
	public PassGroup(String name) {
		this.name = name;
		passes = new ArrayList<>();
	}
	
	public PassGroup add(IPass p) {
		passes.add(p);
		return this;
	}
	
	public PassGroup remove(IPass p) {
		passes.remove(p);
		return this;
	}
	
	public IPass getPass(Predicate<IPass> p) {
		List<IPass> list = getPasses(p);
		if(list.size() == 1) {
			return list.get(0);
		} else {
			return null;
		}
	}
	
	public List<IPass> getPasses(Predicate<IPass> p) {
		return passes.stream().filter(p).collect(Collectors.toList());
	}

	@Override
	public PassResult accept(PassContext pcxt) {		
		return null;
	}
}
