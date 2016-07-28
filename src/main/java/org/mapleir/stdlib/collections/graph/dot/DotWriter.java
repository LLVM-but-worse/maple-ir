package org.mapleir.stdlib.collections.graph.dot;

import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.collections.ListCreator;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class DotWriter<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> extends TabbedStringWriter {
	
	private static final String GRAPHVIZ_DOT_PATH = "dot/dot.exe";
	private static final File GRAPH_FOLDER = new File("cfg testing");
	
	private static final CharsetEncoder UTF8_ENCODER = Charset.forName("UTF-8").newEncoder();
	
	protected final DotConfiguration<G, N, E> config;
	protected final G graph;
	private String name;
	private final NullPermeableHashMap<N, List<String>> vertexStartComments;
	private final NullPermeableHashMap<N, List<String>> vertexEndComments;
	private final Map<N, String> vertexColors;
	
	public DotWriter(DotConfiguration<G, N, E> config, G graph) {
		this.config = config;
		this.graph = graph;
		vertexStartComments = new NullPermeableHashMap<>(new ListCreator<>());
		vertexEndComments = new NullPermeableHashMap<>(new ListCreator<>());
		vertexColors = new HashMap<>();
	}
	
	public String getName() {
		return name;
	}
	
	public DotWriter<G, N, E> setName(String name) {
		this.name = name;
		return this;
	}
	
	public DotWriter addStartComment(N n, String comment) {
		vertexStartComments.getNonNull(n).add(comment);
		return this;
	}
	
	public DotWriter addEndComment(N n, String comment) {
		vertexEndComments.getNonNull(n).add(comment);
		return this;
	}
	
	public DotWriter setColor(N n, String color) {
		vertexColors.put(n, color);
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
				processNodeProperties(n, nprops);
				writeSettings(nprops);
			}
			print("]").newLine();
		}
	}
	
	private void processNodeProperties(N n, Map<String, Object> nprops) {
		if (nprops.containsKey("label")) {
			StringBuilder sb = new StringBuilder();
			for (String comment : vertexStartComments.getNonNull(n))
				sb.append("// ").append(comment).append("\\l");
			sb.append(nprops.get("label").toString());
			sb.append("\\l");
			for (String comment : vertexEndComments.getNonNull(n))
				sb.append("// ").append(comment).append("\\l");
			nprops.put("label", sb.toString());
		}
		if (vertexColors.containsKey(n)) {
			nprops.put("style", "filled");
			nprops.put("fillcolor", vertexColors.get(n));
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
				
				print(e.src.getId()).print(config.getType().getEdgeArrow()).print(e.dst.getId()).print(" ").print("[");
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
		
		print(config.getType().getPrintType());
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
		
		File dotFile = new File(GRAPH_FOLDER, fname + ".gv");
		if(dotFile.exists())
			dotFile.delete();
		
		try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(dotFile))){
			String d = toDotString();
			System.out.println(d);
			ByteBuffer buf = UTF8_ENCODER.encode(CharBuffer.wrap(d));
			byte[] dst = new byte[buf.remaining()];
			buf.get(dst);
			dos.write(dst);

			File gv = new File(GRAPHVIZ_DOT_PATH);
			File imgFile = new File(GRAPH_FOLDER, fname + ".png");
			if (imgFile.exists())
				imgFile.delete();
			ProcessBuilder builder = new ProcessBuilder('"' + gv.getAbsolutePath() + '"', "-Tpng", '"' + dotFile.getAbsolutePath() + '"', "-o", '"' + imgFile.getAbsolutePath() + '"');
			Process process = builder.start();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			System.err.println("Exception while exporting graph " + fname + ":");
			e.printStackTrace();;
		}
	}
}