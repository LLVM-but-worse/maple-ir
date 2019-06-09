package org.mapleir.asm;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import java.util.ArrayList;
import java.util.List;

public class ClassNode implements FastGraphVertex {
    private static int ID_COUNTER = 1;
   	private final int numericId = ID_COUNTER++;

    public final org.objectweb.asm.tree.ClassNode node;
    private final List<MethodNode> methods;
    private final List<FieldNode> fields;

    public ClassNode() {
        this.node = new org.objectweb.asm.tree.ClassNode();
        methods = new ArrayList<>();
        fields = new ArrayList<>();
    }

    ClassNode(org.objectweb.asm.tree.ClassNode node) {
        this.node = node;
        methods = new ArrayList<>(node.methods.size());
        for (org.objectweb.asm.tree.MethodNode mn : node.methods)
            methods.add(new MethodNode(mn, this));
        fields = new ArrayList<>(node.fields.size());
        for (org.objectweb.asm.tree.FieldNode fn : node.fields)
            fields.add(new FieldNode(fn, this));
    }

    public String getName() {
        return node.name;
    }

    public List<MethodNode> getMethods() {
        return methods;
    }

    public void addMethod(MethodNode mn) {
        methods.add(mn);
        node.methods.add(mn.node);
    }

    public List<FieldNode> getFields() {
        return fields;
    }

    @Override
    public String getDisplayName() {
        return node.name.replace("/", "_");
    }

    @Override
    public int getNumericId() {
        return numericId;
    }
}
