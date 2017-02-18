package org.mapleir.testsuite.api;

import java.util.Set;

import org.objectweb.asm.tree.ClassNode;

public interface TestSource {

	Set<ClassNode> loadTestClasses() throws Exception;
}