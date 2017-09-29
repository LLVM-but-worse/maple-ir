package org.mapleir.propertyframework.util;

import org.mapleir.propertyframework.api.IProperty;
import org.mapleir.propertyframework.api.IPropertyDictionary;
import org.mapleir.propertyframework.impl.PropertyDictionaryImpl;

public class PropertyHelper {
	private static final IPropertyDictionary EMPTY_DICTIONARY = new PropertyDictionaryImpl() {
		@Override
		public void put(IProperty<?> property) {
			throw new UnsupportedOperationException("Immutable dictionary");
		}
	};
	
	public static IPropertyDictionary getEmptyDictionary() {
		return EMPTY_DICTIONARY;
	}
	
	public static IPropertyDictionary createDictionary() {
		return new PropertyDictionaryImpl();
	}
}