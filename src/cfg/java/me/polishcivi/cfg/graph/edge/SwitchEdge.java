package me.polishcivi.cfg.graph.edge;

import java.util.List;

import me.polishcivi.cfg.graph.ICFGEdge;

public class SwitchEdge implements ICFGEdge {

	private final int key;
	private final boolean dflt;

	public SwitchEdge(int key) {
		this(key, false);
	}
	
	public SwitchEdge(boolean dflt) {
		if(!dflt) {
			throw new IllegalArgumentException("can't init non-default edge without key");
		} else {
			this.dflt = dflt;
			this.key = 0;
		}
	}
	
	public SwitchEdge(int key, boolean dflt) {
		this.key = key;
		this.dflt = dflt;
	}
	
	public int getKey() {
		if(dflt) {
			throw new UnsupportedOperationException("default edge has no key");
		} else {
			return key;
		}
	}
	
	public boolean isDefault() {
		return dflt;
	}

	@Override
	public String label() {
		return "default edge";
	}

	@Override
	public ICFGEdge clone() {
		if(dflt) {
			return new SwitchEdge(true);
		} else {
			throw new UnsupportedOperationException("can't close non-default edge with a key");
		}
	}

	@Override
	public boolean checkEquality(ICFGEdge other) {
		return dflt && (other instanceof SwitchEdge);
	}

	public static class TableSwitchEdge extends SwitchEdge {
		private final int min, max;

		public TableSwitchEdge(int key, int min, int max) {
			super(key);
			this.min = min;
			this.max = max;
		}

		public int getMin() {
			return min;
		}

		public int getMax() {
			return max;
		}

		@Override
		public String label() {
			return String.format("tableswitch (%d <-> %d, key=%d)", min, max, getKey());
		}

		@Override
		public ICFGEdge clone() {
			return new TableSwitchEdge(min, max, getKey());
		}

		@Override
		public boolean checkEquality(ICFGEdge other) {
			return other instanceof TableSwitchEdge;
		}
	}

	public static class LookupSwitchEdge extends SwitchEdge {
		private final List<Integer> keys;

		public LookupSwitchEdge(int key, List<Integer> keys) {
			super(key);
			this.keys = keys;
		}

		public List<Integer> getKeys() {
			return keys;
		}

		@Override
		public String label() {
			return String.format("lookupswitch (%s, key=%d)", keys, getKey());
		}

		@Override
		public ICFGEdge clone() {
			return new LookupSwitchEdge(getKey(), keys);
		}

		@Override
		public boolean checkEquality(ICFGEdge other) {
			return other instanceof LookupSwitchEdge;
		}
	}
}