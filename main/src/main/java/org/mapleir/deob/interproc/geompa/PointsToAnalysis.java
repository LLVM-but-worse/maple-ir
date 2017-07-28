package org.mapleir.deob.interproc.geompa;

import org.mapleir.ir.locals.Local;
import org.objectweb.asm.tree.FieldNode;

public interface PointsToAnalysis {
	
    public static final String THIS_NODE = "THIS_NODE";
    public static final int RETURN_NODE = -2;
    public static final String THROW_NODE = "THROW_NODE";
    public static final String ARRAY_ELEMENTS_NODE = "ARRAY_ELEMENTS_NODE";
    public static final String CAST_NODE = "CAST_NODE";
    public static final String STRING_ARRAY_NODE = "STRING_ARRAY_NODE";
    public static final String STRING_NODE = "STRING_NODE";
    public static final String STRING_NODE_LOCAL = "STRING_NODE_LOCAL";
    public static final String EXCEPTION_NODE = "EXCEPTION_NODE";
    public static final String RETURN_STRING_CONSTANT_NODE = "RETURN_STRING_CONSTANT_NODE";
    public static final String STRING_ARRAY_NODE_LOCAL = "STRING_ARRAY_NODE_LOCAL";
    public static final String MAIN_THREAD_NODE = "MAIN_THREAD_NODE";
    public static final String MAIN_THREAD_NODE_LOCAL = "MAIN_THREAD_NODE_LOCAL";
    public static final String MAIN_THREAD_GROUP_NODE = "MAIN_THREAD_GROUP_NODE";
    public static final String MAIN_THREAD_GROUP_NODE_LOCAL = "MAIN_THREAD_GROUP_NODE_LOCAL";
    public static final String MAIN_CLASS_NAME_STRING = "MAIN_CLASS_NAME_STRING";
    public static final String MAIN_CLASS_NAME_STRING_LOCAL = "MAIN_CLASS_NAME_STRING_LOCAL";
    public static final String DEFAULT_CLASS_LOADER = "DEFAULT_CLASS_LOADER";
    public static final String DEFAULT_CLASS_LOADER_LOCAL = "DEFAULT_CLASS_LOADER_LOCAL";
    public static final String FINALIZE_QUEUE = "FINALIZE_QUEUE";
    public static final String CANONICAL_PATH = "CANONICAL_PATH";
    public static final String CANONICAL_PATH_LOCAL = "CANONICAL_PATH_LOCAL";
    public static final String PRIVILEGED_ACTION_EXCEPTION = "PRIVILEGED_ACTION_EXCEPTION";
    public static final String PRIVILEGED_ACTION_EXCEPTION_LOCAL = "PRIVILEGED_ACTION_EXCEPTION_LOCAL";
    public static final String PHI_NODE = "PHI_NODE";
    
    /** Returns the set of objects pointed to by variable l. */
    public AbstractPointsToSet reachingObjects( Local l );

    /** Returns the set of objects pointed to by variable l in context c. */
    public AbstractPointsToSet reachingObjects( Context c, Local l );

    /** Returns the set of objects pointed to by static field f. */
    public AbstractPointsToSet reachingObjects( FieldNode f );

    /** Returns the set of objects pointed to by instance field f
     * of the objects in the AbstractPointsToSet s. */
    public AbstractPointsToSet reachingObjects( AbstractPointsToSet s, FieldNode f );

    /** Returns the set of objects pointed to by instance field f
     * of the objects pointed to by l. */
    public AbstractPointsToSet reachingObjects( Local l, FieldNode f );

    /** Returns the set of objects pointed to by instance field f
     * of the objects pointed to by l in context c. */
    public AbstractPointsToSet reachingObjects( Context c, Local l, FieldNode f );

    /** Returns the set of objects pointed to by elements of the arrays
     * in the AbstractPointsToSet s. */
    public AbstractPointsToSet reachingObjectsOfArrayElement( AbstractPointsToSet s );
}