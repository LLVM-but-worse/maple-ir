package org.mapleir.deob.interproc.geompa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.mapleir.context.AnalysisContext;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.collections.map.KeyedValueCreator;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class PAG {

	public static final boolean RTA = false;
	public static final boolean VTA = true;
	public static final boolean SIMPLIFY_SCCS = true;
	public static final boolean TYPES_FOR_SITES = true;
	public static final boolean ON_FLY_CG = false;
	public static final boolean FIELD_BASED = false;
	
	public static final boolean ALIAS_PROP = false;
	
	private final AnalysisContext context;
	private KeyedValueCreator<Type, AbstractPointsToSet> pointsToSetCreator;
	private final Map<Local, LocalVarNode> localToNodeMap = new HashMap<>();
	// protected Map<Pair<Node, Node>, Set<Edge>> assign2edges = new HashMap<Pair<Node, Node>, Set<Edge>>();
	private final Map<Object, LocalVarNode> valToLocalVarNode = new HashMap<>(1000);
	private final Map<Object, GlobalVarNode> valToGlobalVarNode = new HashMap<Object, GlobalVarNode>(1000);
	private final Map<Object, AllocNode> valToAllocNode = new HashMap<Object, AllocNode>(1000);
	private final Table<Object, Type, AllocNode> valToReflAllocNode = HashBasedTable.create();

	public int maxFinishNumber = 0;

	protected Map<VarNode, Object> simple = new HashMap<>();
	protected Map<FieldRefNode, Object> load = new HashMap<>();
	protected Map<VarNode, Object> store = new HashMap<>();
	protected Map<AllocNode, Object> alloc = new HashMap<AllocNode, Object>();
	protected Map<VarNode, Object> newInstance = new HashMap<>();
	protected Map<NewInstanceNode, Object> assignInstance = new HashMap<NewInstanceNode, Object>();

	protected Map<VarNode, Object> simpleInv = new HashMap<>();
	protected Map<VarNode, Object> loadInv = new HashMap<>();
	protected Map<FieldRefNode, Object> storeInv = new HashMap<>();
	protected Map<VarNode, Object> allocInv = new HashMap<>();
	protected Map<NewInstanceNode, Object> newInstanceInv = new HashMap<NewInstanceNode, Object>();
	protected Map<VarNode, Object> assignInstanceInv = new HashMap<>();

	protected boolean somethingMerged = false;

	public PAG(AnalysisContext context) {
		this.context = context;
		pointsToSetCreator = (k) -> new DefaultPointsToSet(context.getApplication(), k);
	}

	public KeyedValueCreator<Type, AbstractPointsToSet> getSetFactory() {
		return pointsToSetCreator;
	}

	public LocalVarNode findLocalVarNode(Object value) {
		if (RTA) {
			value = null;
		} else if (value instanceof Local) {
			return localToNodeMap.get((Local) value);
		}
		return valToLocalVarNode.get(value);
	}

	/** Returns the set of objects pointed to by variable l. */
	public AbstractPointsToSet reachingObjects(Local l) {
		PointsToNode n = findLocalVarNode(l);
		if (n == null) {
			return EmptyPointsToSet.INSTANCE;
		}
		return n.getP2Set();
	}

	/** Returns the set of objects pointed to by variable l in context c. */
	public AbstractPointsToSet reachingObjects(Context c, Local l) {
		VarNode n = findContextVarNode(l, c);
		if (n == null) {
			return EmptyPointsToSet.INSTANCE;
		}
		return n.getP2Set();
	}

	/** Returns the set of objects pointed to by static field f. */
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
	public AbstractPointsToSet reachingObjects(AbstractPointsToSet s, final FieldNode f) {
		if (f.isStatic())
			throw new RuntimeException("The parameter f must be an *instance* field.");

		return reachingObjectsInternal(s, f);
	}

	/**
	 * Returns the set of objects pointed to by elements of the arrays in the PointsToSet s.
	 */
	public AbstractPointsToSet reachingObjectsOfArrayElement(AbstractPointsToSet s) {
		return reachingObjectsInternal(s, ArrayElement.v());
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
		final AbstractPointsToSet ret = setFactory.newSet((f instanceof SootField) ? ((SootField) f).getType() : null,
				this);
		bases.forall(new P2SetVisitor() {
			public final void visit(Node n) {
				Node nDotF = ((AllocNode) n).dot(f);
				if (nDotF != null)
					ret.addAll(nDotF.getP2Set(), null);
			}
		});
		return ret;
	}

	private final ArrayNumberer<AllocNode> allocNodeNumberer = new ArrayNumberer<AllocNode>();

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

	private final ArrayNumberer<AllocDotField> allocDotFieldNodeNumberer = new ArrayNumberer<AllocDotField>();

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
}