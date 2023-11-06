package greenscripter.gtml.simulator;

import java.util.ArrayList;
import java.util.List;

import java.io.File;
import java.io.IOException;

import greenscripter.gtml.assembly.Assembler;
import greenscripter.gtml.simulator.MachineGraph.Transition;

public class Simulator {

	public static void main(String[] args) throws IOException {
		long compileStart = System.currentTimeMillis();

		Assembler assembler = new Assembler(new File("defaultfunctions.gtma"));
		MachineGraph graph = assembler.outputGraph;
		//		MachineGraph graph = new MachineGraph(new File("outputtestmangled.gtm"));
		graph.write(new File("outputtest.gtm"), true);
		System.out.println(graph.initialState);
		System.out.println(graph.acceptingStates);
		Simulator simulator = new Simulator(graph);
		simulator.loadTape("abababbaabaabababbbb");
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
		graph.mangleNames();
		graph.write(new File("outputtestmangled.gtm"), true);

	}

	MachineGraph graph;
	Tape tape = new Tape();
	boolean terminated = false;
	int steps = 0;
	String state;

	public Simulator(MachineGraph graph) {
		this.graph = graph;
		state = graph.initialState;
	}

	public void step() {
		steps++;
		System.out.println(state);
		System.out.println(tape.toString());
		List<Transition> transitions = graph.transitions.get(state);
		if (transitions == null) {
			terminated = true;
			return;
		}
		String element = tape.read();
		for (Transition t : transitions) {
			if (t.read.equals(element)) {
				tape.write(t.write);
				switch (t.move) {
					case LEFT:
						tape.left();
						break;
					case RIGHT:
						tape.right();
						break;
					case STAY:
						break;
					default:
						break;

				}
				if (t.lineNumber != 0) System.out.println("Line: " + t.lineNumber + " machine code: " + t);
				if (t.lineNumber == 0) System.out.println("Machine code: " + t);
				state = t.target;
				return;
			}
		}
		System.out.println("No transitions from state: " + transitions);
		terminated = true;
	}

	public boolean isTerminated() {
		return terminated;
	}

	public boolean isAccepting() {
		return terminated && graph.acceptingStates.contains(state);
	}

	public String getResult() {
		return tape.readOutput();
	}

	public void loadTape(List<String> tape) {
		this.tape = new Tape(tape);
	}

	public void loadTape(String tape) {
		List<String> letters = new ArrayList<>();
		for (int i = 0; i < tape.length(); i++) {
			letters.add(tape.charAt(i) + "");
		}
		loadTape(letters);
	}
}
