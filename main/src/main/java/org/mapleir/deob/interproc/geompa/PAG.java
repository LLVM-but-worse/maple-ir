package org.mapleir.deob.interproc.geompa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.interproc.geompa.PointsToFunctor.BooleanPointsToFunctor;
import org.mapleir.deob.interproc.geompa.util.ChunkedQueue;
import org.mapleir.deob.interproc.geompa.util.QueueReader;
import org.mapleir.ir.TypeUtils;
import org.mapleir.ir.code.expr.AllocObjectExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.expr.invoke.InitialisedObjectExpr;
import org.mapleir.stdlib.collections.map.CachedKeyedValueCreator;
import org.mapleir.stdlib.collections.map.KeyedValueCreator;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

// Pointer Assignment Graph
public class PAG implements PointsToAnalysis  {

	// not final so asshole compiler doesnt cry 
	public static boolean VERBOSE = true;
	
	public static boolean RTA = false;
	public static boolean VTA = true;
	public static boolean SIMPLIFY_SCCS = true;
	public static boolean TYPES_FOR_SITES = true;
	public static boolean ON_FLY_CG = false;
	public static boolean FIELD_BASED = false;
	
	public static boolean ALIAS_PROP = false;
	public static boolean SIMPLE_EDGES_BIDIRECTIONAL = false;
	
	public static boolean LIBRARY = false;
	public static boolean LIBRARY_DISABLED = false;
	
	public static final boolean ADD_TAGS = false;
	
	private final AnalysisContext context;
	private KeyedValueCreator<Type, AbstractPointsToSet> pointsToSetCreator;
	private final Map<VarExpr, LocalVarNode> localToNodeMap = new HashMap<>();
	// protected Map<Pair<Node, Node>, Set<Edge>> assign2edges = new HashMap<Pair<Node, Node>, Set<Edge>>();
	private final Map<Object, LocalVarNode> valToLocalVarNode = new HashMap<>(1000);
	private final Map<Object, GlobalVarNode> valToGlobalVarNode = new HashMap<>(1000);
	private final Map<Object, AllocNode> valToAllocNode = new HashMap<>(1000);
	private final Table<Object, Type, AllocNode> valToReflAllocNode = HashBasedTable.create();

	private final KeyedValueCreator<FieldNode, SparkField> sparkFieldFinder;
	protected ChunkedQueue<PointsToNode> edgeQueue = new ChunkedQueue<>();
	protected ChunkedQueue<AllocNode> newAllocNodes = new ChunkedQueue<>();
	
	public int maxFinishNumber = 0;

	protected Map<VarNode, Object> simple = new HashMap<>();
	protected Map<FieldRefNode, Object> load = new HashMap<>();
	protected Map<VarNode, Object> store = new HashMap<>();
	protected Map<AllocNode, Object> alloc = new HashMap<>();
	protected Map<VarNode, Object> newInstance = new HashMap<>();
	protected Map<NewInstanceNode, Object> assignInstance = new HashMap<>();

	protected Map<VarNode, Object> simpleInv = new HashMap<>();
	protected Map<VarNode, Object> loadInv = new HashMap<>();
	protected Map<FieldRefNode, Object> storeInv = new HashMap<>();
	protected Map<VarNode, Object> allocInv = new HashMap<>();
	protected Map<NewInstanceNode, Object> newInstanceInv = new HashMap<>();
	protected Map<VarNode, Object> assignInstanceInv = new HashMap<>();

	// used to use soot.Value instead of Object
	private final Map<Object, NewInstanceNode> newInstToNodeMap = new HashMap<>();

	protected boolean somethingMerged = false;
	
	private Map<PointsToNode, Tag> nodeToTag;

	public PAG(AnalysisContext context) {
		this.context = context;
		pointsToSetCreator = (k) -> new DefaultPointsToSet(context.getApplication(), k);
		
		sparkFieldFinder = new CachedKeyedValueCreator<FieldNode, SparkField>() {
			@Override
			protected SparkField create0(FieldNode k) {
				return new SparkFieldNodeImpl(k);
			}
		};
		
		if(ADD_TAGS) {
			nodeToTag = new HashMap<>();
		}
	}

	public QueueReader<PointsToNode> edgeReader() {
		return edgeQueue.reader();
	}
	
	public QueueReader<AllocNode> allocNodeListener() {
		return newAllocNodes.reader();
	}

