package greenscripter.gtml.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import java.io.File;
import java.io.IOException;

import greenscripter.gtml.simulator.MachineGraph;

public class Tests {

	public static void main(String[] args) throws IOException {
		Map<File, List<File>> allTests = new HashMap<>();
		Map<String, File> testNames = new HashMap<>();
		for (File f : new File("tests").listFiles()) {
			if (f.getName().endsWith(".gtmtests")) {
				allTests.put(f, new ArrayList<>());
				testNames.put(f.getName().substring(0, f.getName().length() - 9), f);
			}
		}
		System.out.println(testNames);
		for (File f : new File("tests").listFiles()) {
			if (f.getName().endsWith(".gtm")) {
				String machineName = f.getName().substring(0, f.getName().length() - 4);
				for (String name : testNames.keySet()) {
					if (machineName.endsWith(name)) {
						allTests.get(testNames.get(name)).add(f);
					}
				}
			}
		}
		System.out.println(allTests);

		for (Entry<File, List<File>> test : allTests.entrySet()) {
			TestSet tests = new TestSet(test.getKey());
			for (File f : test.getValue()) {
				MachineGraph graph = new MachineGraph(f);
				tests.setLogging(true);
				tests.test(graph);
			}
		}
	}

}
