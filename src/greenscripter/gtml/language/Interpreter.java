package greenscripter.gtml.language;

import static greenscripter.gtml.utils.Utils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.File;
import java.io.IOException;

import greenscripter.gtml.language.Parser.Assignment;
import greenscripter.gtml.language.Parser.Assignment.Destination;
import greenscripter.gtml.language.Parser.CharacterLiteral;
import greenscripter.gtml.language.Parser.Code;
import greenscripter.gtml.language.Parser.CodeBlock;
import greenscripter.gtml.language.Parser.FunctionCall;
import greenscripter.gtml.language.Parser.FunctionDefinition;
import greenscripter.gtml.language.Parser.IfStatement;
import greenscripter.gtml.language.Parser.Return;
import greenscripter.gtml.language.Parser.StringLiteral;
import greenscripter.gtml.language.Parser.VariableRead;
import greenscripter.gtml.language.Tokenizer.Token;

public class Interpreter {

	public static void main(String[] args) throws IOException {
		Interpreter interpreter = new Interpreter(new Parser(new File("testcode.gtml")));
		RunResult output = interpreter.run("bainput");
		System.out.println(output.accept);
		System.out.println(output.value);
	}

	public Parser parser;

	Map<MethodSignature, Function> functions = new HashMap<>();

	public Interpreter(Parser parser) {
		this.parser = parser;

		functions.put(new MethodSignature("atEquals", arraylist("any", "any")), //
				(caller, args) -> arraylist(new Value("bool", arraylist(args.get(0).get().equals(args.get(1).get()) ? "1" : "0"))));
		functions.put(new MethodSignature("atEqualsInline", arraylist("any", "any")), //
				(caller, args) -> arraylist(new Value("bool", arraylist(args.get(0).get().equals(args.get(1).get()) ? "1" : "0"))));

		functions.put(new MethodSignature("next", arraylist("any")), //
				(caller, args) -> {
					args.get(0).next();
					return null;
				});
		functions.put(new MethodSignature("reset", arraylist("any")), //
				(caller, args) -> {
					args.get(0).reset();
					return null;
				});
		functions.put(new MethodSignature("previous", arraylist("any")), //
				(caller, args) -> {
					args.get(0).previous();
					return null;
				});

		functions.put(new MethodSignature("start", arraylist("any")), //
				(caller, args) -> arraylist(new Value("bool", arraylist(args.get(0).atStart() ? "1" : "0"))));
		functions.put(new MethodSignature("end", arraylist("any")), //
				(caller, args) -> arraylist(new Value("bool", arraylist(args.get(0).atEnd() ? "1" : "0"))));

		functions.put(new MethodSignature("insert", arraylist("any", "any")), //
				(caller, args) -> {
					args.get(0).insert(args.get(1).get());
					return null;
				});
		functions.put(new MethodSignature("insertInline", arraylist("any", "any")), //
				(caller, args) -> {
					args.get(0).insert(args.get(1).get());
					return null;
				});
		functions.put(new MethodSignature("set", arraylist("any", "any")), //
				(caller, args) -> {
					args.get(0).set(args.get(1).get());
					return null;
				});
		functions.put(new MethodSignature("setInline", arraylist("any", "any")), //
				(caller, args) -> {
					args.get(0).set(args.get(1).get());
					return null;
				});

		functions.put(new MethodSignature("remove", arraylist("any")), //
				(caller, args) -> {
					args.get(0).remove();
					return null;
				});
		functions.put(new MethodSignature("debug_print", arraylist("any")), //
				(caller, args) -> {
					System.out.println(args.get(0));
					return null;
				});
		functions.put(new MethodSignature("accept", arraylist("any")), //
				(caller, args) -> {
					throw new EarlyExit(true, args.get(0));
				});
		functions.put(new MethodSignature("reject", arraylist("any")), //
				(caller, args) -> {
					throw new EarlyExit(false, args.get(0));
				});

		for (Code statement : parser.code.blocks) {
			switch (statement.getType()) {
				case COMPILER_INFO:
					break;
				case FUNCTION_DEFINITION:
					FunctionDefinition func = (FunctionDefinition) statement;
					Function old = functions.put(new MethodSignature(func.name.token, func.argumentTypes.stream().map(t -> t.token).toList()), new CodeFunction(func));
					if (old != null) {
						throw new TokenException("Duplicate function " + func.name, func.name);
					}
					break;
				default:
					throw new TokenException("Unexpected " + statement.getType(), statement.startToken);
			}
		}
	}

