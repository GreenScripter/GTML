package greenscripter.gtml.assembly;

import static greenscripter.gtml.simulator.MachineGraph.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import greenscripter.gtml.simulator.MachineGraph;
import greenscripter.gtml.simulator.Simulator;
import greenscripter.gtml.simulator.MachineGraph.Transition;

public class Assembler {

	public MachineGraph outputGraph = new MachineGraph();
	List<GeneralTransition> transitions = new ArrayList<>();

	public static void main(String[] args) throws Exception {
		//		Assembler a = new Assembler(new File("testmachinea1reversesyntax.gtma"));
		//		a.outputGraph.write(new File("outputtest.gtm"), true);
		//		a.outputGraph.mangleNames();
		//		a.outputGraph.write(new File("outputtestmangled.gtm"), true);
		//		System.out.println(removeTransitionOperator("A(hello)RM(1)", 'A', new GeneralTransition()));
		Simulator.main(args);

	}

	public Assembler(File file) throws IOException {
		try (BufferedReader input = new BufferedReader(new FileReader(file))) {
			int lineNumber = 0;

			Set<String> validSymbols = new HashSet<>();
			validSymbols.add("");

			lineNumber++;
			outputGraph.inputSymbols = splitCommas(input.readLine());
			lineNumber++;
			outputGraph.tapeSymbols = splitCommas(input.readLine());
			validSymbols.addAll(outputGraph.inputSymbols);
			validSymbols.addAll(outputGraph.tapeSymbols);

			lineNumber++;
			List<String> stateData = splitCommas(input.readLine());

			if (stateData.size() % 2 != 0) {
				throw new RuntimeException("Invalid state configuration, " + stateData.size() + " is not even.");
			}
			outputGraph.acceptingStates = new ArrayList<>();
			outputGraph.initialState = null;
			for (int i = 0; i < stateData.size(); i += 2) {
				String name = stateData.get(i);
				if (stateData.get(i + 1).equals("I")) {
					if (outputGraph.initialState == null) {
						outputGraph.initialState = name;
					} else {
						throw new RuntimeException("Duplicate initial states: " + outputGraph.initialState + ", " + name);
					}
				}
				if (stateData.get(i + 1).equals("A")) {
					if (outputGraph.acceptingStates.contains(name)) {
						throw new RuntimeException("Duplicate accept marks for state " + name);
					}
					outputGraph.acceptingStates.add(name);
				}
			}

			Map<String, Module> modules = new HashMap<>();
			Module activeModule = null;

			Map<String, Set<String>> unions = new HashMap<>();
			Map<String, Set<String>> moduleUnions = new HashMap<>();

			String line;
			while ((line = input.readLine()) != null) {
				lineNumber++;
				List<String> parts = splitCommas(line);
				//end module definition
				if (parts.size() == 1 && line.equals("endmodule")) {
					if (activeModule == null) {
						throw new RuntimeException("No module to end." + " line: " + lineNumber);
					}
					modules.put(activeModule.name, activeModule);
					moduleUnions.clear();
					activeModule = null;
				} else

				//start module
				if (parts.size() == 1 && line.startsWith("module ") && line.endsWith(":")) {
					String moduleName = line.substring(7, line.length() - 1);
					if (activeModule != null) {
						throw new RuntimeException("Nested modules are not allowed, " + moduleName + " inside " + activeModule.name + " line: " + lineNumber);
					}
					activeModule = new Module();
					activeModule.name = moduleName;

					lineNumber++;
					String inputsetsLine = input.readLine();
					lineNumber++;
					String inputstatesLine = input.readLine();
					if (inputsetsLine == null || inputstatesLine == null) throw new RuntimeException("Input ended mid module." + " line: " + lineNumber);

					List<String> inputsets = splitCommas(inputsetsLine);
					List<String> inputstates = splitCommas(inputstatesLine);

					if (!inputsets.get(0).equals("inputsets") || !inputstates.get(0).equals("inputstates")) {
						throw new RuntimeException("Invalid module declaration for module " + moduleName + " line: " + lineNumber);
					}

					activeModule.inputs.addAll(inputsets.subList(1, inputsets.size()));
					activeModule.inputStates.addAll(inputstates.subList(1, inputstates.size()));
				} else

				//union
				if (parts.size() > 1 && parts.get(0).startsWith("union ")) {
					String name = parts.get(0).substring(6);
					if (activeModule == null && unions.containsKey(name)) {
						throw new RuntimeException("Duplicate union " + name + " line: " + lineNumber);
					}
					if (activeModule != null && moduleUnions.containsKey(name)) {
						throw new RuntimeException("Duplicate union " + name + " in module " + activeModule.name + " line: " + lineNumber);
					}
					Set<String> symbols = new HashSet<>();
					for (int i = 1; i < parts.size(); i++) {
						String part = parts.get(i);
						if (moduleUnions.containsKey(part)) {
							symbols.addAll(moduleUnions.get(part));
						} else if (unions.containsKey(part)) {
							symbols.addAll(unions.get(part));
						} else {
							symbols.add(part);
						}
					}
					if (activeModule == null) {
						unions.put(name, symbols);
					}
					if (activeModule != null) {
						moduleUnions.put(name, symbols);
					}
				} else

				//transition
				if (parts.size() >= 3) {
					String source = parts.get(0);
					String controlCode = parts.get(1);

					GeneralTransition trans = new GeneralTransition();
					trans.lineNumber = lineNumber;
					trans.source = source;
					trans.transition = controlCode;

					boolean unchanged = removeTransitionOperator(controlCode, 'U', trans).found;
					boolean any = removeTransitionOperator(controlCode, 'A', trans).found;

					//read and write parts
					int part = 2;
					if (!any) {
						trans.read = parts.get(part);
						part++;
					}
					if (!unchanged) {
						if (parts.size() <= part) throw new RuntimeException("Missing components in " + line + " line: " + lineNumber);
						trans.write = parts.get(part);
						part++;
					}
					if (parts.size() <= part) throw new RuntimeException("Missing components in " + line + " line: " + lineNumber);
					if (parts.get(part).startsWith("$")) {
						trans.destination = mergeCommas(parts.subList(part, parts.size()));
					} else {
						trans.destination = parts.get(part);
						part++;
						if (parts.size() > part) throw new RuntimeException("Excess content in line " + line + " line: " + lineNumber);
					}
					trans.unions.putAll(moduleUnions);
					trans.unions.putAll(unions);
					//undo unions
					List<GeneralTransition> unioned = new ArrayList<>();

					if (moduleUnions.containsKey(trans.read)) {
						for (String symbol : moduleUnions.get(trans.read)) {
							GeneralTransition copy = trans.copy();
							copy.read = symbol;
							unioned.add(copy);
						}
					}
					if (unions.containsKey(trans.read)) {
						for (String symbol : unions.get(trans.read)) {
							GeneralTransition copy = trans.copy();
							copy.read = symbol;
							unioned.add(copy);
						}
					}

					//allow write unions if they only contain one symbol
					if (moduleUnions.containsKey(trans.write)) {
						if (moduleUnions.get(trans.write).size() > 1) {
							throw new RuntimeException("Attempt to write a union with multiple characters in " + line + " line: " + lineNumber);
						}
						for (String symbol : moduleUnions.get(trans.write)) {
							GeneralTransition copy = trans.copy();
							copy.write = symbol;
							unioned.add(copy);
						}
					}
					if (unions.containsKey(trans.write)) {
						if (unions.get(trans.write).size() > 1) {
							throw new RuntimeException("Attempt to write a union with multiple characters in " + line + " line: " + lineNumber);
						}
						for (String symbol : unions.get(trans.write)) {
							GeneralTransition copy = trans.copy();
							copy.write = symbol;
							unioned.add(copy);
						}
					}

					//If there are no unions, add the base transition
					if (unioned.isEmpty()) {
						unioned.add(trans);
					}

					if (activeModule != null) {
						activeModule.transitions.addAll(unioned);
					} else {
						transitions.addAll(unioned);
					}

				}
			}

			System.out.println("Instructions: ");
			transitions.forEach(System.out::println);
			System.out.println();

			for (Module m : modules.values()) {
				System.out.println("Module " + m.name + ":");
				System.out.println("Inputs: " + m.inputs);
				System.out.println("State Inputs: " + m.inputStates);
				m.transitions.forEach(System.out::println);
				System.out.println();
			}

			//apply modules
			applyModules(modules);

			System.out.println("Modules Applied: ");
			transitions.forEach(System.out::println);
			System.out.println();
			for (Module m : modules.values()) {
				System.out.println("Module " + m.name + ":");
				System.out.println("Inputs: " + m.inputs);
				System.out.println("State Inputs: " + m.inputStates);
				m.transitions.forEach(System.out::println);
				System.out.println();
			}
			//apply * transitions
			applyStarTransitions();

			System.out.println("Stars Applied: ");
			transitions.forEach(System.out::println);
			System.out.println();

			//apply A transitions
			applyATransitions(validSymbols);

			System.out.println("As Applied: ");
			transitions.forEach(System.out::println);
			System.out.println();

			//apply U transitions
			applyUTransitions();

			System.out.println("Us Applied: ");
			transitions.forEach(System.out::println);
			System.out.println();

			//apply MD transitions
			applyMDTransitions();

			System.out.println("MDs Applied: ");
			transitions.forEach(System.out::println);
			System.out.println();

			//optimize transitions
			optimize();

			System.out.println("Optimized: ");
			transitions.forEach(System.out::println);
			System.out.println();

			//map to simple turing machine
			for (GeneralTransition trans : transitions) {
				List<Transition> result = outputGraph.transitions.get(trans.source);
				if (result == null) {
					result = new ArrayList<>();
					outputGraph.transitions.put(trans.source, result);
				}

				Transition t = new Transition();
				t.move = switch (trans.transition) {
					case "L" -> Transition.Move.LEFT;
					case "R" -> Transition.Move.RIGHT;
					case "S" -> Transition.Move.STAY;
					default -> throw new RuntimeException("Unexpected transition type: " + trans.transition + " in " + trans.getErrored() + " line: " + trans.lineNumber);
				};
				t.read = trans.read;
				t.write = trans.write;
				t.source = trans.source;
				t.target = trans.destination;
				t.lineNumber = trans.lineNumber;

				if (!validSymbols.contains(t.read)) {
					throw new RuntimeException("Undefined tape symbol: " + t.read + " in " + trans.getErrored() + " line: " + trans.lineNumber);
				}
				if (!validSymbols.contains(t.write)) {
					throw new RuntimeException("Undefined tape symbol: " + t.write + " in " + trans.getErrored() + " line: " + trans.lineNumber);
				}

				boolean duplicate = false;
				for (Transition other : result) {
					if (other.read.equals(t.read)) {
						duplicate = true;
						System.err.println("Warning: Duplicate transitions for symbol " + other.read + " on state " + t.source + " in " + trans.getErrored() + " line: " + trans.lineNumber);
					}
				}
				if (!duplicate) result.add(t);
			}

		}
	}