	public KeyedValueCreator<Type, AbstractPointsToSet> getSetFactory() {
		return pointsToSetCreator;
	}
	
	/** Returns the set of objects pointed to by variable l. */
	@Override
	public AbstractPointsToSet reachingObjects(VarExpr l) {
		PointsToNode n = findLocalVarNode(l);
		if (n == null) {
			return EmptyPointsToSet.INSTANCE;
		}
		return n.getP2Set();
	}
	
	/** Returns the set of objects pointed to by variable l in context c. */
	@Override
	public AbstractPointsToSet reachingObjects(Context c, VarExpr l) {
		VarNode n = findContextVarNode(l, c);
		if (n == null) {
			return EmptyPointsToSet.INSTANCE;
		}
		return n.getP2Set();
	}

	/** Returns the set of objects pointed to by static field f. */
	@Override
	public AbstractPointsToSet reachingObjects(FieldNode f) {
		if (!f.isStatic())
			throw new RuntimeException("The parameter f must be a *static* field.");
		VarNode n = findGlobalVarNode(f);
		if (n == null) {
			return EmptyPointsToSet.INSTANCE;
		}
		return n.getP2Set();
	}

	/**
	 * Returns the set of objects pointed to by instance field f of the objects in the PointsToSet s.
	 */
	@Override
	public AbstractPointsToSet reachingObjects(AbstractPointsToSet s, final FieldNode f) {
		if (f.isStatic())
			throw new RuntimeException("The parameter f must be an *instance* field.");

		return reachingObjectsInternal(s, sparkFieldFinder.create(f));
	}

	/**
	 * Returns the set of objects pointed to by elements of the arrays in the PointsToSet s.
	 */
	@Override
	public AbstractPointsToSet reachingObjectsOfArrayElement(AbstractPointsToSet s) {
		return reachingObjectsInternal(s, ArrayElement.INSTANCE);
	}

	private AbstractPointsToSet reachingObjectsInternal(AbstractPointsToSet s, final SparkField f) {
		if (FIELD_BASED || VTA) {
			VarNode n = findGlobalVarNode(f);
			if (n == null) {
				return EmptyPointsToSet.INSTANCE;
			}
			return n.getP2Set();
		}
		if (ALIAS_PROP) {
			throw new RuntimeException(
					"The alias edge propagator does not compute points-to information for instance fields! Use a different propagator.");
		}
		AbstractPointsToSet bases = (AbstractPointsToSet) s;
		final AbstractPointsToSet ret = pointsToSetCreator.create((f instanceof SparkFieldNodeImpl) ? f.getType() : null);
		bases.forAll(new BooleanPointsToFunctor() {
			@Override
			public void apply(PointsToNode n) {
				PointsToNode nDotF = ((AllocNode) n).dot(f);
				if (nDotF != null)
					ret.addAll(nDotF.getP2Set(), null);
			}
			
		});
		return ret;
	}
	
	@Override
	public AbstractPointsToSet reachingObjects(VarExpr l, FieldNode f) {
		return reachingObjects(reachingObjects(l), f);
	}

	/**
	 * Returns the set of objects pointed to by instance field f of the objects
	 * pointed to by l in context c.
	 */
	@Override
	public AbstractPointsToSet reachingObjects(Context c, VarExpr l, FieldNode f) {
		return reachingObjects(reachingObjects(c, l), f);
	}
	
	public AllocNode makeAllocNode(Object newExpr, Type type, MethodNode m) {
		if (TYPES_FOR_SITES || VTA)
			newExpr = type;
		
		AllocNode ret = valToAllocNode.get(newExpr);
		if(newExpr instanceof AllocObjectExpr) {
			throw new IllegalStateException();
		} else if (newExpr instanceof InitialisedObjectExpr) {
			// Do we need to create a new allocation node?
			if (ret == null) {
				valToAllocNode.put(newExpr, ret = new AllocNode(this, newExpr, type, m));
				newAllocNodes.add(ret);
				addNodeTag(ret, m);
			}
			// For a normal "new" expression, there may only be one type
			else if (!(ret.getType().equals(type)))
				throw new RuntimeException(
						"NewExpr " + newExpr + " of type " + type + " previously had type " + ret.getType());
		}
		// Check for reflective allocation sites
		else {
			ret = valToReflAllocNode.get(newExpr, type);
			if (ret == null) {
				valToReflAllocNode.put(newExpr, type, ret = new AllocNode(this, newExpr, type, m));
				newAllocNodes.add(ret);
				addNodeTag(ret, m);
			}
		}
		return ret;
	}
	
