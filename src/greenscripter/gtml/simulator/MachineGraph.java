package greenscripter.gtml.simulator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class MachineGraph {

	public List<String> inputSymbols;
	public List<String> tapeSymbols;
	public List<String> acceptingStates;

	public String initialState;
	public Map<String, List<Transition>> transitions = new HashMap<>();

	public static void main(String[] args) throws IOException {
		MachineGraph graph = new MachineGraph(new File("testmachine.gtm"));
		System.out.println(graph.transitions);
	}

	public MachineGraph() {

	}

	public MachineGraph(File file) throws IOException {
		try (BufferedReader input = new BufferedReader(new FileReader(file))) {
			inputSymbols = splitCommas(input.readLine());
			tapeSymbols = splitCommas(input.readLine());

			List<String> stateData = splitCommas(input.readLine());

			if (stateData.size() % 2 != 0) {
				throw new RuntimeException("Invalid state configuration, " + stateData.size() + " is not even.");
			}
			acceptingStates = new ArrayList<>();
			initialState = null;
			for (int i = 0; i < stateData.size(); i += 2) {
				String name = stateData.get(i);
				if (stateData.get(i + 1).equals("I")) {
					if (initialState == null) {
						initialState = name;
					} else {
						throw new RuntimeException("Duplicate initial states: " + initialState + ", " + name);
					}
				}
				if (stateData.get(i + 1).equals("A")) {
					if (acceptingStates.contains(name)) {
						throw new RuntimeException("Duplicate accept marks for state " + name);
					}
					acceptingStates.add(name);
				}
			}
			String line;
			while ((line = input.readLine()) != null) {
				List<String> parts = splitCommas(line);
				if (parts.size() == 5) {
					String source = parts.get(0);
					String read = parts.get(1);
					String write = parts.get(2);
					String move = parts.get(3);
					String dest = parts.get(4);

					Transition trans = new Transition();
					trans.read = read;
					trans.write = write;
					trans.move = switch (move) {
						case "L" -> Transition.Move.LEFT;
						case "R" -> Transition.Move.RIGHT;
						case "S" -> Transition.Move.STAY;
						default -> throw new RuntimeException("Unexpected transition type: " + move);
					};
					trans.target = dest;
					trans.source = source;

					List<Transition> others = transitions.get(source);
					if (others == null) {
						others = new ArrayList<>();
						transitions.put(source, others);
					}
					for (Transition other : others) {
						if (other.read.equals(trans.read)) {
							throw new RuntimeException("Duplicate transitions for symbol " + other.read + " on state " + source);
						}
					}
					others.add(trans);
				}
			}
		}
	}

	public void write(File file) throws IOException {
		try (BufferedWriter output = new BufferedWriter(new FileWriter(file))) {
			output.write(mergeCommas(inputSymbols));
			output.write("\n");
			output.write(mergeCommas(tapeSymbols));
			output.write("\n");
			List<String> initFragments = new ArrayList<>();
			initFragments.add(initialState);
			initFragments.add("I");
			for (String s : acceptingStates) {
				initFragments.add(s);
				initFragments.add("A");
			}
			output.write(mergeCommas(initFragments));
			output.write("\n");
			for (List<Transition> state : transitions.values()) {
				for (Transition t : state) {
					output.write(t.toString());
					output.write("\n");
				}
			}
			output.flush();
		}
	}

	public static class Transition {

		public String source;
		public String target;
		public String read;
		public String write;
		public Move move;

		public enum Move {

			LEFT("L"), RIGHT("R"), STAY("S");

			public final String id;

			Move(String id) {
				this.id = id;
			}

			public String toString() {
				return id;
			}
		}

		public String toString() {
			return mergeCommas(List.of(source, read, write, move.toString(), target));
		}
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
		for (String s : parts) {
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

		}
		if (!parts.isEmpty()) {
			sb.setLength(sb.length() - 1);
		}
		return sb.toString();
	}
}
