package org.mapleir.stdlib.collections.graph.dot;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.util.TabbedStringWriter;

public class DotWriter<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> extends TabbedStringWriter {
	
	private static final String GRAPHVIZ_DOT_PATH = "dot/dot.exe";
	private static final File GRAPH_FOLDER = new File("cfg testing");
	
	private static final CharsetEncoder UTF8_ENCODER = Charset.forName("UTF-8").newEncoder();
	
	private final DotConfiguration<G, N, E> config;
	private final List<String> pipelineOrder;
	private final Map<String, DotPropertyDecorator<G, N, E>> pipeline;
	private final G graph;
	private String name;
	
	public DotWriter(DotConfiguration<G, N, E> config, G graph) {
		this.config = config;
		this.graph = graph;
		pipelineOrder = new ArrayList<>();
		pipeline = new HashMap<>();
	}
	
	private boolean canAppend(String name, DotPropertyDecorator<G, N, E> d) {
		return !pipelineOrder.contains(name) && !pipeline.containsValue(d);
	}
	
	private String genName(DotPropertyDecorator<G, N, E> d) {
		// shouldn't be in the map when this is called.
		Class<?> clazz = d.getClass();
		String cname = clazz.getSimpleName();
		String name = cname;
		int count = 0;
		while(pipelineOrder.contains(name)) {
			name = cname + count;
		}
		return name;
	}
	
	private void add0(int index, String name, DotPropertyDecorator<G, N, E> d) {
		if(canAppend(name, d)) {
			if(name == null) {
				name = genName(d);
			}
			pipelineOrder.add(index, name);
			pipeline.put(name, d);
		}
	}
	
	private void insert0(int index, String pos, String name, DotPropertyDecorator<G, N, E> d) {
		if(pipelineOrder.contains(pos)) {
			add0(index, name, d);
		}
	}
	
	public DotWriter<G, N, E> add(DotPropertyDecorator<G, N, E> d) {
		return add(null, d);
	}
	
	public DotWriter<G, N, E> add(String name, DotPropertyDecorator<G, N, E> d) {
		// there may be 0 elements in the pipeline
		add0(pipelineOrder.size(), name, d);
		return this;
	}

	public DotWriter<G, N, E> addFirst(DotPropertyDecorator<G, N, E> d) {
		add0(0, null, d);
		return this;
	}
	
	public DotWriter<G, N, E> addFirst(String name, DotPropertyDecorator<G, N, E> d) {
		add0(0, name, d);
		return this;
	}
	
	public DotWriter<G, N, E> addAfter(String pos, DotPropertyDecorator<G, N, E> d) {
		insert0(pipelineOrder.indexOf(pos) + 1, pos, null, d);
		return this;
	}

	public DotWriter<G, N, E> addAfter(String pos, String name, DotPropertyDecorator<G, N, E> d) {
		insert0(pipelineOrder.indexOf(pos) + 1, pos, name, d);
		return this;
	}
	
	public DotWriter<G, N, E> addBefore(String pos, DotPropertyDecorator<G, N, E> d) {
		insert0(pipelineOrder.indexOf(pos), pos, null, d);
		return this;
	}
	
	public DotWriter<G, N, E> addBefore(String pos, String name, DotPropertyDecorator<G, N, E> d) {
		insert0(pipelineOrder.indexOf(pos), pos, name, d);
		return this;
	}
	
	public DotWriter<G, N, E> remove(String name) {
		pipelineOrder.remove(name);
		pipeline.remove(name);
		return this;
	}
	
	public DotWriter<G, N, E> removeAll() {
		pipelineOrder.clear();
		pipeline.clear();
		return this;
	}
	
	public DotWriter<G, N, E> remove(DotPropertyDecorator<G, N, E> d) {
		Iterator<Entry<String, DotPropertyDecorator<G, N, E>>> it = pipeline.entrySet().iterator();
		while(it.hasNext()) {
			Entry<String, DotPropertyDecorator<G, N, E>> e = it.next();
			if(e.getValue() == d) {
				String key = e.getKey();
				pipelineOrder.remove(key);
				it.remove();
			}
		}
		return this;
	}
	
	public DotConfiguration<G, N, E> getConfiguration() {
		return config;
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
	
	private boolean isNodePrintable(N n) {
		for(String key : pipelineOrder){
			DotPropertyDecorator<G, N, E> d = pipeline.get(key);
			if(!d.isNodePrintable(graph, n)) {
				return false;
			}
		}
		return true;
	}
	
	private boolean isEdgePrintable(N n, E e) {
		for(String key : pipelineOrder){
			DotPropertyDecorator<G, N, E> d = pipeline.get(key);
			if(!d.isEdgePrintable(graph, n, e)) {
				return false;
			}
		}
		return true;
	}
	
	private void writeNodes() {
		for(N n : graph.vertices()) {
			if(!isNodePrintable(n)) {
				continue;
			}
			
			print(n.getId()).print(" [");
			Map<String, Object> nprops = new HashMap<>();
			for(String key : pipelineOrder){
				DotPropertyDecorator<G, N, E> d = pipeline.get(key);
				d.decorateNodeProperties(graph, n, nprops);
			}
			writeSettings(nprops);
			print("]").newLine();
		}
	}
	
	private void writeEdges() {
		final String arrow = config.getType().getEdgeArrow();
		
		for(N n : graph.vertices()) {
			if(!isNodePrintable(n)) {
				continue;
			}
			
			for(E e : graph.getEdges(n)) {
				if(!isEdgePrintable(n, e)) {
					continue;
				}
				
				print(e.src.getId()).print(arrow).print(e.dst.getId()).print(" ").print("[");
				Map<String, Object> eprops = new HashMap<>();
				for(String key : pipelineOrder){
					DotPropertyDecorator<G, N, E> d = pipeline.get(key);
					d.decorateEdgeProperties(graph, n, e, eprops);
				}
				writeSettings(eprops);
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
		return esc().print(s.replaceAll("\"", "\\\\\"")).esc();
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
		
		if (!GRAPH_FOLDER.exists())
			GRAPH_FOLDER.mkdir();
		
		File dotFile = new File(GRAPH_FOLDER, fname + ".gv");
		if(dotFile.exists())
			dotFile.delete();
		
		try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(dotFile))){
			String d = toDotString();
			ByteBuffer buf = UTF8_ENCODER.encode(CharBuffer.wrap(d));
			byte[] dst = new byte[buf.remaining()];
			buf.get(dst);
			dos.write(dst);

			File gv = new File(GRAPHVIZ_DOT_PATH);
			File imgFile = new File(GRAPH_FOLDER, fname + ".png");
			if (imgFile.exists())
				imgFile.delete();
			ProcessBuilder builder = new ProcessBuilder('"' + gv.getAbsolutePath() + '"', "-Tpng", '"' + dotFile.getAbsolutePath() + '"', "-o", '"' + imgFile.getAbsolutePath() + '"');
			builder.redirectError(ProcessBuilder.Redirect.INHERIT);
			Process process = builder.start();
			builder.redirectError(ProcessBuilder.Redirect.INHERIT);
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			System.err.println("Exception while exporting graph " + fname + ":");
			e.printStackTrace();;
		}
	}
}