	public AllocNode makeStringConstantNode(String s) {
		if (TYPES_FOR_SITES || VTA)
			return makeAllocNode(TypeUtils.STRING, TypeUtils.STRING, null);
		StringConstantNode ret = (StringConstantNode) valToAllocNode.get(s);
		if (ret == null) {
			valToAllocNode.put(s, ret = new StringConstantNode(this, s));
			newAllocNodes.add(ret);
			addNodeTag(ret, null);
		}
		return ret;
	}
	
	public AllocNode makeClassConstantNode(ClassConstant cc) {
		if (TYPES_FOR_SITES || VTA)
			return makeAllocNode(TypeUtils.CLASS, TypeUtils.CLASS, null);
		ClassConstantNode ret = (ClassConstantNode) valToAllocNode.get(cc);
		if (ret == null) {
			valToAllocNode.put(cc, ret = new ClassConstantNode(this, cc));
			newAllocNodes.add(ret);
			addNodeTag(ret, null);
		}
		return ret;
	}
	
	public GlobalVarNode makeGlobalVarNode(Object value, Type type) {
		if (RTA) {
			value = null;
			type = TypeUtils.OBJECT_TYPE;
		}
		GlobalVarNode ret = valToGlobalVarNode.get(value);
		if (ret == null) {
			valToGlobalVarNode.put(value, ret = new GlobalVarNode(this, value, type));

			// if library mode is activated, add allocation of every possible
			// type to accessible fields
			if (LIBRARY != LIBRARY_DISABLED) {
				// FIXME: check for FieldNode or SparkFieldNodeImpl here?
				/*if (value instanceof SootField) {
					SootField sf = (SootField) value;

					if (accessibilityOracle.isAccessible(sf)) {
						type.apply(new SparkLibraryHelper(this, ret, null));
					}
				}*/
				throw new RuntimeException("fix my ass");
			}
			addNodeTag(ret, null);
		} else if (!(ret.getType().equals(type))) {
			throw new RuntimeException("Value " + value + " of type " + type + " previously had type " + ret.getType());
		}
		return ret;
	}
	
	public LocalVarNode makeLocalVarNode(Object value, Type type, MethodNode method) {
		if (RTA) {
			value = null;
			type = TypeUtils.OBJECT_TYPE;
			method = null;
		} else if (value instanceof VarExpr) {
			VarExpr val = (VarExpr) value;
			// FIXME: impact of numbering?
			//if (val.getNumber() == 0)
			//	Scene.v().getLocalNumberer().add(val);
			LocalVarNode ret = localToNodeMap.get(val);
			if (ret == null) {
				localToNodeMap.put(val, ret = new LocalVarNode(this, value, type, method));
				addNodeTag(ret, method);
			} else if (!(ret.getType().equals(type))) {
				throw new RuntimeException(
						"Value " + value + " of type " + type + " previously had type " + ret.getType());
			}
			return ret;
		}
		LocalVarNode ret = valToLocalVarNode.get(value);
		if (ret == null) {
			valToLocalVarNode.put(value, ret = new LocalVarNode(this, value, type, method));
			addNodeTag(ret, method);
		} else if (!(ret.getType().equals(type))) {
			throw new RuntimeException("Value " + value + " of type " + type + " previously had type " + ret.getType());
		}
		return ret;
	}
	
	// used to use soot.Value instead Object
	public NewInstanceNode makeNewInstanceNode(Object value, Type type, MethodNode method) {
		NewInstanceNode node = newInstToNodeMap.get(value);
		if (node == null) {
			node = new NewInstanceNode(this, value, type);
			newInstToNodeMap.put(value, node);
			addNodeTag(node, method);
		}
		return node;
	}

	public ContextVarNode makeContextVarNode(Object baseValue, Type baseType, Context context, MethodNode method) {
		LocalVarNode base = makeLocalVarNode(baseValue, baseType, method);
		return makeContextVarNode(base, context);
	}
	
