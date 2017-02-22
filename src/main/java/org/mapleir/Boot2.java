package org.mapleir;

import java.applet.Applet;
import java.applet.AppletContext;
import java.applet.AppletStub;
import java.applet.AudioClip;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mapleir.ir.ControlFlowGraphDumper;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.stdlib.klass.ClassNodeUtil;
import org.mapleir.stdlib.klass.library.ApplicationClassSource;
import org.mapleir.stdlib.klass.library.InstalledRuntimeClassSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.blocksplit.SplitMethodWriterDelegate;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.banalysis.asm.insn.InstructionPrinter;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

public class Boot2 {

	public static void main(String[] args) throws Exception {
//		VirtualRunescapeBrowser browser = new VirtualRunescapeBrowser(new URL("http://oldschool84.runescape.com/"));
	
//		File f = Boot.locateRevFile(133);


		File f = new File("res/gamepack133.jar");
//		System.out.printf("Running from %s%n", f.getAbsolutePath());
		
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		dl.download();
		
		ApplicationClassSource app = new ApplicationClassSource(f.getName().substring(0, f.getName().length() - 4), dl.getJarContents().getClassContents());
		InstalledRuntimeClassSource jre = new InstalledRuntimeClassSource(app);
		app.addLibraries(jre);
		
		// System.out.println("@ " + j);
		for(ClassNode cn : app.iterate()) {
			if(!cn.name.equals("client"))
				continue;
			for(MethodNode m : cn.methods) {
				if(!m.toString().equals("client.o(Ljava/lang/String;Ljava/lang/String;ZI)Lbu;"))
					continue;
//				if(m.toString().equals("client.ro()V")) {

//					System.out.println("transform: " +m + " @" + m.instructions.size());
					ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
					BoissinotDestructor.leaveSSA(cfg);
					cfg.getLocals().realloc(cfg);
					System.out.println(cfg);
					ControlFlowGraphDumper.dump(cfg, m);
					
					InstructionPrinter.consolePrint(m);
//				}
			}
		
		}
		
		// System.out.printf("Linked runtime%n");
		
		ClassLoader cl = new ClassLoader() {
			@Override
			public Class<?> findClass(String name) throws ClassNotFoundException {
				if(name == null) {
					throw new IllegalArgumentException();
				}
				
				String bname = name.replace(".", "/");
				ClassNode node = app.findClassNode(bname);
				
				if(node != null) {
//					System.out.println("Defining " + bname);
					
					ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES, new SplitMethodWriterDelegate()) {
					    @Override
						protected String getCommonSuperClass(String type1, String type2) {
					    	ClassNode ccn = app.findClassNode(type1);
					    	ClassNode dcn = app.findClassNode(type2);
					    	
					    	if(ccn == null) {
					    		ClassNode c = ClassNodeUtil.create(type1);
					    		if(c == null) {
					    			return "java/lang/Object";
					    		}
					    		throw new UnsupportedOperationException(c.toString());
					    	}
					    	
					    	if(dcn == null) {
					    		ClassNode c = ClassNodeUtil.create(type2);
					    		if(c == null) {
					    			return "java/lang/Object";
					    		}
					    		throw new UnsupportedOperationException(c.toString());
					    	}
					    	
					        Set<ClassNode> c = app.getStructures().getSupers(ccn);
					        Set<ClassNode> d = app.getStructures().getSupers(dcn);
					        
					        if(c.contains(dcn))
					        	return type1;
					        
					        if(d.contains(ccn))
					        	return type2;
					        
					        if(Modifier.isInterface(ccn.access) || Modifier.isInterface(dcn.access)) {
					        	// enums as well?
					        	return "java/lang/Object";
					        } else {
					        	do {
					        		ClassNode nccn = app.findClassNode(ccn.superName);
					        		if(nccn == null)
					        			break;
					        		ccn = nccn;
					        		c = app.getStructures().getSupers(ccn);
					        	} while(!c.contains(dcn));
					        	return ccn.name;
					        }
					    }
					};
					
					node.accept(writer);
					byte[] bytes = writer.toByteArray();
					
					return defineClass(name, bytes, 0, bytes.length);
				}
				
				return super.findClass(name);
			}
		};

//		System.out.printf("Loading client%n");
		try {
			Class<?> client = cl.loadClass("client");
			Applet applet = (Applet) client.newInstance();
			System.out.println("success");
			// break mainFor;
		} catch(RuntimeException | ClassFormatError e) {
//			System.out.println("exception thrown: " + e.getMessage());
			e.printStackTrace();
		}
	
//		browser.setApplet(applet);
		
//		new Thread(){
//			@Override
//			public void run() {
//				applet.init();
//				applet.start();
//			}
//		}.start();
//
//		System.out.printf("Create ui%n");
//		JFrame frame = new JFrame("RS #" + f.getName().substring(0, f.getName().length() - 4));
//		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//		
//		JPanel panel = new JPanel(new BorderLayout());
//		panel.add(applet, BorderLayout.NORTH);
//		frame.setContentPane(panel);
//		
//		
//		frame.pack();
//		frame.setVisible(true);

		
	}

	public static class OldschoolCrawler implements IPageCrawler {
		private static final String USER_AGENT = getHttpUserAgent();
		private static final String PARAM_PATTERN = "<param name=\"?([^\\s]+)\"?\\s+value=\"?([^>]*)\"?>";
		private static final String HAS_IE6 = "haveie6";
		private static final char[] DOCUMENT_PUT = "document.write('<app".toCharArray();

		private final Map<String, String> parameterMap;
		private final Map<String, String> appletMap;

		public OldschoolCrawler(URL pageURL) throws IOException {
			parameterMap = new HashMap<>();
			appletMap = new HashMap<>();

			String data = downloadPage(pageURL, null);
			parseGenericApplet(data);
			parseDocument(data);
		}

		private void parseGenericApplet(String data) throws IOException {
			Pattern pattern = Pattern.compile(PARAM_PATTERN, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			Matcher matcher = pattern.matcher(data);
			while (matcher.find()) {
				String key = removeTrailingChar(matcher.group(1), '"');
				String value = removeTrailingChar(matcher.group(2), '"');
				if (!parameterMap.containsKey(key)) {
					parameterMap.put(key, value);
				}
			}

			if (parameterMap.containsKey(HAS_IE6)) {
				parameterMap.remove(HAS_IE6);
			}
			parameterMap.put(HAS_IE6, "0");
		}

		private void parseDocument(String data) {
			char[] chars = data.toCharArray();
			boolean reading = false;
			for (int i = 0; i < chars.length; i++) {
				if (reading) {
					char c = chars[i];
					if (c == '=') {
						int s = dist(chars, i, false, '\'');
						int e = dist(chars, i, true, '\'');
						int s2 = i + s;
						int e2 = i + e;
						String str = data.substring(s2 + 1, e2).trim();
						String[] parts = str.split("=");
						if (parts.length != 2) {
							System.err.println(
									String.format("Malformed document data (i=%d, s=%d, e=%d, s2=%d, e2=%d, str=%s).",
											i, s, e, s2, e2, str));
						} else {
							appletMap.put(parts[0], parts[1]);
						}
						i = e2 + 1;
					} else if (c == '>') {
						reading = false;
						return;
					}
				} else {
					reading = next(chars, i, DOCUMENT_PUT);
				}
			}
		}

		private static boolean next(char[] chars, int offset, char[] pattern) {
			if ((offset + pattern.length) >= chars.length)
				return false;
			for (int i = offset; i < (offset + pattern.length); i++) {
				char c = chars[i];
				char p = pattern[i - offset];
				if (c != p)
					return false;
			}
			return true;
		}

		private static int dist(char[] chars, int offset, boolean forward, char t) {
			if (forward) {
				for (int i = offset; i < chars.length; i++) {
					char c = chars[i];
					if (c == t)
						return i - offset;
				}
			} else {
				for (int i = offset; i >= 0; i--) {
					char c = chars[i];
					if (c == t)
						return i - offset;
				}
			}
			return -1;
		}

		private static String downloadPage(URL pageURL, String referer) throws IOException {
			HttpURLConnection con = getHttpConnection(pageURL);
			if (referer != null && !referer.isEmpty())
				con.addRequestProperty("Referer", referer);
			BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
			StringBuilder buf = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				buf.append(line);
			}
			reader.close();
			return buf.toString();
		}

		private static String getHttpUserAgent() {
			if (USER_AGENT != null)
				return USER_AGENT;
			String os = "Windows NT 6.1";
			String osn = System.getProperty("os.name");
			if (osn.contains("Mac")) {
				os = "Macintosh; Intel Mac OS X 10_6_6";
			} else if (os.contains("Linux")) {
				os = "X11; Linux x86_64";
			}
			StringBuilder buf = new StringBuilder(125);
			buf.append("Mozilla/5.0 (").append(os).append(")");
			buf.append(" AppleWebKit/534.24 (KHTML, like Gecko) Chrome/11.0.696.60 Safari/534.24");
			return buf.toString();
		}

		private static HttpURLConnection getHttpConnection(URL url) throws IOException {
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
			con.addRequestProperty("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
			con.addRequestProperty("Accept-Encoding", "gzip,deflate");
			con.addRequestProperty("Accept-Language", "en-us,en;q=0.5");
			con.addRequestProperty("Host", url.getHost());
			con.addRequestProperty("User-Agent", getHttpUserAgent());
			con.setConnectTimeout(10000);
			return con;
		}

		private static String removeTrailingChar(String str, char ch) {
			if ((str == null) || str.isEmpty()) {
				return str;
			} else if (str.length() == 1) {
				return str.charAt(0) == ch ? "" : str;
			}
			try {
				int l = str.length() - 1;
				if (str.charAt(l) == ch) {
					return str.substring(0, l);
				}
				return str;
			} catch (Exception e) {
				return str;
			}
		}

		@Override
		public Map<String, String> getGameParameters() {
			return parameterMap;
		}

		@Override
		public Map<String, String> getAppletParameters() {
			return appletMap;
		}
	}

	public static class VirtualRunescapeBrowser extends AbstractVirtualGameBrowser {

		private static final Dimension DEFAULT_DIMENSION = new Dimension(765, 503);

		private final URL pageURL;
		private final URL gamepackURL;

		public VirtualRunescapeBrowser(URL pageURL) throws IOException {
			super(new OldschoolCrawler(pageURL));
			this.pageURL = pageURL;
			if (!getCrawler().getAppletParameters().containsKey("archive"))
				throw new IOException("No archive.");

			gamepackURL = new URL(pageURL.toExternalForm() + getCrawler().getAppletParameters().get("archive"));
		}

		@Override
		public synchronized void setApplet(Applet applet) {
			if (applet == null)
				return;

			super.setApplet(applet);

			Map<String, String> params = getCrawler().getAppletParameters();
			int newWidth = asInt(params, "width", DEFAULT_DIMENSION.width);
			int newHeight = asInt(params, "height", DEFAULT_DIMENSION.height);

			applet.setPreferredSize(new Dimension(newWidth, newHeight));
			applet.setSize(new Dimension(newWidth, newHeight));

			// appletResize(newHeight, newWidth);
		}

		private static int asInt(Map<String, String> params, String key, int val) {
			if (!params.containsKey(key))
				return val;

			String p = params.get(key);
			try {
				if (p.endsWith("%"))
					p = p.substring(0, p.length() - 1);
				int ip = Integer.parseInt(p);
				if (ip <= 0 /* || ip > 100 */) {
					System.err.printf("Applet %s = %d?.%n", key, ip);
					return val;
				} else {
					return val * (ip / 100);
				}
			} catch (NumberFormatException e) {
				System.err.printf("Map value for %s wasn't a valid int.%n", key);
				e.printStackTrace();
				return val;
			} catch (NullPointerException e) {
				System.err.printf("Map value for %s was null.%n", key);
				e.printStackTrace();
				return val;
			}
		}

		@Override
		public AudioClip getAudioClip(URL url) {
			return null;
		}

		@Override
		public void showDocument(URL url, String target) {
			System.out.printf("Applet at [%s] attempting to show %s.%n", url, target);
		}

		@Override
		public void showStatus(String status) {
			System.out.printf("Applet status: %s.%n", status);
		}

		@Override
		public URL getDocumentBase() {
			return pageURL;
		}

		@Override
		public URL getCodeBase() {
			return pageURL;
		}

		public URL getGamepackURL() {
			return gamepackURL;
		}
	}

	public static abstract class AbstractVirtualGameBrowser implements IVirtualGameBrowser {

		private final transient Map<URL, WeakReference<Image>> imageCache;
		private final transient Map<String, InputStream> inputCache;
		private final IPageCrawler crawler;
		private volatile transient Applet appletRef;

		public AbstractVirtualGameBrowser(IPageCrawler crawler) {
			imageCache = new HashMap<>();
			inputCache = Collections.synchronizedMap(new HashMap<String, InputStream>(2));
			this.crawler = crawler;
			appletRef = null;
		}

		public AbstractVirtualGameBrowser(IPageCrawler crawler, Applet applet) {
			imageCache = new HashMap<>();
			inputCache = Collections.synchronizedMap(new HashMap<String, InputStream>(2));
			this.crawler = crawler;

			setApplet(applet);
		}

		@Override
		public abstract AudioClip getAudioClip(URL url);

		@Override
		public abstract void showDocument(URL url, String target);

		@Override
		public abstract void showStatus(String status);

		@Override
		public abstract URL getDocumentBase();

		@Override
		public abstract URL getCodeBase();

		@Override
		public String getParameter(String name) {
			String value = crawler.getGameParameters().get(name);
			if (value == null) {
				System.err.printf("Null parameter value for key=%s.%n", name);
				return "";
			} else {
				return value;
			}
		}

		@Override
		public synchronized void appletResize(int width, int height) {
			if (appletRef != null) {
				Dimension size = new Dimension(width, height);
				appletRef.setSize(size);
				appletRef.setPreferredSize(size);
			}
		}

		@Override
		public synchronized void setApplet(Applet applet) {
			appletRef = applet;
			applet.setStub(this);
		}

		@Override
		public Image getImage(URL url) {
			synchronized (imageCache) {
				WeakReference<Image> ref = imageCache.get(url);
				Image img;
				if ((ref == null) || ((img = ref.get()) == null)) {
					img = Toolkit.getDefaultToolkit().createImage(url);
					ref = new WeakReference<>(img);
					imageCache.put(url, ref);
				}
				return img;
			}
		}

		@Override
		public Applet getApplet(String name) {
			String thisName = getParameter("name");
			if (thisName == null)
				return null;
			return thisName.equals(name) ? appletRef : null;
		}

		@Override
		public Enumeration<Applet> getApplets() {
			Vector<Applet> applets = new Vector<>();
			applets.add(appletRef);
			return applets.elements();
		}

		@Override
		public void showDocument(URL url) {
			showDocument(url, "");
		}

		@Override
		public void setStream(String key, InputStream stream) throws IOException {
			inputCache.put(key, stream);
		}

		@Override
		public InputStream getStream(String key) {
			return inputCache.get(key);
		}

		@Override
		public Iterator<String> getStreamKeys() {
			return Collections.unmodifiableSet(inputCache.keySet()).iterator();
		}

		@Override
		public boolean isActive() {
			return appletRef != null;
		}

		@Override
		public AppletContext getAppletContext() {
			return this;
		}

		@Override
		public IPageCrawler getCrawler() {
			return crawler;
		}
	}

	public static interface IVirtualGameBrowser extends AppletStub, AppletContext {

		/**
		 * Syncs an Applet with this browser instance. <br>
		 * Syncing in this case may refer to caching or calls to {@link Applet#setStub(AppletStub)}.
		 * 
		 * @param applet The new Applet.
		 */
		public void setApplet(Applet applet);

		@Override
		public AudioClip getAudioClip(URL url);

		@Override
		public Image getImage(URL url);

		@Override
		public Applet getApplet(String name);

		@Override
		public Enumeration<Applet> getApplets();

		@Override
		public void showDocument(URL url);

		@Override
		public void showDocument(URL url, String target);

		@Override
		public void showStatus(String status);

		@Override
		public void setStream(String key, InputStream stream) throws IOException;

		@Override
		public InputStream getStream(String key);

		@Override
		public Iterator<String> getStreamKeys();

		@Override
		public boolean isActive();

		@Override
		public URL getDocumentBase();

		@Override
		public URL getCodeBase();

		@Override
		public String getParameter(String name);

		/**
		 * Each virtual game browser is associated with a webpage which embeds the Applet that is going to be ran. Therefore a {@link IPageCrawler} is needed to parse the parameters for the Applet environment and the Applet itself.
		 * 
		 * @return The crawler.
		 */
		public IPageCrawler getCrawler();

		@Override
		public AppletContext getAppletContext();

		@Override
		public void appletResize(int width, int height);
	}

	public static interface IPageCrawler {

		/**
		 * @return A value-pair map of Applet usable parameters.
		 */
		public Map<String, String> getGameParameters();

		/**
		 * @return A value-pair map of Applet environment parameters.
		 */
		public Map<String, String> getAppletParameters();
	}
}