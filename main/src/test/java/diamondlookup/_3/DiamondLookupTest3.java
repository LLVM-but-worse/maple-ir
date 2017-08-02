package diamondlookup._3;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DiamondLookupTest3 {

	@Test
	public void test() {
		assertEquals(new X().speak(), "speak:I");
		assertEquals(new Z().speak(), null);
	}
}