	public ContextVarNode makeContextVarNode(LocalVarNode base, Context context) {
		ContextVarNode ret = base.context(context);
		if (ret == null) {
			ret = new ContextVarNode(this, base, context);
			addNodeTag(ret, base.getMethod());
		}
		return ret;
	}
	
	public FieldRefNode makeLocalFieldRefNode(Object baseValue, Type baseType, SparkField field, MethodNode method) {
		VarNode base = makeLocalVarNode(baseValue, baseType, method);
		FieldRefNode ret = makeFieldRefNode(base, field);

		// if library mode is activated, add allocation of every possible type
		// to accessible fields
		if (LIBRARY != LIBRARY_DISABLED) {
			// FIXME: check for FieldNode or SparkFieldNodeImpl here?
			/*if (field instanceof SootField) {
				SootField sf = (SootField) field;
				Type type = sf.getType();
				if (accessibilityOracle.isAccessible(sf)) {
					type.apply(new SparkLibraryHelper(this, ret, method));
				}
			}*/
			throw new RuntimeException("fix my ass");
		}

		return ret;
	}

	public FieldRefNode makeGlobalFieldRefNode(Object baseValue, Type baseType, SparkField field) {
		VarNode base = makeGlobalVarNode(baseValue, baseType);
		return makeFieldRefNode(base, field);
	}

	public FieldRefNode makeFieldRefNode(VarNode base, SparkField field) {
		FieldRefNode ret = base.dot(field);
		if (ret == null) {
			ret = new FieldRefNode(this, base, field);
			if (base instanceof LocalVarNode) {
				addNodeTag(ret, ((LocalVarNode) base).getMethod());
			} else {
				addNodeTag(ret, null);
			}

		}
		return ret;
	}
	
	public AllocDotField makeAllocDotField(AllocNode an, SparkField field) {
		AllocDotField ret = an.dot(field);
		if (ret == null) {
			ret = new AllocDotField(this, an, field);
		}
		return ret;
	}

	public GlobalVarNode findGlobalVarNode(Object value) {
		if (RTA) {
			value = null;
		}
		return valToGlobalVarNode.get(value);
	}
	
	public LocalVarNode findLocalVarNode(Object value) {
		if (RTA) {
			value = null;
		} else if (value instanceof VarExpr) {
			return localToNodeMap.get((VarExpr) value);
		}
		return valToLocalVarNode.get(value);
	}

	public ContextVarNode findContextVarNode(Object baseValue, Context context) {
		LocalVarNode base = findLocalVarNode(baseValue);
		if (base == null)
			return null;
		return base.context(context);
	}
	
	public FieldRefNode findLocalFieldRefNode(Object baseValue, SparkField field) {
		VarNode base = findLocalVarNode(baseValue);
		if (base == null)
			return null;
		return base.dot(field);
	}
	
	public FieldRefNode findGlobalFieldRefNode(Object baseValue, SparkField field) {
		VarNode base = findGlobalVarNode(baseValue);
		if (base == null)
			return null;
		return base.dot(field);
	}

	public AllocDotField findAllocDotField(AllocNode an, SparkField field) {
		return an.dot(field);
	}
	/* looks like class file annotations */
	private void addNodeTag(PointsToNode node, MethodNode m) {
		if (nodeToTag != null) {
			/*Tag tag;
			if (m == null) {
				tag = new StringTag(node.toString());
			} else {
				tag = new LinkTag(node.toString(), m, m.getDeclaringClass().getName());
			}
			nodeToTag.put(node, tag);*/
			throw new UnsupportedOperationException();
		}
	}
	
	public boolean doAddSimpleEdge(VarNode from, VarNode to) {
		return addToMap(simple, from, to) | addToMap(simpleInv, to, from);
	}

	public boolean doAddStoreEdge(VarNode from, FieldRefNode to) {
		return addToMap(store, from, to) | addToMap(storeInv, to, from);
	}

	public boolean doAddLoadEdge(FieldRefNode from, VarNode to) {
		return addToMap(load, from, to) | addToMap(loadInv, to, from);
	}

	public boolean doAddAllocEdge(AllocNode from, VarNode to) {
		return addToMap(alloc, from, to) | addToMap(allocInv, to, from);
	}

	public boolean doAddNewInstanceEdge(VarNode from, NewInstanceNode to) {
		return addToMap(newInstance, from, to) | addToMap(newInstanceInv, to, from);
	}

