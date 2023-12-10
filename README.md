# GreenScripter's Turing Machine Language

A simple custom programming language for compiling into Turing Machines.

Currently there is a Java interpreter for the language that does not compile it, and most systems are in place for compilation though it is not yet complete.

There is currently no overall interface, parts of the program can be used by running and modifying the main methods of several classes used for different parts of the process.
Compiler.java compiles code to an intermediaty assembly type format, Assembler.java compiles that assembly into a simulatable Turing Machine and simulates it. Interpreter.java can interpret and run the high level language directly. Simulator.java runs the simulator by itself, and the Parser and Tokenizer can be run similarly. Test.java can also batch run the simulator on currently only .gtm compiled files. 

To run the current beta compiled code the compiler's output must be put in the defaultfunctions.gtma file which is an assembly file with all the code modules called by the compiler.

Finally there is support for exporting to JFLAP .jff files using the MachineToJFlap.java file.
