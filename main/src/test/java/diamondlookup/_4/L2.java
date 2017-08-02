package diamondlookup._4;

public interface L2 extends L1 {

	@Override
	default String speak() {
		return "L2";
	}
}