	public RunResult run(List<String> string) {
		Value input = new Value("string", string);

		Function main = functions.get(new MethodSignature("main", arraylist("string")));

		try {
			List<Value> output = main.evaluate(new Token("<maincaller>", "<maincaller>", 0, 0), arraylist(input));
			if (output == null) {
				throw new TokenException("Main function did not return", ((CodeFunction) main).func.startToken);
			}
			if (output.size() != 1) {
				throw new TokenException("Wrong number of return values from main", ((CodeFunction) main).func.startToken);
			}
			return new RunResult(true, merge(output.get(0).value), output.get(0).value);
		} catch (EarlyExit e) {
			return new RunResult(e.accept, merge(e.value.value), e.value.value);
		}
	}

	public RunResult run(String string) {
		return run(splitString(string));
	}

	public static record RunResult(boolean accept, String value, List<String> longValue) {

	}

	public EvalResult evaluate(Code block, Scope scope) {
		switch (block.getType()) {
			case ASSIGNMENT: {
				Assignment assign = (Assignment) block;
				List<Value> values = new ArrayList<>();
				for (Code c : assign.sources) {
					values.addAll(evaluate(c, scope).value());
				}
				int index = 0;
				for (Destination target : assign.names) {
					if (target.type == null) {
						Value v = scope.get(target.name.token);
						if (v == null) {
							throw new TokenException("Undefined variable " + target.name.token, assign.startToken);
						}
						if (v.type.equals(values.get(index).type)) {
							v.value.clear();
							v.value.addAll(values.get(index).value);
							v.index = values.get(index).index;
						} else {
							throw new TokenException("Type mismatch " + values.get(index).type + " not assignable to " + v.type, assign.startToken);
						}
					} else {
						Value v = scope.get(target.name.token);
						if (v != null) {
							throw new TokenException("Variable " + target.name.token + " already defined", assign.startToken);
						}
						scope.add(target.name.token, new Value(values.get(index)));
					}
					index++;
				}
				return new EvalResult(null, false);

			}
			case CHARACTER_LITERAL:
				return new EvalResult(arraylist(new Value("string", arraylist(((CharacterLiteral) block).contents.token))), false);
			case CODE_BLOCK: {
				Scope s = new Scope(scope);
				for (Code c : ((CodeBlock) block).blocks) {
					EvalResult result = evaluate(c, s);
					if (result.returned) {
						return result;
					}
				}
				return new EvalResult(null, false);
			}
			case COMPILER_INFO:
				return new EvalResult(null, false);
			case WHILE_STATEMENT: {
				IfStatement ifState = (IfStatement) block;
				List<Value> p = evaluate(ifState.arguments, scope).value;
				if (p.size() != 1) {
					throw new TokenException("Statement with no return value", ifState.arguments.startToken);
				}
				while (p.get(0).get().equals("1")) {
					EvalResult result = evaluate(ifState.contents, new Scope(scope));
					if (result.returned) {
						return result;
					}
					p = evaluate(ifState.arguments, scope).value;
					if (p.size() != 1) {
						throw new TokenException("Statement with no return value", ifState.arguments.startToken);
					}
				}
				return new EvalResult(null, false);
			}
			case IF_STATEMENT: {
				IfStatement ifState = (IfStatement) block;
				List<Value> p = evaluate(ifState.arguments, scope).value;
				if (p.size() != 1) {
					throw new TokenException("Statement with no return value", ifState.arguments.startToken);
				}
				if (p.get(0).get().equals("1")) {
					EvalResult result = evaluate(ifState.contents, new Scope(scope));
					if (result.returned) {
						return result;
					}
				} else {
					if (ifState.elseBlock != null) {
						EvalResult result = evaluate(ifState.elseBlock, new Scope(scope));
						if (result.returned) {
							return result;
						}
					}
				}
				return new EvalResult(null, false);
			}
			case FUNCTION_CALL: {
				FunctionCall call = (FunctionCall) block;
				List<Value> results = new ArrayList<>();
				for (Code c : call.arguments) {
					List<Value> p = evaluate(c, scope).value;
					if (p == null) throw new TokenException("Statement with no return value", c.startToken);
					results.addAll(p);
				}
				List<String> types = results.stream().map(v -> v.type).toList();
				Function func = functions.get(new MethodSignature(call.name.token, types));
				if (func == null) {
					func = functions.get(new MethodSignature(call.name.token, types.stream().map(s -> "any").toList()));
					if (func == null) {
						if (types.size() == 1) {
							Value out = new Value(results.get(0));
							out.type = call.name.token;
							return new EvalResult(arraylist(out), false);
						} else {
							throw new TokenException("Function " + call.name.token + "(" + types.stream().reduce((s1, s2) -> s1 + ", " + s2).orElse("") + ") not found", block.startToken);
						}
					}
				}
				return new EvalResult(func.evaluate(call.startToken, results), false);
			}
			case FUNCTION_DEFINITION:
				throw new TokenException("Invalid function definition, no nesting", block.startToken);
			case RETURN: {
				Return returnStatement = (Return) block;
				List<Value> results = new ArrayList<>();
				for (Code c : returnStatement.statements) {
					List<Value> p = evaluate(c, scope).value;
					if (p == null) throw new TokenException("Statement with no return value", c.startToken);
					results.addAll(p);
				}
				return new EvalResult(results, true);
			}
			case STRING_LITERAL:
				return new EvalResult(arraylist(new Value("string", splitString(((StringLiteral) block).contents.token))), false);
			case VARIABLE_READ: {
				VariableRead read = (VariableRead) block;
				Value value = scope.get(read.name.token);
				if (value == null) throw new TokenException("Undefinined variable", read.startToken);
				return new EvalResult(arraylist(value), false);
			}
			default:
				throw new TokenException("Unknown code type " + block.getType(), block.startToken);
		}
	}

