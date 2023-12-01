package greenscripter.gtml.simulator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.io.File;
import java.io.IOException;

import greenscripter.gtml.simulator.MachineGraph.Transition;
import greenscripter.gtml.simulator.MachineGraph.Transition.Move;

public class MachineGenerator {

	public static void main(String[] args) throws IOException {
		long compileStart = System.currentTimeMillis();

		MachineGraph m = new MachineGraph();
		m.initialState = "initial";
		m.acceptingStates = new ArrayList<>();
		m.acceptingStates.add("accept");
		m.inputSymbols = new ArrayList<>();
		m.inputSymbols.addAll(List.of("0", "1", "L", "S", "C"));
		m.tapeSymbols = new ArrayList<>();

		List<Transition> trans = new ArrayList<>();
		/*initial,R,,,start
		initial,R,L,,initial
		initial,R,C,,initial
		initial,AUR,initial*/

		//init loop
		trans.add(mkTrans("initial", Move.LEFT, "", "", "scount0"));
		trans.add(mkTrans("initial", Move.RIGHT, "L", "", "initial"));
		trans.add(mkTrans("initial", Move.RIGHT, "C", "", "initial"));

		trans.add(mkTrans("initial", Move.RIGHT, "S", "S", "initial"));
		trans.add(mkTrans("initial", Move.RIGHT, "0", "0", "initial"));
		trans.add(mkTrans("initial", Move.RIGHT, "1", "1", "initial"));

		//count S instances
		for (int i = 0; i < 8; i++) {
			trans.add(mkTrans("scount" + i, Move.LEFT, "S", "0", "scount" + (i + 1)));
			if (i == 0) {
				trans.add(mkTrans("scount" + i, Move.STAY, "0", "0", "load0s"));
				trans.add(mkTrans("scount" + i, Move.STAY, "1", "1", "load0s"));
				trans.add(mkTrans("scount" + i, Move.LEFT, "", "", "blank0"));
			} else {
				trans.add(mkTrans("scount" + i, Move.LEFT, "0", "", "load" + (i) + "s"));
				trans.add(mkTrans("scount" + i, Move.LEFT, "1", "", "load" + (i) + "s"));
				trans.add(mkTrans("scount" + i, Move.LEFT, "", "", "blank0"));
			}

		}

		//Handle 8 S
		moveTamper("scount8", Move.STAY, trans);
		repoint("scount8", "accept", trans);

		//Handle all S deletion
		for (int i = 2; i < 8; i++) {
			trans.add(mkTrans("load" + (i) + "s", Move.LEFT, "0", "", "load" + (i) + "s" + (i - 1)));
			trans.add(mkTrans("load" + (i) + "s", Move.LEFT, "1", "", "load" + (i) + "s" + (i - 1)));
			trans.add(mkTrans("load" + (i) + "s", Move.LEFT, "", "", "blank0"));
			for (int j = 2; j < i; j++) {
				trans.add(mkTrans("load" + i + "s" + j, Move.LEFT, "0", "", "load" + (i) + "s" + (j - 1)));
				trans.add(mkTrans("load" + i + "s" + j, Move.LEFT, "1", "", "load" + (i) + "s" + (j - 1)));
				trans.add(mkTrans("load" + i + "s" + j, Move.LEFT, "", "", "blank0"));
			}
		}

		//redirect no-ops
		repoint("load1s", "load1s1", trans);
		repoint("load0s", "load0s1", trans);

		//Handle skip all digits
		for (int i = 0; i < 8; i++) {
			trans.add(mkTrans("blank" + i, Move.LEFT, "0", "0", "blank" + (i + 1)));
			trans.add(mkTrans("blank" + i, Move.LEFT, "1", "0", "blank" + (i + 1)));
			trans.add(mkTrans("blank" + i, Move.LEFT, "", "0", "blank" + (i + 1)));
			trans.add(mkTrans("blank" + i, Move.LEFT, "S", "0", "blank" + (i + 1)));
		}

		//convert blank8 to accept
		moveTamper("blank8", Move.STAY, trans);
		repoint("blank8", "accept", trans);

		//Add blank returns if all input consumed
		for (int i = 0; i < 8; i++) {
			trans.add(mkTrans("load" + i + "s1", Move.LEFT, "", "", "blank0"));
		}

		//Map load exit points
		for (int i = 0; i < 8; i++) {
			repoint("load" + i + "s1", "loadnext" + i + "r" + (8 - i), trans);
		}

		//Handle truncate load
		for (int i = 0; i < 8; i++) {
			for (int j = 1; j <= (8 - i); j++) {
				trans.add(mkTrans("loadnext" + i + "r" + j, Move.LEFT, "1", "1", "loadnext" + i + "r" + (j - 1)));
				trans.add(mkTrans("loadnext" + i + "r" + j, Move.LEFT, "0", "0", "loadnext" + i + "r" + (j - 1)));
				trans.add(mkTrans("loadnext" + i + "r" + j, Move.LEFT, "", "0", "blank" + (8 - (i + j - 1))));
			}
			repoint("loadnext" + i + "r0", "blank" + (8 - i), trans);
		}

		//Handle 0 extend load
		for (int i = 0; i < 8; i++) {
			for (int j = 1; j <= (8 - i); j++) {
				trans.add(mkTrans("fill" + i + "r" + j, Move.LEFT, "1", "0", "blank" + (8 - (i + j - 1))));
				trans.add(mkTrans("fill" + i + "r" + j, Move.LEFT, "0", "0", "blank" + (8 - (i + j - 1))));
				trans.add(mkTrans("fill" + i + "r" + j, Move.LEFT, "", "0", "blank" + (8 - (i + j - 1))));
				trans.add(mkTrans("fill" + i + "r" + j, Move.LEFT, "S", "0", "blank" + (8 - (i + j - 1))));
			}
			repoint("fill" + i + "r0", "blank" + (8 - i), trans);
		}

		//Translate no blanks left to accept
		moveTamper("blank8", Move.STAY, trans);
		repoint("blank8", "accept", trans);

		//Cleanup extra states
		Set<String> seen = new HashSet<>();
		seen.add(m.initialState);
		boolean anyRemoved = true;
		while (anyRemoved) {
			anyRemoved = false;
			for (int i = 0; i < trans.size(); i++) {
				Transition t = trans.get(i);
				if (seen.contains(t.source)) {
					anyRemoved |= seen.add(t.target);
				}
			}
		}
		for (int i = 0; i < trans.size(); i++) {
			Transition t = trans.get(i);
			if (!seen.contains(t.source)) {
				trans.remove(i);
				i--;
			}
		}

		//Build machine
		for (Transition t : trans) {
			List<Transition> result = m.transitions.get(t.source);
			if (result == null) {
				result = new ArrayList<>();
				m.transitions.put(t.source, result);
			}
			result.add(t);
		}

		m.write(new File("generated.gtm"), false);

		MachineGraph graph = new MachineGraph(new File("generated.gtm"));
		graph.write(new File("outputtest.gtm"), true);
		System.out.println(graph.initialState);
		System.out.println(graph.acceptingStates);
		Simulator simulator = new Simulator(graph);
		simulator.loadTape("L101101110SS");
		long start = System.currentTimeMillis();

		while (!simulator.isTerminated()) {
			simulator.step();
			//			if (simulator.steps > 50) break;
		}
		System.out.println(simulator.isAccepting());
		System.out.println("Result: " + simulator.getResult());
		System.out.println("Steps: " + simulator.steps);
		System.out.println("Compile time: " + (start - compileStart) + " ms.");
		System.out.println("Runtime: " + (System.currentTimeMillis() - start) + " ms.");
	}

	public static void repoint(String dest, String newDest, List<Transition> trans) {
		for (Transition t : trans) {
			if (t.target.equals(dest)) {
				t.target = newDest;
			}
		}
	}

	public static void moveTamper(String dest, Move newMove, List<Transition> trans) {
		for (Transition t : trans) {
			if (t.target.equals(dest)) {
				t.move = newMove;
			}
		}
	}

	public static Transition mkTrans(String source, Move m, String read, String write, String dest) {
		Transition t = new Transition();
		t.source = source;
		t.target = dest;
		t.read = read;
		t.write = write;
		t.move = m;
		return t;
	}
}
