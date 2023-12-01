package greenscripter.gtml.simulator;

import java.util.ArrayList;
import java.util.List;

public class Tape {

	List<String> positive = new ArrayList<>();
	List<String> negative = new ArrayList<>();
	int cursor = 0;

	public Tape() {
		positive.add("");
		negative.add("");
	}

	public Tape(List<String> tape) {
		positive.addAll(tape);
		positive.add("");
		negative.add("");
	}

	public String read() {
		if (cursor >= 0) {
			return positive.get(cursor);
		} else {
			return negative.get(-cursor - 1);
		}
	}

	public void write(String s) {
		if (cursor >= 0) {
			positive.set(cursor, s);
		} else {
			negative.set(-cursor - 1, s);
		}
	}

	public void left() {
		cursor--;
		if (cursor < 0 && -cursor - 1 >= negative.size()) {
			negative.add("");
		}
	}

	public void right() {
		cursor++;
		if (cursor >= positive.size()) {
			positive.add("");
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("|");
		int cursorIndex = 0;
		for (int i = negative.size() - 1; i >= 0; i--) {
			if (negative.get(i).equals("")) {
				sb.append(" ");
			} else if (negative.get(i).equals("\n")) {
				sb.append("\\n");
			} else {
				sb.append(negative.get(i));
			}
			if (cursor < 0 && -cursor - 1 == i) {
				cursorIndex = sb.length();
			}
			sb.append("|");
		}
		for (int i = 0; i < positive.size(); i++) {
			if (positive.get(i).equals("")) {
				sb.append(" ");
			} else if (positive.get(i).equals("\n")) {
				sb.append("\\n");
			} else {
				sb.append(positive.get(i));
			}
			if (cursor == i) {
				cursorIndex = sb.length();
			}
			sb.append("|");
		}
		sb.append("\n");
		for (int i = 0; i < cursorIndex - 1; i++) {
			sb.append(" ");
		}
		sb.append("^");
		return sb.toString();
	}

	public String readOutput() {
		int cursorAt = cursor;
		StringBuilder sb = new StringBuilder();
		while (!read().equals("")) {
			sb.append(read());
			right();
		}
		cursor = cursorAt;
		return sb.toString();
	}

	public List<String> readOutputList() {
		List<String> result = new ArrayList<>();
		int cursorAt = cursor;
		while (!read().equals("")) {
			result.add(read());
			right();
		}
		cursor = cursorAt;
		return result;
	}
}
