package greenscripter.gtml.simulator;

import java.util.ArrayList;
import java.util.List;

import java.io.File;
import java.io.IOException;

import greenscripter.gtml.simulator.MachineGraph.Transition;

public class Simulator {

	public static void main(String[] args) throws IOException {
		MachineGraph graph = new MachineGraph(new File("outputtestmangled.gtm"));
		graph.write(new File("outputtest.gtm"), false);
		System.out.println(graph.initialState);
		System.out.println(graph.acceptingStates);
		System.out.println(graph.transitions);
		Simulator simulator = new Simulator(graph);
		simulator.loadTape("ababa");
		while (!simulator.isTerminated()) {
			simulator.step();
		}
		System.out.println(simulator.isAccepting());
		System.out.println("Result: " + simulator.getResult());
	}

	MachineGraph graph;
	Tape tape = new Tape();
	boolean terminated = false;
	String state;

	public Simulator(MachineGraph graph) {
		this.graph = graph;
		state = graph.initialState;
	}

	public void step() {
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
				System.out.println("Line: " + t.lineNumber);
				state = t.target;
				return;
			}
		}
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
