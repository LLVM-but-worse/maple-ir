package org.mapleir.ir.antlr.util;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.mapleir.ir.antlr.internallex.LexerException;
import org.mapleir.ir.antlr.internallex.Lexer;
import org.objectweb.asm.Opcodes;

public class AccessHelper {

	public enum AccessItem {
		CLASS("public", "private", "protected", "final", "super", "interface", "abstract", "synthetic", "enum"),
		FIELD(
				"public", "private", "protected", "static", "final", "volatile", "transient", "synthetic",
				"enum"), 
		METHOD("public", "private", "protected", "static", "final", "synchronized", "bridge",
						"varargs", "native", "abstract", "strict", "synthetic");

		private final Map<String, Integer> supportedModifiers;
		
		private AccessItem(String... keys) {
			supportedModifiers = Collections.unmodifiableMap(readFromOpcodes(keys));
		}
		
		public boolean isRecgonisedModifier(String key) {
			return supportedModifiers.containsKey(key);
		}
		
		public int getModifierValue(String key) {
			return supportedModifiers.get(key);
		}
		
		private static Map<String, Integer> readFromOpcodes(String[] keys) {
			Map<String, Integer> map = new HashMap<>();
			
			Class<?> opcodesClass = Opcodes.class;
			
			for(String k : keys) {
				String fieldName = "ACC_" + (k.toUpperCase());
				
				try {
					Field f = opcodesClass.getDeclaredField(fieldName);
					int val = f.getInt(null);
					map.put(k.toLowerCase(), val);
					map.put(k.toLowerCase(), val);
					map.put(fieldName, val);
					map.put(fieldName.toLowerCase(), val);
				} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}
			
			return map;
		}
	}

	public static int decodeAccessString(String input, AccessItem item) throws ParseException {
		if (input == null || input.isEmpty()) {
			throw new IllegalArgumentException("No modifiers");
		}
		
		int mods = 0;

		String[] tokens = input.split(" ");

		for (String t : tokens) {
			t = t.trim();
			if (!t.isEmpty()) {
				if(item.isRecgonisedModifier(t)) {
					mods |= item.getModifierValue(t);
				} else {
					mods |= lexModifiers(t);
				}
			}
		}
		
		return mods;
	}
	
	private static int lexModifiers(String input) throws ParseException {
		Lexer lexer = new Lexer(input.toCharArray());
		
		try {
			lexer.nextToken();
		} catch(LexerException e) {
			throw new ParseException(e.getMessage(), e.getBufferPointer());
		}
		
		switch(lexer.getTokenKind()) {
			case INTLIT: {
				try {
					return Conversion.asInt(lexer.asString(), lexer.getRadix());
				} catch (NumberFormatException e) {
					throw new ParseException(e.getMessage(), lexer.getBufferPointer());
				}
			}
			default:
				throw new UnsupportedOperationException("Modifier must be an integer constant");
		}
	}
}