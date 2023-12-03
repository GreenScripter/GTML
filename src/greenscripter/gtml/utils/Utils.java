package greenscripter.gtml.utils;

import java.util.ArrayList;
import java.util.List;

public class Utils {

	public static List<String> splitString(String input) {
		List<String> letters = new ArrayList<>();
		for (int i = 0; i < input.length(); i++) {
			letters.add(input.charAt(i) + "");
		}
		return letters;
	}

	public static String merge(List<String> input) {
		StringBuilder sb = new StringBuilder();
		for (String s : input) {
			sb.append(s);
		}
		return sb.toString();
	}

	public static List<String> splitCommas(String s) {
		List<String> result = new ArrayList<>();
		String segment = "";
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == ',') {
				result.add(segment);
				segment = "";
			} else if (s.charAt(i) == '\\') {
				if (i + 1 < s.length()) {
					if (s.charAt(i + 1) == 'n') {
						segment += "\n";
					} else {
						segment += s.charAt(i + 1);
					}
					i++;
				} else {
					throw new RuntimeException("Invalid escaping.");
				}
			} else {
				segment += s.charAt(i);
			}
		}
		result.add(segment);
		return result;
	}

	public static String mergeCommas(List<String> parts) {
		StringBuilder sb = new StringBuilder();
		boolean anyParts = false;
		for (String s : parts) {
			if (s == null) continue;
			for (int i = 0; i < s.length(); i++) {
				if (s.charAt(i) == '\n') {
					sb.append("\\n");
				} else {
					if (s.charAt(i) == '\\' || s.charAt(i) == ',') {
						sb.append('\\');
					}
					sb.append(s.charAt(i));
				}
			}
			sb.append(',');
			anyParts = true;

		}
		if (anyParts) {
			sb.setLength(sb.length() - 1);
		}
		return sb.toString();
	}
}
