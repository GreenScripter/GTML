package greenscripter.gtml.testing;

import static greenscripter.gtml.utils.Utils.*;

import java.util.ArrayList;
import java.util.List;

import greenscripter.gtml.simulator.MachineGraph;
import greenscripter.gtml.simulator.Simulator;

public class TestCase {

	public long maxSteps = 1_000_000_000;
	public long maxTimeMS = 1000;

	public List<String> input = new ArrayList<>();
	public boolean accept = true;
	public List<String> output = new ArrayList<>();
	public boolean logging = false;

	public TestCase() {

	}

	public TestCase(String input, String output) {
		this(splitString(input), splitString(output));
	}

	public TestCase(List<String> input, List<String> output) {
		this.input.addAll(input);
		this.output.addAll(output);
	}

	public boolean test(MachineGraph g) {
		Simulator sim = new Simulator(g);
		//		sim.setLogging(logging);
		sim.loadTape(input);
		long start = System.currentTimeMillis();
		long step = 0;
		while (!sim.isTerminated() && System.currentTimeMillis() - start < maxTimeMS && step < maxSteps) {
			step++;
			sim.step();
		}
		if (!sim.isTerminated()) {
			if (logging) {
				if (System.currentTimeMillis() - start >= maxTimeMS) {
					System.out.println("Test time exceeded.");
				}
				if (step < maxSteps) {
					System.out.println("Number of steps exceeded.");
				}
			}
			return false;
		}
		if (sim.isAccepting() != accept) {
			System.out.println("Accept incorrect, " + sim.isAccepting() + "!=" + accept);
			return false;
		}
		if (sim.isAccepting()) {
			List<String> result = sim.getResultList();
			if (result.size() != output.size()) {
				System.out.println("Incorrect result:");
				System.out.println(result);
				System.out.println("Expected:");
				System.out.println(output);
				return false;
			}
			for (int i = 0; i < output.size(); i++) {
				if (!result.get(i).equals(output.get(i))) {
					System.out.println("Incorrect result:");
					System.out.println(result);
					System.out.println("Expected:");
					System.out.println(output);
					return false;
				}
			}
		}
		return true;
	}

}
