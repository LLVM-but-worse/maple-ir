package diamondlookup.i5test;

public interface ISpeak2 extends ISpeakX {
	@Override
	default String speak() {
		return "ISpeak2 Speaking!";
	}
}