	private void applyModules(Map<String, Module> modules) {
		for (Module m : modules.values()) {
			List<String> notAllowed = new ArrayList<>();
			notAllowed.add(m.name);
			applyModulesTo(modules, m.transitions, notAllowed);
		}
		applyModulesTo(modules, transitions, new ArrayList<>());
	}

	private void applyModulesTo(Map<String, Module> modules, List<GeneralTransition> transitions, List<String> notAllowed) {
		for (int i = 0; i < transitions.size(); i++) {
			GeneralTransition trans = transitions.get(i);
			if (trans.destination == null) continue;
			if (trans.destination.startsWith("$")) {
				trans.saveTransformation();

				List<String> parts = splitCommas(trans.destination);
				String name = parts.remove(0).substring(1);

				if (notAllowed.contains(name)) {
					throw new RuntimeException("Invalid looping call to module " + name + " in " + trans.getErrored() + " line: " + trans.lineNumber);
				}

				Module target = modules.get(name);
				if (target == null) {
					throw new RuntimeException("Module " + name + " does not exist." + " in " + trans.getErrored() + " line: " + trans.lineNumber);
				}
				if (parts.size() != target.inputs.size() + target.inputStates.size()) {
					throw new RuntimeException("Wrong number of fields in call to module " + name + " with " + parts + " in " + trans.getErrored() + " line: " + trans.lineNumber);
				}

				List<String> notAllowedNext = new ArrayList<>(notAllowed);
				notAllowedNext.add(name);
				applyModulesTo(modules, target.transitions, notAllowedNext);

				List<String> inputs = parts.subList(0, target.inputs.size());
				List<String> states = parts.subList(target.inputs.size(), parts.size());
				for (GeneralTransition substep : target.transitions) {
					GeneralTransition copy = substep.copy();

					if (target.inputStates.contains(copy.destination)) {
						copy.destination = states.get(target.inputStates.indexOf(copy.destination));
					} else {
						copy.destination = trans.source + "$" + trans.read + "$" + name + "$" + copy.destination;
					}
					copy.source = trans.source + "$" + trans.read + "$" + name + "$" + copy.source;

					if (target.inputs.contains(copy.write)) {
						copy.write = inputs.get(target.inputs.indexOf(copy.write));
						if (trans.unions.containsKey(copy.write)) {
							if (trans.unions.get(copy.write).size() > 1) {
								throw new RuntimeException("Attempt to write a union with multiple characters in module call to " + target.name + " via " + trans.getErrored() + " line: " + trans.lineNumber);
							}
							for (String symbol : trans.unions.get(copy.write)) {
								copy.write = symbol;
							}
						}
					}
					if (target.inputs.contains(copy.read)) {
						copy.read = inputs.get(target.inputs.indexOf(copy.read));
						if (trans.unions.containsKey(copy.read)) {
							for (String symbol : trans.unions.get(copy.read)) {
								GeneralTransition copycopy = copy.copy();
								copycopy.read = symbol;
								i++;
								transitions.add(i, copycopy);
							}
							continue;
						}
					}
					i++;
					transitions.add(i, copy);
				}
				trans.destination = trans.source + "$" + trans.read + "$" + name + "$initial";
			}
		}
	}

