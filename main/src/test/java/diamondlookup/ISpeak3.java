package diamondlookup;

public interface ISpeak3 extends ISpeak2 {
	@Override
	default String speak() {
		return "ISpeak3 Speaking!";
	}
}