	private record EvalResult(List<Value> value, boolean returned) {

	}

	private class EarlyExit extends RuntimeException {

		boolean accept;
		Value value;

		public EarlyExit(boolean accept, Value value) {
			this.accept = accept;
			this.value = value;
		}

	}

	private interface Function {

		public List<Value> evaluate(Token caller, List<Value> args);
	}

	private record MethodSignature(String name, List<String> args) {

	}

	private class CodeFunction implements Function {

		FunctionDefinition func;

		public CodeFunction(FunctionDefinition func) {
			this.func = func;
		}

		public List<Value> evaluate(Token caller, List<Value> args) {
			if (func.argumentTypes.size() != args.size()) {
				throw new TokenException("Wrong number of function arguments", caller);
			}
			Scope scope = new Scope();
			for (int i = 0; i < args.size(); i++) {
				if (!args.get(i).type.equals(func.argumentTypes.get(i).token)) {
					throw new TokenException("Incorrect argument type for arg " + i + " expected " + func.argumentTypes.get(i).token + " got " + args.get(i).type, caller);
				} else {
					scope.add(func.arguments.get(i).token, args.get(i));
				}
			}

			EvalResult eval = Interpreter.this.evaluate(func.body, scope);
			List<Value> returned = !eval.returned ? null : eval.value;

			if (returned == null) return null;

			if (func.returnTypes.size() != returned.size()) {
				throw new TokenException("Wrong number of function return values", func.startToken);
			}

			for (int i = 0; i < func.returnTypes.size(); i++) {
				if (!func.returnTypes.get(i).token.equals(returned.get(i).type)) {
					throw new TokenException("Wrong types of return values expected " + func.returnTypes.get(i).token + " got " + returned.get(i).type, func.startToken);
				}
			}

			return returned;
		}
	}

	private class Scope {

		Map<String, Value> contents = new HashMap<>();
		Scope parent;

		public Scope() {

		}

		public Scope(Scope parent) {
			this.parent = parent;
		}

		public boolean add(String s, Value v) {
			return contents.put(s, v) == null;
		}

		public Value get(String s) {
			Value local = contents.get(s);
			if (local == null && parent != null) {
				Value parentV = parent.get(s);
				if (parentV != null) return parentV;
			}
			return local;
		}

		public String toString() {
			return "Scope [contents=" + contents + ", parent=" + parent + "]";
		}

	}

	private class Value {

		List<String> value = new ArrayList<>();
		String type;
		int index = 0;

		public Value(Value v) {
			this.value.addAll(v.value);
			this.type = v.type;
			this.index = v.index;
		}

		public Value(String type, List<String> s) {
			this.type = type;
			value.addAll(s);
		}

		public boolean atEnd() {
			return index == value.size();
		}

		public boolean atStart() {
			return index == 0;
		}

		public String get() {
			return value.get(index);
		}

		public void set(String character) {
			value.set(index, character);
		}

		public void insert(String character) {
			value.add(index, character);
		}

		public void remove() {
			value.remove(index);
		}

		public void reset() {
			index = 0;
		}

		public void next() {
			if (atEnd()) {
				throw new IndexOutOfBoundsException(index + 1);
			}
			index++;
		}

		public void previous() {
			if (atStart()) {
				throw new IndexOutOfBoundsException(index - 1);
			}
			index--;
		}

		public String toString() {
			return "Value [value=" + value + ", type=" + type + ", index=" + index + "]";
		}

	}

}