	private void applyStarTransitions() {
		for (int i = 0; i < transitions.size(); i++) {
			GeneralTransition trans = transitions.get(i);
			ParsedTO operation = removeTransitionOperator(trans.transition, '*', trans);
			if (operation.found) {
				trans.saveTransformation();
				int count = Integer.parseInt(operation.content.get(0));
				trans.transition = operation.removed;
				i++;
				for (int j = 1; j < count - 1; j++) {
					GeneralTransition copy = trans.copy();
					copy.destination = trans.source + "$part" + j;
					copy.source = trans.source + "$part" + (j - 1);
					transitions.add(i, copy);
					i++;
				}

				GeneralTransition copy = trans.copy();
				copy.destination = trans.destination;
				copy.source = trans.source + "$part" + (count - 2);
				transitions.add(i, copy);
				i++;

				trans.destination = trans.source + "$part" + 0;
				i--;
			}
		}
	}

	private void applyATransitions(Set<String> possibleSymbols) {
		for (int i = 0; i < transitions.size(); i++) {
			GeneralTransition trans = transitions.get(i);
			ParsedTO operation = removeTransitionOperator(trans.transition, 'A', trans);
			if (operation.found) {
				transitions.remove(i);
				Set<String> inUse = new HashSet<>();
				for (GeneralTransition t : transitions) {
					if (t.source.equals(trans.source)) {
						inUse.add(t.read);
					}
				}

				boolean anyAdded = false;
				for (String s : possibleSymbols) {
					if (inUse.contains(s)) continue;
					GeneralTransition copy = trans.copy();
					copy.read = s;
					copy.transition = operation.removed;
					transitions.add(i, copy);
					anyAdded = true;
					i++;
				}
				if (!anyAdded) {
					System.err.println("Warning: Invalid A transition with no states in " + trans.getErrored() + " line: " + trans.lineNumber);
				}
				i--;
			}
		}
	}

