package greenscripter.gtml.testing;

import java.util.ArrayList;
import java.util.List;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import greenscripter.gtml.simulator.MachineGraph;

public class TestSet {

	List<TestCase> cases = new ArrayList<>();

	public boolean logging;

	public TestSet() {

	}

	public TestSet(List<TestCase> cases) {
		this.cases.addAll(cases);
	}

	public TestSet(File tests) throws IOException {

		try (BufferedReader input = new BufferedReader(new FileReader(tests))) {
			String line;
			while ((line = input.readLine()) != null) {
				TestCase c = new TestCase();

				if (line.startsWith("+")) {
					if (line.startsWith("+*")) {
						c.input = TestCase.splitString(line.substring(2));
						c.output = TestCase.splitString(input.readLine().substring(1));
					}
					if (line.startsWith("+$")) {
						c.input = MachineGraph.splitCommas(line.substring(2));
						c.output = MachineGraph.splitCommas(input.readLine().substring(1));
					}
				}
				if (line.startsWith("-")) {
					if (line.startsWith("-*")) {
						c.input = TestCase.splitString(line.substring(2));
						c.output.clear();
					}
					if (line.startsWith("-$")) {
						c.input = MachineGraph.splitCommas(line.substring(2));
						c.output.clear();
					}
				}

				cases.add(c);
			}
		}

	}

	public void write(File file) throws IOException {
		try (BufferedWriter output = new BufferedWriter(new FileWriter(file))) {
			for (TestCase c : cases) {
				boolean escape = false;
				for (String s : c.input) {
					if (s.length() != 1) {
						escape = true;
					}
					if (s.contains("\n")) {
						escape = true;
					}
				}
				for (String s : c.output) {
					if (s.length() != 1) {
						escape = true;
					}
					if (s.contains("\n")) {
						escape = true;
					}
				}
				if (c.accept) {
					output.write("+");
					if (escape) {
						output.write("$");
						output.write(MachineGraph.mergeCommas(c.input));
						output.write("\n>");
						output.write(MachineGraph.mergeCommas(c.output));
						output.write("\n");
					} else {
						output.write("*");
						output.write(TestCase.merge(c.input));
						output.write("\n>");
						output.write(TestCase.merge(c.output));
						output.write("\n");
					}
				} else {
					output.write("-");
					if (escape) {
						output.write("$");
						output.write(MachineGraph.mergeCommas(c.input));
						output.write("\n");
					} else {
						output.write("*");
						output.write(TestCase.merge(c.input));
						output.write("\n");
					}
				}
			}
		}
	}

	public boolean test(MachineGraph g) {
		for (TestCase c : cases) {
			if (!c.test(g)) {
				if (logging) System.out.println("Failed case " + TestCase.merge(c.input));
				return false;
			}
		}
		if (logging) System.out.println("Passed " + cases.size() + " tests.");
		return true;
	}

	public void setLogging(boolean logging) {
		this.logging = logging;
		for (TestCase c : cases) {
			c.logging = logging;
		}
	}
}
