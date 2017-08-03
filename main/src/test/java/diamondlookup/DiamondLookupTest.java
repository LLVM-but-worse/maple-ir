package diamondlookup;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DiamondLookupTest {

	@Test
	public void test() {
		assertEquals(new EmptySpeakImpl().speak(), "ISpeak2 Speaking!");
		assertEquals(new EmptySpeakImplChild().speak(), "ISpeak2 Speaking!");
		assertEquals(new EmptySpeakImplChild2().speak(), "EmptySpeakImplChild2 Speaking!");
		assertEquals(new EmptySpeakImplChild3().speak(), "EmptySpeakImplChild2 Speaking!");
		System.out.println(new BB().speak());
		
		ISpeak speaker;

		speaker = new EmptySpeakImpl();
		assertEquals(speaker.speak(), "ISpeak2 Speaking!");
		speaker = new EmptySpeakImplChild3();
		assertEquals(speaker.speak(), "EmptySpeakImplChild2 Speaking!");
	}
}
