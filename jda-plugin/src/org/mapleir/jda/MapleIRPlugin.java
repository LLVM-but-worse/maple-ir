package org.mapleir.jda;

import the.bytecode.club.jda.api.Plugin;
import the.bytecode.club.jda.decompilers.Decompiler;

public class MapleIRPlugin implements Plugin {
	public static Decompiler MAPLEIR = new IRDecompiler();
	
	public MapleIRPlugin() {
		System.out.println("maple-ir plugin loaded");
	}
	
	public static void main(String[] args) {
		new MapleIRPlugin();
	}
	
	@Override
	public int onGUILoad() {
		System.out.println("On gui load");
		return 0;
	}
	
	@Override
	public int onExit() {
		System.out.println("On exit");
		return 0;
	}
}
