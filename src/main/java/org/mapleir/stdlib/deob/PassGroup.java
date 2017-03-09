package org.mapleir.stdlib.deob;

import org.mapleir.Boot;
import org.mapleir.stdlib.IContext;

import java.util.ArrayList;
import java.util.List;

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
	
	public void run(IContext cxt) {
		// TODO: threads
	}

	@Override
	public int accept(IContext cxt, IPass __prev, List<IPass> __completed) {
		boolean[] passed = new boolean[passes.size()];
		
		List<IPass> completed = new ArrayList<>();
		IPass last = null;
		int lastDelta = 0;
		int delta = 0;
		
		for(;;) {
			completed.clear();
			last = null;
			// int pdelta = delta;
			delta = 0;
			
			if(name != null) {
				System.out.printf("Running %s group.%n", name);
			}
			
			for(int i=0; i < passed.length; i++) {
				IPass p = passes.get(i);
				
				/* run once. */
				if(passed[i] && !p.isSingletonPass()) {
					continue;
				}
				
				if(Boot.logging) {
					Boot.section0("...took %fs." + (i == 0 ? "%n" : ""), "Running " + p.getId());
				} else {
					System.out.println("Running " + p.getId());
				}
				lastDelta = p.accept(cxt, last, completed);
				
				completed.add(p);
				last = p;
				passed[i] = true;
				
				delta += lastDelta;
			}
			
			if(delta == 0) {
				break;
			}
			System.out.println();
			System.out.println();
		}
		
		return 0;
	}
	
	@Override
	public boolean isSingletonPass() {
		return false;
	}
}