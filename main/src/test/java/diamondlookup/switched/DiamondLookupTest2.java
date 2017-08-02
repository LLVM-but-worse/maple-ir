package diamondlookup.switched;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DiamondLookupTest2 {

	@Test
	public void test() {
//		System.out.println(new EmptySpeakImpl2().speak());
		System.out.println(new EmptySpeakImplChild2().speak());
//		assertEquals(new EmptySpeakImpl2().speak(), "ISpeak2 Speaking!");
		assertEquals(new EmptySpeakImplChild2().speak(), "ISpeak2 Speaking!");
	}
}
