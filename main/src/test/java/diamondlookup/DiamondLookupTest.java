package diamondlookup;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DiamondLookupTest {

	@Test
	public void test() {
		assertEquals(new EmptySpeakImpl().speak(), "ISpeak2 Speaking!");
		assertEquals(new EmptySpeakImplChild().speak(), "ISpeak2 Speaking!");
	}
}