	public boolean doAddAssignInstanceEdge(NewInstanceNode from, VarNode to) {
		return addToMap(assignInstance, from, to) | addToMap(assignInstanceInv, to, from);
	}
	
	protected <K extends PointsToNode> boolean addToMap(Map<K, Object> m, K key, PointsToNode value) {
		Object valueList = m.get(key);

		if (valueList == null) {
			m.put(key, valueList = new HashSet<>(4));
		} else if (!(valueList instanceof Set)) {
			PointsToNode[] ar = (PointsToNode[]) valueList;
			HashSet<PointsToNode> vl = new HashSet<>(ar.length + 4);
			m.put(key, vl);
			for (PointsToNode element : ar)
				vl.add(element);
			return vl.add(value);
		}
		return ((Set<PointsToNode>) valueList).add(value);
	}
	
	public boolean addSimpleEdge(VarNode from, VarNode to) {
		boolean ret = false;
		if (doAddSimpleEdge(from, to)) {
			edgeQueue.add(from);
			edgeQueue.add(to);
			ret = true;
		}
		if (SIMPLE_EDGES_BIDIRECTIONAL) {
			if (doAddSimpleEdge(to, from)) {
				edgeQueue.add(to);
				edgeQueue.add(from);
				ret = true;
			}
		}
		return ret;
	}

	public boolean addStoreEdge(VarNode from, FieldRefNode to) {
		if (!RTA) {
			if (doAddStoreEdge(from, to)) {
				edgeQueue.add(from);
				edgeQueue.add(to);
				return true;
			}
		}
		return false;
	}

	public boolean addLoadEdge(FieldRefNode from, VarNode to) {
		if (!RTA) {
			if (doAddLoadEdge(from, to)) {
				edgeQueue.add(from);
				edgeQueue.add(to);
				return true;
			}
		}
		return false;
	}

	public boolean addAllocEdge(AllocNode from, VarNode to) {
		if (to.getType() == null || TypeUtils.canStoreClass(context.getApplication(), from.getType(), to.getType())) {
			if (doAddAllocEdge(from, to)) {
				edgeQueue.add(from);
				edgeQueue.add(to);
				return true;
			}
		}
		return false;
	}
	
	public boolean addNewInstanceEdge(VarNode from, NewInstanceNode to) {
		if (!RTA) {
			if (doAddNewInstanceEdge(from, to)) {
				edgeQueue.add(from);
				edgeQueue.add(to);
				return true;
			}
		}
		return false;
	}

	public boolean addAssignInstanceEdge(NewInstanceNode from, VarNode to) {
		if (!RTA) {
			if (doAddAssignInstanceEdge(from, to)) {
				edgeQueue.add(from);
				edgeQueue.add(to);
				return true;
			}
		}
		return false;
	}

	/** Adds an edge to the graph, returning false if it was already there. */
	public final boolean addEdge(PointsToNode from, PointsToNode to) {
		from = from.getReplacement();
		to = to.getReplacement();
		if (from instanceof VarNode) {
			if (to instanceof VarNode) {
				return addSimpleEdge((VarNode) from, (VarNode) to);
			} else if (to instanceof FieldRefNode) {
				return addStoreEdge((VarNode) from, (FieldRefNode) to);
			} else if (to instanceof NewInstanceNode) {
				return addNewInstanceEdge((VarNode) from, (NewInstanceNode) to);
			}
			else
				throw new RuntimeException("Invalid node type");
		} else if (from instanceof FieldRefNode) {
			return addLoadEdge((FieldRefNode) from, (VarNode) to);

		} else if (from instanceof NewInstanceNode) {
			return addAssignInstanceEdge((NewInstanceNode) from, (VarNode) to);
		} else {
			return addAllocEdge((AllocNode) from, (VarNode) to);
		}
}
	
	private final ArrayNumberer<AllocNode> allocNodeNumberer = new ArrayNumberer<>();

	public ArrayNumberer<AllocNode> getAllocNodeNumberer() {
		return allocNodeNumberer;
	}

	private final ArrayNumberer<VarNode> varNodeNumberer = new ArrayNumberer<>();

	public ArrayNumberer<VarNode> getVarNodeNumberer() {
		return varNodeNumberer;
	}

	private final ArrayNumberer<FieldRefNode> fieldRefNodeNumberer = new ArrayNumberer<>();

