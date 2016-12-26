import the.bytecode.club.jda.decompilers.Decompiler;

public class Plugin {
	public static Decompiler MAPLEIR = new IRDecompiler();
	
	public Plugin() {
		System.out.println("maple-ir plugin loaded");
	}
}
