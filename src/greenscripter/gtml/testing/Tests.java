package greenscripter.gtml.testing;

import java.io.File;
import java.io.IOException;

import greenscripter.gtml.simulator.MachineGraph;

public class Tests {

	public static void main(String[] args) throws IOException {
		MachineGraph graph = new MachineGraph(new File("generated.gtm"));
		TestSet tests = new TestSet();
		tests.cases.add(new TestCase("L101101110SS", "00011011"));
		tests.setLogging(true);
		tests.test(graph);
	}

}