	private void applyUTransitions() {
		for (int i = 0; i < transitions.size(); i++) {
			GeneralTransition trans = transitions.get(i);
			ParsedTO operation = removeTransitionOperator(trans.transition, 'U', trans);
			if (operation.found) {
				trans.saveTransformation();
				trans.transition = operation.removed;
				trans.write = trans.read;
			}
		}
	}

	private void applyMDTransitions() {
		for (int i = 0; i < transitions.size(); i++) {
			GeneralTransition trans = transitions.get(i);
			ParsedTO operation = removeTransitionOperator(trans.transition, 'M', trans);
			if (operation.found && !trans.mVisited) {
				if (operation.content.isEmpty()) {
					throw new RuntimeException("Invalid M transition with no arguments " + trans.getErrored() + " line: " + trans.lineNumber);
				}
				if (operation.content.size() > 1) {
					throw new RuntimeException("Invalid M transition with too many arguments " + trans.getErrored() + " line: " + trans.lineNumber);
				}
				ParsedTO checkD = removeTransitionOperator(trans.transition, 'D', trans);
				if (checkD.found && checkD.content.contains(operation.content.get(0))) {
					throw new RuntimeException("M and D transition for the same name in " + trans.getErrored() + " line: " + trans.lineNumber);
				}

				String variable = operation.content.get(0);
				String value = trans.read;
				String append = "$store" + variable + "$" + value;

				TPair set = getMDSet(trans, operation.content.get(0));
				for (int j = 0; j < transitions.size(); j++) {
					GeneralTransition t = transitions.get(j);
					if (!set.source.contains(t)) continue;
					GeneralTransition copy = t.copy();
					copy.source += append;
					copy.destination += append;
					if (copy.read.equals(variable)) {
						copy.read = value;
					}
					if (copy.write.equals(variable)) {
						copy.write = value;
					}
					if (!checkExists(copy)) {
						transitions.add(j + 1, copy);
						j++;
					}
				}

				for (int j = 0; j < transitions.size(); j++) {
					GeneralTransition t = transitions.get(j);
					if (!set.dest.contains(t)) continue;
					GeneralTransition copy = t.copy();
					copy.source += append;
					if (copy.read.equals(variable)) {
						copy.read = value;
					}
					if (copy.write.equals(variable)) {
						copy.write = value;
					}
					if (!checkExists(copy)) {
						transitions.add(j + 1, copy);
						j++;
					}
				}
				trans.saveTransformation();
				trans.destination += append;

			}
		}

		for (int i = 0; i < transitions.size(); i++) {
			GeneralTransition trans = transitions.get(i);
			ParsedTO operation = removeTransitionOperator(trans.transition, 'D', trans);
			if (operation.found) {
				trans.saveTransformation();
				trans.transition = operation.removed;
			}
			operation = removeTransitionOperator(trans.transition, 'M', trans);
			if (operation.found) {
				trans.saveTransformation();
				trans.transition = operation.removed;
			}
		}
	}

