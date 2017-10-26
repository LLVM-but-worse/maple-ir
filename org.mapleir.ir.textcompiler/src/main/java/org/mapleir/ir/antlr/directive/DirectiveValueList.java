package org.mapleir.ir.antlr.directive;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.mapleir.ir.antlr.source.SourcePosition;

public class DirectiveValueList extends DirectiveValue implements Iterable<DirectiveValue> {

	public DirectiveValueList(SourcePosition valueSourcePosition, List<DirectiveValue> values) {
		super(valueSourcePosition, Collections.unmodifiableList(values));
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public List<DirectiveValue> getValue() {
		return (List<DirectiveValue>) super.getValue();
	}
	
	@Override
	public String toString() {
		List<DirectiveValue> vals = getValue();
		
		if(vals != null) {
			if(vals.isEmpty()) {
				return "[]";
			} else {
				StringBuilder sb = new StringBuilder();
				sb.append("[");
				
				Iterator<DirectiveValue> it = vals.iterator();
				DirectiveValue pv = it.next();
				
				sb.append(pv.toString());
				
				while(it.hasNext()) {
					sb.append(", ").append(it.next().toString());
				}
				
				return sb.append("]").toString();
			}
		} else {
			return "null";
		}
	}

	@Override
	public Iterator<DirectiveValue> iterator() {
		return getValue().iterator();
	}
}