	public ArrayNumberer<FieldRefNode> getFieldRefNodeNumberer() {
		return fieldRefNodeNumberer;
	}

	private final ArrayNumberer<AllocDotField> allocDotFieldNodeNumberer = new ArrayNumberer<>();

	public ArrayNumberer<AllocDotField> getAllocDotFieldNodeNumberer() {
		return allocDotFieldNodeNumberer;
	}

	static private int getSize(Object set) {
		if (set instanceof Set)
			return ((Set<?>) set).size();
		else if (set == null)
			return 0;
		else
			return ((Object[]) set).length;
	}

	void mergedWith(PointsToNode n1, PointsToNode n2) {
		if (n1.equals(n2))
			throw new RuntimeException("oops");

		somethingMerged = true;
		// FIXME:
		// if (ofcg() != null)
		// ofcg().mergedWith(n1, n2);

		Map[] maps = { simple, alloc, store, load, simpleInv, allocInv, storeInv, loadInv };
		for (Map<PointsToNode, Object> m : maps) {
			if (!m.keySet().contains(n2))
				continue;

			Object[] os = { m.get(n1), m.get(n2) };
			int size1 = getSize(os[0]);
			int size2 = getSize(os[1]);
			if (size1 == 0) {
				if (os[1] != null)
					m.put(n1, os[1]);
			} else if (size2 == 0) {
				// nothing needed
			} else if (os[0] instanceof HashSet) {
				if (os[1] instanceof HashSet) {
					((HashSet) os[0]).addAll((HashSet) os[1]);
				} else {
					PointsToNode[] ar = (PointsToNode[]) os[1];
					for (PointsToNode element0 : ar) {
						((HashSet<PointsToNode>) os[0]).add(element0);
					}
				}
			} else if (os[1] instanceof HashSet) {
				PointsToNode[] ar = (PointsToNode[]) os[0];
				for (PointsToNode element0 : ar) {
					((HashSet<PointsToNode>) os[1]).add(element0);
				}
				m.put(n1, os[1]);
			} else if (size1 * size2 < 1000) {
				PointsToNode[] a1 = (PointsToNode[]) os[0];
				PointsToNode[] a2 = (PointsToNode[]) os[1];
				PointsToNode[] ret = new PointsToNode[size1 + size2];
				System.arraycopy(a1, 0, ret, 0, a1.length);
				int j = a1.length;
				outer: for (PointsToNode rep : a2) {
					for (int k = 0; k < j; k++)
						if (rep == ret[k])
							continue outer;
					ret[j++] = rep;
				}
				PointsToNode[] newArray = new PointsToNode[j];
				System.arraycopy(ret, 0, newArray, 0, j);
				m.put(n1, ret = newArray);
			} else {
				HashSet<PointsToNode> s = new HashSet<>(size1 + size2);
				for (Object o : os) {
					if (o == null)
						continue;
					if (o instanceof Set) {
						s.addAll((Set<PointsToNode>) o);
					} else {
						PointsToNode[] ar = (PointsToNode[]) o;
						for (PointsToNode element1 : ar) {
							s.add(element1);
						}
					}
				}
				m.put(n1, s);
			}
			m.remove(n2);
		}
	}

	public void cleanUpMerges() {
		if (VERBOSE) {
			System.out.println("Cleaning up graph for merged nodes");
		}
		lookupInMap(simple);
		lookupInMap(alloc);
		lookupInMap(store);
		lookupInMap(load);
		lookupInMap(simpleInv);
		lookupInMap(allocInv);
		lookupInMap(storeInv);
		lookupInMap(loadInv);

		somethingMerged = false;
		if (VERBOSE) {
			System.out.println("Done cleaning up graph for merged nodes");
		}
	}
	
	private <K extends PointsToNode> void lookupInMap(Map<K, Object> map) {
		for (K object : map.keySet()) {
			lookup(map, object);
		}
	}

	protected final static PointsToNode[] EMPTY_NODE_ARRAY = new PointsToNode[0];

