package org.mapleir.stdlib.collections.graph.dot;

import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

public class DotWriter<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> extends TabbedStringWriter {
	
	private static final String GRAPHVIZ_DOT_PATH = "dot/dot.exe";
	private static final File GRAPH_FOLDER = new File("cfg testing");
	
	private static final CharsetEncoder UTF8_ENCODER = Charset.forName("UTF-8").newEncoder();
	
	protected final DotConfiguration<G, N, E> config;
	protected final G graph;
	private String name;
	public final List<DotPropertyDecorator<G, N, E>> decorators;
	
	public DotWriter(DotConfiguration<G, N, E> config, G graph) {
		this.config = config;
		this.graph = graph;
		decorators = new ArrayList<>();
	}
	
	public String getName() {
		return name;
	}
	
	public DotWriter<G, N, E> setName(String name) {
		this.name = name;
		return this;
	}
	
	public DotWriter<G, N, E> addDecorator(DotPropertyDecorator<G, N, E> decorator) {
		decorators.add(decorator);
		return this;
	}
	
	public DotWriter<G, N, E> addDecorator(int index, DotPropertyDecorator<G, N, E> decorator) {
		decorators.add(index, decorator);
		return this;
	}
	
	public DotWriter<G, N, E> removeDecorator(Class<? extends DotPropertyDecorator<G, N, E>> type) {
		Iterator<DotPropertyDecorator<G, N, E>> it = decorators.iterator();
		while (it.hasNext()) {
			DotPropertyDecorator<G, N, E> decorator = it.next();
			if (type.isAssignableFrom(decorator.getClass()))
				it.remove();
		}
		return this;
	}
	
	public DotWriter<G, N, E> clearDecorators() {
		decorators.clear();;
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
	
	private boolean printable(N n) {
		AtomicBoolean printable = new AtomicBoolean(true);
		for (DotPropertyDecorator<G, N, E> decorator : decorators)
			decorator.decorateNodePrintability(graph, n, printable);
		return printable.get();
	}
	
	private boolean printable(N n, E e) {
		AtomicBoolean printable = new AtomicBoolean(true);
		for (DotPropertyDecorator<G, N, E> decorator : decorators)
			decorator.decorateEdgePrintability(graph, n, e, printable);
		return printable.get();
	}
	
	private void writeNodes() {
		for(N n : graph.vertices()) {
			if(!printable(n)) {
				continue;
			}
			
			print(n.getId()).print(" [");
			Map<String, Object> nprops = new HashMap<>();
			for (DotPropertyDecorator<G, N, E> decorator : decorators)
				decorator.decorateNodeProperties(graph, n, nprops);
			writeSettings(nprops);
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
				
				print(e.src.getId()).print(config.getType().getEdgeArrow()).print(e.dst.getId()).print(" ").print("[");
				Map<String, Object> eprops = new HashMap<>();
				for (DotPropertyDecorator<G, N, E> decorator : decorators)
					decorator.decorateEdgeProperties(graph, n, e, eprops);
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