	private boolean checkExists(GeneralTransition trans) {
		for (int i = 0; i < transitions.size(); i++) {
			GeneralTransition t = transitions.get(i);
			if (t.source.equals(trans.source) && t.read.equals(trans.read)) {
				return true;
			}
		}
		return false;
	}

	private TPair getMDSet(GeneralTransition trans, String name) {
		Set<GeneralTransition> resultDest = new HashSet<>();
		Set<GeneralTransition> resultSource = new HashSet<>();
		Set<GeneralTransition> next = new HashSet<>();
		next.add(trans);
		while (!next.isEmpty()) {
			Iterator<GeneralTransition> it = next.iterator();
			GeneralTransition current = it.next();
			it.remove();
			for (int i = 0; i < transitions.size(); i++) {
				GeneralTransition t = transitions.get(i);
				if (t.source.equals(current.destination) && !resultDest.contains(t)) {
					ParsedTO operation = removeTransitionOperator(t.transition, 'D', t);
					if (!operation.found || (operation.content != null && !operation.content.contains(name))) {
						operation = removeTransitionOperator(t.transition, 'M', t);
						if (!operation.found || (operation.content != null && !operation.content.contains(name))) {
							next.add(t);
							resultSource.add(t);
						}
					}
					resultDest.add(t);
				}
			}
		}
		resultDest.removeAll(resultSource);
		return new TPair(resultDest, resultSource);
	}

	record TPair(Set<GeneralTransition> dest, Set<GeneralTransition> source) {

	}