	protected <K extends PointsToNode> PointsToNode[] lookup(Map<K, Object> m, K key) {
		Object valueList = m.get(key);
		if (valueList == null) {
			return EMPTY_NODE_ARRAY;
		}
		if (valueList instanceof Set) {
			try {
				m.put(key, valueList = ((Set) valueList).toArray(EMPTY_NODE_ARRAY));
			} catch (Exception e) {
				for (Iterator it = ((Set) valueList).iterator(); it.hasNext();) {
					// G.v().out.println("" + it.next());
					System.out.println(it.next());
				}
				throw new RuntimeException("" + valueList + e);
			}
		}
		PointsToNode[] ret = (PointsToNode[]) valueList;
		if (somethingMerged) {
			for (int i = 0; i < ret.length; i++) {
				PointsToNode reti = ret[i];
				PointsToNode rep = reti.getReplacement();
				if (rep != reti || rep == key) {
					Set<PointsToNode> s;
					if (ret.length <= 75) {
						int j = i;
						outer: for (; i < ret.length; i++) {
							reti = ret[i];
							rep = reti.getReplacement();
							if (rep == key)
								continue;
							for (int k = 0; k < j; k++)
								if (rep == ret[k])
									continue outer;
							ret[j++] = rep;
						}
						PointsToNode[] newArray = new PointsToNode[j];
						System.arraycopy(ret, 0, newArray, 0, j);
						m.put(key, ret = newArray);
					} else {
						s = new HashSet<>(ret.length * 2);
						for (int j = 0; j < i; j++)
							s.add(ret[j]);
						for (int j = i; j < ret.length; j++) {
							rep = ret[j].getReplacement();
							if (rep != key) {
								s.add(rep);
							}
						}
						m.put(key, ret = s.toArray(EMPTY_NODE_ARRAY));
					}
					break;
				}
			}
		}
		return ret;
	}
	

	public PointsToNode[] simpleLookup(VarNode key) {
		return lookup(simple, key);
	}

	public PointsToNode[] simpleInvLookup(VarNode key) {
		return lookup(simpleInv, key);
	}

	public PointsToNode[] loadLookup(FieldRefNode key) {
		return lookup(load, key);
	}

	public PointsToNode[] loadInvLookup(VarNode key) {
		return lookup(loadInv, key);
	}

	public PointsToNode[] storeLookup(VarNode key) {
		return lookup(store, key);
	}
	
	public PointsToNode[] newInstanceLookup(VarNode key) {
		return lookup(newInstance, key);
	}

	public PointsToNode[] assignInstanceLookup(NewInstanceNode key) {
		return lookup(assignInstance, key);
	}

	public PointsToNode[] storeInvLookup(FieldRefNode key) {
		return lookup(storeInv, key);
	}

	public PointsToNode[] allocLookup(AllocNode key) {
		return lookup(alloc, key);
	}

	public PointsToNode[] allocInvLookup(VarNode key) {
		return lookup(allocInv, key);
	}

	public Set<VarNode> simpleSources() {
		return simple.keySet();
	}

	public Set<AllocNode> allocSources() {
		return alloc.keySet();
	}

	public Set<VarNode> storeSources() {
		return store.keySet();
	}

	public Set<FieldRefNode> loadSources() {
		return load.keySet();
	}

	public Set<VarNode> newInstanceSources() {
		return newInstance.keySet();
	}

	public Set<NewInstanceNode> assignInstanceSources() {
		return assignInstance.keySet();
	}

	public Set<VarNode> simpleInvSources() {
		return simpleInv.keySet();
	}

	public Set<VarNode> allocInvSources() {
		return allocInv.keySet();
	}

	public Set<FieldRefNode> storeInvSources() {
		return storeInv.keySet();
	}

	public Set<VarNode> loadInvSources() {
		return loadInv.keySet();
	}

	public Iterator<VarNode> simpleSourcesIterator() {
		return simple.keySet().iterator();
	}

	public Iterator<AllocNode> allocSourcesIterator() {
		return alloc.keySet().iterator();
	}

	public Iterator<VarNode> storeSourcesIterator() {
		return store.keySet().iterator();
	}

	public Iterator<FieldRefNode> loadSourcesIterator() {
		return load.keySet().iterator();
	}

	public Iterator<VarNode> simpleInvSourcesIterator() {
		return simpleInv.keySet().iterator();
	}

	public Iterator<VarNode> allocInvSourcesIterator() {
		return allocInv.keySet().iterator();
	}

	public Iterator<FieldRefNode> storeInvSourcesIterator() {
		return storeInv.keySet().iterator();
	}

	public Iterator<VarNode> loadInvSourcesIterator() {
		return loadInv.keySet().iterator();
	}
}
