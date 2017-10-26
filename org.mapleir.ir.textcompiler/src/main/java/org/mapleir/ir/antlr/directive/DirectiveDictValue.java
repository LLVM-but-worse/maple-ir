package org.mapleir.ir.antlr.directive;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.mapleir.ir.antlr.source.SourcePosition;

public class DirectiveDictValue extends DirectiveValue {

	public DirectiveDictValue(SourcePosition valueSoucePosition, Map<String, DirectiveValue> value) {
		super(valueSoucePosition, value);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Map<String, DirectiveValue> getValue() {
		return (Map<String, DirectiveValue>) super.getValue();
	}
	
	public DirectiveValue getValue(String key) {
		return getValue().get(key);
	}
	
	@Override
	public String toString() {
		Map<String, DirectiveValue> vals = getValue();
		
		if(vals != null) {
			if(vals.isEmpty()) {
				return "{}";
			} else {
				boolean multiLine = vals.size() > 1;
				
				StringBuilder inner = new StringBuilder();
				
				Iterator<Entry<String, DirectiveValue>> it = vals.entrySet().iterator();
				Entry<String, DirectiveValue> pv = it.next();
				
				if(multiLine) {
					inner.append("  ");
				}
				inner.append(pv.getKey()).append(" = ").append(pv.getValue());
				while(it.hasNext()) {
					pv = it.next();
					
					inner.append(",\n  ");
					inner.append(pv.getKey()).append(" = ").append(pv.getValue());
				}
				
				StringBuilder outer = new StringBuilder();
				outer.append('{');
				
				if(multiLine) {
					outer.append('\n');
				}
				
				outer.append(inner.toString());
				
				if(multiLine) {
					outer.append('\n');
				}
				
				outer.append('}');
				
				return outer.toString();
			}
		} else {
			return "null";
		}
	}
}