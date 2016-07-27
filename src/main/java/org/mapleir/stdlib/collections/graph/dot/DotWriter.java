package org.mapleir.stdlib.collections.graph.dot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public class DotWriter<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> extends TabbedStringWriter {
	
	private static final String GRAPHVIZ_DOT_PATH = "F:/Program Files (x86)/Graphviz2.38/bin/dot.exe";
	private static final File GRAPH_FOLDER = new File("cfg testing");
	
	protected final DotConfiguration<G, N, E> config;
	protected final G graph;
	private String name;
	
	public DotWriter(DotConfiguration<G, N, E> config, G graph) {
		this.config = config;
		this.graph = graph;
	}
	
	public String getName() {
		return name;
	}
	
	public DotWriter<G, N, E> setName(String name) {
		this.name = name;
		return this;
	}
	
	private void writeSettings(Map<String, Object> properties) {
		Iterator<Entry<String, Object>> it = properties.entrySet().iterator();
		while(it.hasNext()) {
			Entry<String, Object> e2 = it.next();
			Object val = e2.getValue();
			print(e2.getKey()).print(" = ");
			// string parameters need to be escaped.
			if(val instanceof Number) {
				print(val.toString());
			} else if(val instanceof String) {
				esc(val.toString());
			} else {
				throw new UnsupportedOperationException("Key: " + e2.getKey() + ", val: " + val);
			}
			
			if(it.hasNext()) {
				print(", ");
			}
		}
	}
	
	private void writeGlobalSettings() {
		for(Entry<String, Map<String, Object>> e : config.getGlobalProperties().entrySet()) {
			print(e.getKey()).print(" [");
			writeSettings(e.getValue());
			print("]").newLine();
		}
	}
	
	protected boolean printable(N n) {
		return true;
	}
	
	protected boolean printable(N n, E e) {
		return true;
	}
	
	public Map<String, Object> getNodeProperties(N n) {
		return null;
	}
	
	public Map<String, Object> getEdgeProperties(N n, E e) {
		return null;
	}
	
	private void writeNodes() {
		for(N n : graph.vertices()) {
			if(!printable(n)) {
				continue;
			}
			
			print(n.getId()).print(" [");
			Map<String, Object> nprops = getNodeProperties(n);
			if(nprops != null) {
				writeSettings(nprops);
			}
			print("]").newLine();
		}
	}
	
	private void writeEdges() {
		for(N n : graph.vertices()) {
			if(!printable(n)) {
				continue;
			}
			
			for(E e : graph.getEdges(n)) {
				if(!printable(n, e)) {
					continue;
				}
				
				print(e.src.getId()).print(" -> ").print(e.dst.getId()).print(" ").print("[");
				Map<String, Object> eprops = getEdgeProperties(n, e);
				if(eprops != null) {
					writeSettings(eprops);
				}
				print("];").newLine();
			}
		}
	}
	
	public String toDotString() {
		clear();
		
		print(config.getType().toString());
		if(name != null) {
			print(" ").esc(name);
		}
		print(" {");
		tab();
		newLine();
		
		writeGlobalSettings();
		writeNodes();
		writeEdges();
		
		untab();
		newLine();
		print("}");
		
		return toString();
	}
	
	public DotWriter<G, N, E> esc() {
		return print("\"");
	}
	
	public DotWriter<G, N, E> esc(String s) {
		return esc().print(s).esc();
	}

	public DotWriter<G, N, E> newLine() {
		return print(System.lineSeparator());
	}
	
	public DotWriter<G, N, E> print(String s) {
		super.print(s);
		return this;
	}
	
	// '\', '/', ':', '*', '?', '<', '>', '|'
	private static String escapeFileName(String name) {
		return name.replaceAll("[/\\:*?\"<>|]", "");
	}
	
	public void export() {
		String fname = escapeFileName(name);
		
		File dotFile = new File(GRAPH_FOLDER, fname + "x.gv");
		if(dotFile.exists())
			dotFile.delete();
		
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(dotFile))){
			String d = toDotString();
			System.err.println(d);
			bw.write(d);
			bw.close();
			File gv = new File(GRAPHVIZ_DOT_PATH);
			File imgFile = new File(GRAPH_FOLDER, fname + "y.png");
			if (imgFile.exists())
				imgFile.delete();
			ProcessBuilder builder = new ProcessBuilder('"' + gv.getAbsolutePath() + '"', "-Tpng", '"' + dotFile.getAbsolutePath() + '"', "-o", '"' + imgFile.getAbsolutePath() + '"');
			for(String s : builder.command()) {
				System.out.print(s + " ");
			}
			System.out.println();
			Process process = builder.start();
			process.waitFor();
			
			BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream(), "UTF-8"));
			String line;
			System.err.flush();
			while((line = br.readLine()) != null) {
				System.err.println(line);
				char[] chars = line.toCharArray();
				for(int i=0; i < chars.length; i++) {
					char c = chars[i];
					
					if(i > 0 && chars[i - 1] == '\'') {
						System.err.println();
						System.err.println("char: " + c);
						System.err.println("set: " + Character.getName(c));
						Character c1 = c;
						System.err.println("id: " + (int)c);
						System.err.println("id: " + (int)c1.charValue());
					} else {
						System.err.print(c);
					}
				}
				System.err.println();
			}
		} catch (IOException | InterruptedException e) {
			System.err.println("Exception while exporting graph " + fname + ":");
			e.printStackTrace();;
		}
	}
}