package greenscripter.gtml.jflap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import greenscripter.gtml.simulator.MachineGraph;
import greenscripter.gtml.simulator.MachineGraph.Transition;

public class MachineToJFlap {

	public static void main(String[] args) throws IOException {
		MachineGraph graph = new MachineGraph(new File("compiledmachinemangled.gtm"));
		//replace multi character symbols
		Set<String> allSymbols = new HashSet<>();
		Map<String, String> replace = new HashMap<>();
		for (List<Transition> s : graph.transitions.values()) {
			for (Transition t : s) {
				allSymbols.add(t.read);
				allSymbols.add(t.write);
			}
		}
		char at = 32;
		for (String s : allSymbols) {
			if (s.length() > 1 || s.equals("~") || s.equals("!") || s.equals("'") || s.equals("\"") || s.equals("&") || s.equals("<") || s.equals(">")) {
				while (allSymbols.contains(at + "") || at == '~' || at == '!' || at == '\'' || at == '"' || at == '&' || at == '<' || at == '>') {
					at++;
				}
				replace.put(s, at + "");
				at++;
			}
		}

		for (List<Transition> s : graph.transitions.values()) {
			for (Transition t : s) {
				if (replace.containsKey(t.read)) t.read = replace.get(t.read);
				if (replace.containsKey(t.write)) t.write = replace.get(t.write);
			}
		}
		System.out.println(replace);
		try (BufferedWriter output = new BufferedWriter(new FileWriter(new File("outputtest.jff")))) {
			//headers
			output.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><!--Created with JFLAP 7.1.--><structure>");
			output.write("<type>turingbb</type>");
			output.write("<automaton>");

			//body
			Set<String> states = new HashSet<>();
			for (List<Transition> s : graph.transitions.values()) {
				for (Transition t : s) {
					states.add(t.source);
					states.add(t.target);
				}
			}

			Map<String, Integer> ids = new HashMap<>();
			Map<Integer, String> names = new HashMap<>();
			int id = 0;
			for (String s : states) {
				ids.put(s, id);
				names.put(id, s);
				id++;
				output.write("<block id=\"" + ids.get(s) + "\" name=\"" + s.replace("$", "-") + "\">");
				output.write("<tag>" + xmlEscape(s.replace("$", "-")) + "</tag>");
				output.write("<x>0.0</x>");
				output.write("<y>0.0</y>");
				if (graph.initialState.equals(s)) {
					output.write("<initial/>");
				}
				if (graph.acceptingStates.contains(s)) {
					output.write("<final/>");
				}
				output.write("</block>");

			}
			System.out.println(states.size() + " states");

			for (List<Transition> s : graph.transitions.values()) {
				for (Transition t : s) {
					output.write("<transition>");
					output.write("<from>" + xmlEscape(ids.get(t.source) + "") + "</from>");
					output.write("<to>" + xmlEscape(ids.get(t.target) + "") + "</to>");
					output.write("<read>" + xmlEscape(t.read) + "</read>");
					output.write("<write>" + xmlEscape(t.write) + "</write>");
					output.write("<move>" + xmlEscape(t.move + "") + "</move>");
					output.write("</transition>");
				}
			}

			//tails
			output.write("</automaton>");
			output.write("</structure>");
		}

	}

	/*
	"   &quot;
	'   &apos;
	<   &lt;
	>   &gt;
	&   &amp;
	 */
	public static String xmlEscape(String s) {
		return s.replace("\"", "&quot;").replace("'", "&apos;").replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;");
	}

}
