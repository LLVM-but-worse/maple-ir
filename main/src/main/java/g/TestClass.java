package g;

public class TestClass {

	TestClass global = null;
	
	String k;
	
	TestClass self;
	Exception data;
	String data2;
	
	public TestClass(String s) {
		k = s;
		self = this;
		data = new Exception();
	}
	
	String init() {
		try {
			return data2 = data2.toString();
		} catch(Exception e) {
			data = e;
			throw e;
		}
	}
	
	void run() {
		System.out.println(data2);
		
		if(global == this) {
			System.out.println("ye");
		} else {
			TestClass c2 = new TestClass("v" + hashCode());
			c2.run();
		}
	}
	
	public static void main(String[] args) {
		TestClass c = new TestClass("val");
		try {
			args[0] = c.init();
		} catch(Exception e) {
			e.printStackTrace();
		}
		c.run();
		
		System.out.println(args[0]);
	}
}
