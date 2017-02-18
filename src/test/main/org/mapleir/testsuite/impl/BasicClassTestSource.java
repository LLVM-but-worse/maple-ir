package org.mapleir.testsuite.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.mapleir.testsuite.api.Logger;

public class BasicClassTestSource extends AbstractClassTestSource {

	private final Set<File> classFiles;
	
	public BasicClassTestSource(Collection<File> col) {
		Set<File> classFiles;
		
		if(col == null) {
			classFiles = new HashSet<>();
		} else {
			classFiles = new HashSet<>(col);
		}
		
		this.classFiles = classFiles;
	}
	
	@Override
	public Iterator<InputStream> openClassStreams() {
		Iterator<File> it = classFiles.iterator();
		
		return new Iterator<InputStream>() {
			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public InputStream next() {
				File f = it.next();
				
				try {
					return new FileInputStream(f);
				} catch (FileNotFoundException e) {
					Logger.error("sourcefile: " + f.getAbsolutePath(), e);
				}
				
				return null;
			}
		};
	}
}