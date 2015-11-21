package me.polishcivi.cfg.utils;

import me.polishcivi.cfg.graph.ICFGEdge;
import org.jgrapht.Graph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by polish on 21.11.15.
 */
public class DOTExporter {

    /**
     * @param outputFile
     * @param name
     * @throws IOException
     */
    public static <V, E extends ICFGEdge> void exportDOT(File outputFile, String name, Graph<V, E> g) {
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            out.write("digraph g {\n".getBytes());
            out.write(("label = \"" + name + "\"\n").getBytes());

            for (V vertex : g.vertexSet()) {
                out.write((vertex.hashCode() + "[shape = box, label = \"" + vertex.toString() + "\"]\n").getBytes());
            }

            for (E edge : g.edgeSet()) {
                final V source = g.getEdgeSource(edge);
                final V target = g.getEdgeTarget(edge);

                out.write(("" + source.hashCode() + " -> " + target.hashCode()).getBytes());

                final String label = edge.label();
                if (label != null) {
                    out.write((" [label = \"" + label + "\"]").getBytes());
                }
                out.write("\n".getBytes());

            }
            out.write("}\n".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