	private void optimize() {
		//optimize away AUS transitions
		for (int i = 0; i < transitions.size(); i++) {
			GeneralTransition trans = transitions.get(i);
			if (trans.read.equals(trans.write) && trans.transition.equals("S")) {
				boolean others = false;
				for (GeneralTransition t : transitions) {
					if (t.source.equals(trans.source) && !(t.read.equals(t.write) && t.transition.equals("S"))) {
						others = true;
						break;
					}
				}
				if (!others) {
					if (trans.source.equals(outputGraph.initialState)) {
						outputGraph.initialState = trans.destination;
					}
					System.out.println("Removing: " + trans.getErrored());
					transitions.remove(i);
					i--;
					for (GeneralTransition t : transitions) {
						if (t.destination.equals(trans.source)) {
							t.saveTransformation();
							t.destination = trans.destination;
						}
					}
				}
			}
		}
		//remove unreachable states
		boolean anyRemoved = true;
		while (anyRemoved) {
			anyRemoved = false;
			for (int i = 0; i < transitions.size(); i++) {
				GeneralTransition trans = transitions.get(i);
				boolean seen = outputGraph.initialState.equals(trans.source);
				if (!seen) for (GeneralTransition t : transitions) {
					if (t == trans) continue;
					if (t.destination.equals(trans.source)) {
						seen = true;
						break;
					}
				}
				if (!seen) {
					anyRemoved = true;
					transitions.remove(i);
					i--;
				}
			}
		}
	}

	public static class Module {

		String name;
		List<String> inputs = new ArrayList<>();
		List<String> inputStates = new ArrayList<>();
		List<GeneralTransition> transitions = new ArrayList<>();

		public Module() {

		}

	}

	private static ParsedTO removeTransitionOperator(String transition, char operator, GeneralTransition line) {
		String removed = "";
		List<String> content = new ArrayList<>();
		String contentSegment = "";
		boolean found = false;

		boolean inTarget = false;
		boolean inParentheses = false;
		for (int i = 0; i < transition.length(); i++) {
			if (transition.charAt(i) == '(') {
				if (inParentheses) {
					throw new RuntimeException("No nested parentheses in transitions " + line.getErrored() + " line: " + line.lineNumber);
				} else {
					inParentheses = true;
				}
				if (!inTarget) {
					removed += transition.charAt(i);
				}
			} else if (transition.charAt(i) == ')') {
				if (!inParentheses) {
					throw new RuntimeException("Extra closing parentheses in transition " + line.getErrored() + " line: " + line.lineNumber);
				} else {
					inParentheses = false;
				}
				if (!inTarget) {
					removed += transition.charAt(i);
				} else {
					content.add(contentSegment);
					contentSegment = "";
				}
			} else if (inTarget && inParentheses) {
				contentSegment += transition.charAt(i);
			} else if (inParentheses) {
				removed += transition.charAt(i);
			} else if (transition.charAt(i) == operator) {
				if (found) {
					throw new RuntimeException("Duplicate " + operator + " in transition " + line.getErrored() + " line: " + line.lineNumber);
				}
				inTarget = true;
				found = true;
			} else {
				if (inTarget) inTarget = false;
				removed += transition.charAt(i);
			}

		}
		return new ParsedTO(removed, operator, content, found);
	}

	private record ParsedTO(String removed, char operator, List<String> content, boolean found) {
	}

	public static class GeneralTransition {

		String source;
		String destination;
		String transition;
		String read;
		String write;
		Map<String, Set<String>> unions = new HashMap<>();
		GeneralTransition parent;
		int lineNumber;
		boolean mVisited = false;

		public GeneralTransition copy() {
			GeneralTransition copy = new GeneralTransition();
			copy.destination = destination;
			copy.source = source;
			copy.transition = transition;
			copy.read = read;
			copy.write = write;
			copy.unions.putAll(this.unions);
			copy.mVisited = mVisited;
			copy.parent = this;
			copy.lineNumber = lineNumber;
			return copy;
		}

		public void saveTransformation() {
			GeneralTransition copy = copy();
			copy.parent = parent;
			parent = copy;
		}

		public String getErrored() {
			if (parent == null) {
				return toString();
			}
			return parent.getErrored() + " -> " + toString();
		}

		public String toString() {
			List<String> values = new ArrayList<>();
			values.add(source);
			values.add(transition);
			values.add(read);
			values.add(write);
			values.add(destination);
			return mergeCommas(values);
		}
	}
}
