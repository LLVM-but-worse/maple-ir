package org.mapleir.ir.antlr.util;

import java.util.HashMap;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.LibraryClassSource;
import org.objectweb.asm.tree.ClassNode;

public class AppendableLibraryClassSource extends LibraryClassSource {

    public AppendableLibraryClassSource(ApplicationClassSource parent) {
        super(parent, new HashMap<>());
    }
    
    public void addClass(ClassNode cn) {
        nodeMap.put(cn.name, cn);
        parent.getClassTree().addVertex(cn);
    }
}