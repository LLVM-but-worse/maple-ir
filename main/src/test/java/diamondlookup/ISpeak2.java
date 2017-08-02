package diamondlookup;

public interface ISpeak2 extends ISpeak {
	@Override
	default String speak() {
		return "ISpeak2 Speaking!";
	}
}