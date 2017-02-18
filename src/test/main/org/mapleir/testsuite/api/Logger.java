package org.mapleir.testsuite.api;

public final class Logger {

	public enum Severity {
		DEBUG, WARN, ERROR, FATAL
	}
	
	public static void debug(Object o) {
		log(o, Severity.DEBUG);
	}
	
	public static void warn(Object o) {
		log(o, Severity.WARN);
	}
	
	public static void error(String msg, Throwable t) {
		System.err.println(msg);
		System.err.println(t);
	}
	
	public static void error(Object o) {
		log(o, Severity.ERROR);
	}

	// TODO:
	// private static Severity loggerSeverity = Severity.DEBUG;
	
	public static void log(Object o, Severity severity) {
//		if(severity.ordinal() >= loggerSeverity.ordinal()) {
//			
//		}
//		if(o instanceof Throwable) {
//			
//		} else {
//			
//		}
		
		System.out.printf("[%s]:: %s.%n", severity.name(), String.valueOf(o));
	}
}