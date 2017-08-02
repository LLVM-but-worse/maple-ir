package diamondlookup;

public interface ISpeak {
	default String speak() {
		return "ISpeak Speaking!";
	}
}