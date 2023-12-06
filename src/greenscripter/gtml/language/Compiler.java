package greenscripter.gtml.language;

import static greenscripter.gtml.utils.Utils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import java.io.File;
import java.io.IOException;

import greenscripter.gtml.language.Parser.Assignment;
import greenscripter.gtml.language.Parser.Assignment.Destination;
import greenscripter.gtml.language.Parser.CharacterLiteral;
import greenscripter.gtml.language.Parser.Code;
import greenscripter.gtml.language.Parser.CodeBlock;
import greenscripter.gtml.language.Parser.CompilerInfo;
import greenscripter.gtml.language.Parser.FunctionCall;
import greenscripter.gtml.language.Parser.FunctionDefinition;
import greenscripter.gtml.language.Parser.IfStatement;
import greenscripter.gtml.language.Parser.Return;
import greenscripter.gtml.language.Parser.StringLiteral;
import greenscripter.gtml.language.Parser.VariableRead;
import greenscripter.gtml.language.Parser.WhileStatement;
import greenscripter.gtml.language.Tokenizer.Token;
import greenscripter.gtml.utils.Utils;

public class Compiler {

	public static void main(String[] args) throws IOException {
		Compiler compiler = new Compiler(new Parser(new File("testcode.gtml")));
		System.out.println(compiler.functions.stream().map(f -> f.def.name).toList());
		System.out.println(compiler.publicSymbols);
		System.out.println(compiler.otherSymbols);
		System.out.println(compiler.types);
		System.out.println(compiler.variableTypes);
	}

	public Parser parser;

	List<TrackedFunction> functions = new ArrayList<>();
	List<String> publicSymbols = new ArrayList<>();
	List<String> otherSymbols = new ArrayList<>();
	List<String> types = new ArrayList<>();

	Map<VariableRead, String> variableTypes = new HashMap<>();

	List<DefaultFunction> defaultFunctions = arraylist(//
			new DefaultFunction("atEquals", arraylist("any", "any"), arraylist("bool"), AtEqualsInstruction::new),//
			new DefaultFunction("atEqualsInline", arraylist("any", "any"), arraylist("bool"), AtEqualsInlineInstruction::new),//
			new DefaultFunction("next", arraylist("any"), arraylist(), NextInstruction::new),//
			new DefaultFunction("reset", arraylist("any"), arraylist(), ResetInstruction::new),//
			new DefaultFunction("previous", arraylist("any"), arraylist(), PreviousInstruction::new),//
			new DefaultFunction("start", arraylist("any"), arraylist("bool"), StartInstruction::new),//
			new DefaultFunction("end", arraylist("any"), arraylist("bool"), EndInstruction::new),//
			new DefaultFunction("insert", arraylist("any", "any"), arraylist(), InsertInstruction::new),//
			new DefaultFunction("insertInline", arraylist("any", "any"), arraylist(), InsertInlineInstruction::new),//
			new DefaultFunction("set", arraylist("any", "any"), arraylist(), SetInstruction::new),//
			new DefaultFunction("setInline", arraylist("any", "any"), arraylist(), SetInlineInstruction::new),//
			new DefaultFunction("remove", arraylist("any"), arraylist(), RemoveInstruction::new),//
			new DefaultFunction("debug_print", arraylist("any"), arraylist(), DebugPrintInstruction::new),//
			new DefaultFunction("accept", arraylist("any"), arraylist(), AcceptInstruction::new),//
			new DefaultFunction("reject", arraylist("any"), arraylist(), RejectInstruction::new));

	int tempId = 0;

	public Compiler(Parser parser) {
		this.parser = parser;

		for (Code statement : parser.code.blocks) {
			switch (statement.getType()) {
				case COMPILER_INFO:
					CompilerInfo info = (CompilerInfo) statement;
					if (info.name.is("symbols")) {
						for (Token t : info.contents) {
							publicSymbols.add(t.token);
						}
					}
					break;
				case FUNCTION_DEFINITION:
					FunctionDefinition func = (FunctionDefinition) statement;
					functions.add(new TrackedFunction(func));
					break;
				default:
					throw new TokenException("Unexpected " + statement.getType(), statement.startToken);
			}
		}
		collectInfo();
		for (TrackedFunction func : functions) {
			System.out.print(func.def.name + ": ");
			List<Code> simplified = simplify(func.def.body);
			simplified.forEach(c -> System.out.println(c.write()));
			System.out.println("Instructions:");
			wrapFunction(func, simplified.stream().map(this::flatten).reduce((l1, l2) -> {
				l1.addAll(l2);
				return l1;
			}).orElse(arraylist())).stream().map(s -> Parser.CodeBlock.indentedToString(s.toString())).forEach(System.out::println);
		}
	}

	private String generateName() {
		return "_temp" + tempId++;
	}

	private void collectInfo() {
		for (TrackedFunction func : functions) {
			Map<String, String> args = new HashMap<>();
			for (int i = 0; i < func.def.arguments.size(); i++) {
				args.put(func.def.arguments.get(i).token, func.def.argumentTypes.get(i).token);
			}
			collectInfo(arraylist(func.def.body), args);
			types.addAll(func.def.argumentTypes.stream().map(t -> t.token).toList());
			types.addAll(func.def.returnTypes.stream().map(t -> t.token).toList());
		}

		otherSymbols.addAll(publicSymbols);
		otherSymbols.add("0");
		otherSymbols.add("1");
		otherSymbols.add("MARKER");
		otherSymbols.add("STACK");
		otherSymbols.add("CURSOR");
		otherSymbols.add("PRINTPOINT");
		otherSymbols.add("");
		publicSymbols = new ArrayList<>(publicSymbols.stream().distinct().toList());
		types = new ArrayList<>(types.stream().distinct().toList());
		otherSymbols = new ArrayList<>(otherSymbols.stream().distinct().toList());
	}

	private void collectInfo(List<Code> cs, Map<String, String> variableTypes) {
		for (Code c : cs) {
			if (c == null) continue;
			switch (c.getType()) {
				case ASSIGNMENT:
					collectInfo(((Assignment) c).sources, new HashMap<>(variableTypes));
					types.addAll(((Assignment) c).names.stream().filter(d -> d.type != null).map(d -> d.type.token).toList());
					((Assignment) c).names.stream().filter(d -> d.type != null).forEach(d -> variableTypes.put(d.name.token, d.type.token));
					break;
				case CHARACTER_LITERAL:
					otherSymbols.add(((CharacterLiteral) c).contents.token);
					break;
				case CODE_BLOCK:
					collectInfo(((CodeBlock) c).blocks, new HashMap<>(variableTypes));
					break;
				case COMPILER_INFO:
					break;
				case FUNCTION_CALL:
					collectInfo(((FunctionCall) c).arguments, new HashMap<>(variableTypes));
					break;
				case FUNCTION_DEFINITION:
					break;
				case IF_STATEMENT:
					collectInfo(arraylist(((IfStatement) c).arguments), new HashMap<>(variableTypes));
					collectInfo(arraylist(((IfStatement) c).contents), new HashMap<>(variableTypes));
					collectInfo(arraylist(((IfStatement) c).elseBlock), new HashMap<>(variableTypes));
					break;
				case RETURN:
					collectInfo(((Return) c).statements, new HashMap<>(variableTypes));
					break;
				case STRING_LITERAL:
					otherSymbols.addAll(splitString(((StringLiteral) c).contents.token));
					break;
				case VARIABLE_READ:
					this.variableTypes.put((VariableRead) c, variableTypes.get(((VariableRead) c).name.token));
					break;
				case WHILE_STATEMENT:
					collectInfo(arraylist(((WhileStatement) c).arguments), new HashMap<>(variableTypes));
					collectInfo(arraylist(((WhileStatement) c).contents), new HashMap<>(variableTypes));
					break;
				default:
					throw new TokenException("Unknown code type " + c.getType(), c.startToken);
			}
		}
	}

	private GeneralFunction getFunction(String name, List<String> types) {
		funcLoop: for (DefaultFunction f : defaultFunctions) {
			if (!f.name.equals(name)) {
				continue;
			}
			Iterator<String> typeIt = types.iterator();
			for (String t : f.args) {
				if (!typeIt.hasNext()) {
					continue funcLoop;
				}
				String type = typeIt.next();
				if (!t.equals("any")) {
					if (!t.equals(type)) {
						continue funcLoop;
					}
				}
			}
			return f;
		}

		funcLoop: for (TrackedFunction f : functions) {
			if (!f.def.name.is(name)) {
				continue;
			}
			Iterator<String> typeIt = types.iterator();
			for (Token t : f.def.argumentTypes) {
				if (!typeIt.hasNext()) {
					continue funcLoop;
				}
				String type = typeIt.next();
				if (!t.is("any")) {
					if (!t.is(type)) {
						continue funcLoop;
					}
				}
			}
			return f;
		}

		if (types.size() == 1) for (String type : this.types) {
			if (name.equals(type)) {
				return new TypeCast(name);
			}
		}
		return null;
	}

	private List<String> getTypesFrom(Code code) {
		switch (code.getType()) {
			case ASSIGNMENT:
				return arraylist();
			case CHARACTER_LITERAL:
				return arraylist("string");
			case CODE_BLOCK:
				return arraylist();
			case COMPILER_INFO:
				return arraylist();
			case FUNCTION_CALL:
				FunctionCall call = (FunctionCall) code;
				List<String> types = new ArrayList<>();
				for (Code c : call.arguments) {
					List<String> part = getTypesFrom(c);
					if (part.isEmpty()) throw new TokenException(c.getType() + " has no return value", c.startToken);
					types.addAll(part);
				}
				GeneralFunction func = getFunction(call.name.token, types);
				if (func == null) {
					throw new TokenException("Undefined function name", code.startToken);
				}
				return func.getReturnTypes();
			case FUNCTION_DEFINITION:
				throw new TokenException("Unable to get return types of function definition", code.startToken);
			case IF_STATEMENT:
				return arraylist();
			case RETURN:
				return arraylist();
			case STRING_LITERAL:
				return arraylist("string");
			case VARIABLE_READ:
				if (variableTypes.containsKey(code)) {
					return arraylist(variableTypes.get(code));
				} else {
					throw new TokenException("Undefined variable", code.startToken);
				}
			case WHILE_STATEMENT:
				return arraylist("string");
			default:
				break;
		}
		return arraylist();
	}

	private List<Code> simplifyToRead(Code code) {
		List<Code> internals = simplify(code);
		if (internals.get(internals.size() - 1) instanceof VariableRead) {
			//Already read
		} else {
			//create new read
			Code last = internals.remove(internals.size() - 1);
			Assignment tempVars = new Assignment();
			tempVars.startToken = last.startToken;
			tempVars.sources = arraylist(last);
			tempVars.names = new ArrayList<>();
			String name = generateName();
			List<String> returnTypes = getTypesFrom(last);
			if (returnTypes.size() != 1) {
				throw new TokenException("Unable to read a single result", last.startToken);
			}
			tempVars.names.add(new Destination(last.startToken.derivative(returnTypes.get(0)), last.startToken.derivative(name)));

			VariableRead read = new VariableRead();
			read.name = last.startToken.derivative(name);

			variableTypes.put(read, returnTypes.get(0));

			// Assignments might still be complex, so simplify them.
			internals.addAll(simplify(tempVars));
			internals.add(read);
		}
		return internals;
	}

	private List<Code> simplify(Code code) {
		List<Code> result = new ArrayList<>();
		switch (code.getType()) {
			case ASSIGNMENT: {
				Assignment assign = (Assignment) code;

				if (assign.names.size() == 1) {
					//Single assignment
					Assignment simplified = new Assignment();
					simplified.startToken = assign.startToken;
					List<Code> internals = simplify(assign.sources.get(0));

					if (assign.names.get(0).type == null && (internals.get(internals.size() - 1) instanceof FunctionCall)) {
						internals = simplifyToRead(assign.sources.get(0));
					}
					result.addAll(internals.subList(0, internals.size() - 1));
					simplified.sources.add(internals.get(internals.size() - 1));
					simplified.names = assign.names;

					result.add(simplified);

				} else if (assign.sources.size() == 1) {
					//Single source, multiple assignment
					Assignment simplified = new Assignment();
					simplified.startToken = assign.startToken;
					simplified.names = new ArrayList<>();

					List<Code> internals = simplify(assign.sources.get(0));
					result.addAll(internals.subList(0, internals.size() - 1));
					simplified.sources.add(internals.get(internals.size() - 1));

					List<String> types = getTypesFrom(internals.get(internals.size() - 1));
					if (types.size() != assign.names.size()) {
						throw new TokenException("Incorrect number of destinations", assign.startToken);
					}

					List<Assignment> renames = new ArrayList<>();
					int index = 0;
					for (Destination d : assign.names) {
						if (d.type != null) {
							simplified.names.add(d);
						} else {
							//Create temp assignments for variables that aren't new.
							String tempName = generateName();
							simplified.names.add(new Destination(d.name.derivative(types.get(index)), d.name.derivative(tempName)));
							Assignment copy = new Assignment();
							copy.names.add(d);
							VariableRead tempRead = new VariableRead();
							tempRead.name = d.name.derivative(tempName);
							variableTypes.put(tempRead, types.get(index));
							copy.sources.add(tempRead);
							copy.startToken = assign.startToken;
							renames.add(copy);
						}
						index++;
					}

					result.add(simplified);
					result.addAll(renames);
				} else {
					//multiple source, multiple assignment
					List<List<String>> sourceTypes = new ArrayList<>();
					for (Code source : assign.sources) {
						sourceTypes.add(getTypesFrom(source));
					}

					int dest = 0;
					for (int i = 0; i < assign.sources.size(); i++) {
						Assignment simplified = new Assignment();
						simplified.startToken = assign.startToken;
						simplified.names = new ArrayList<>();
						simplified.sources = new ArrayList<>();
						simplified.sources.add(assign.sources.get(i));

						for (@SuppressWarnings("unused")
						String s : sourceTypes.get(i)) {
							simplified.names.add(assign.names.get(dest));
							dest++;
						}
						result.addAll(simplify(simplified));
					}
					if (dest != assign.names.size()) {
						throw new TokenException("Incorrect number of destinations " + dest + " != " + assign.names.size(), assign.startToken);
					}

				}
				break;
			}
			case CHARACTER_LITERAL:
				result.add(code);
				break;
			case CODE_BLOCK: {
				CodeBlock simplified = new CodeBlock();
				simplified.startToken = code.startToken;
				for (Code c : ((CodeBlock) code).blocks) {
					simplified.blocks.addAll(simplify(c));
				}
				result.add(simplified);
				break;
			}
			case COMPILER_INFO:
				break;
			case FUNCTION_CALL: {
				//Make all call arugments only variable reads.
				FunctionCall call = (FunctionCall) code;
				FunctionCall simpleCall = new FunctionCall();
				simpleCall.name = call.name;
				simpleCall.startToken = call.startToken;
				int index = 0;
				for (Code c : call.arguments) {
					if (call.arguments.size() == 2 && index == 1 && (call.name.is("atEqualsInline") || call.name.is("insertInline") || call.name.is("setInline"))) {
						//special case for compiler inlined pseudo functions.
						simpleCall.arguments.add(c);
					} else {
						List<String> types = getTypesFrom(c);
						if (types.isEmpty()) throw new TokenException(c.getType() + " has no return value", c.startToken);
						if (c instanceof VariableRead) {
							//Already read
							simpleCall.arguments.add(c);
						} else {
							//create new read
							Assignment tempVars = new Assignment();
							tempVars.startToken = c.startToken;
							tempVars.sources = arraylist(c);
							tempVars.names = new ArrayList<>();
							for (String type : types) {
								String name = generateName();
								tempVars.names.add(new Destination(c.startToken.derivative(type), c.startToken.derivative(name)));

								VariableRead read = new VariableRead();
								read.name = c.startToken.derivative(name);

								variableTypes.put(read, type);//register type at this place

								simpleCall.arguments.add(read);
							}
							// Assignments might still be complex, so simplify them.
							result.addAll(simplify(tempVars));
						}
					}
					index++;
				}
				result.add(simpleCall);
				break;
			}
			case FUNCTION_DEFINITION:
				throw new TokenException("Unable to simplify function definition", code.startToken);
			case IF_STATEMENT: {
				IfStatement ifState = (IfStatement) code;
				IfStatement simplified = new IfStatement();

				List<Code> internals = simplifyToRead(ifState.arguments);
				result.addAll(internals.subList(0, internals.size() - 1));
				simplified.arguments = internals.get(internals.size() - 1);

				List<Code> sContents = simplify(ifState.contents);

				if (sContents.size() != 1) {
					CodeBlock contents = new CodeBlock();
					contents.blocks.addAll(sContents);
					simplified.contents = contents;
				} else {
					simplified.contents = sContents.get(0);
				}

				if (ifState.elseBlock != null) {
					List<Code> sElse = simplify(ifState.elseBlock);
					if (sElse.size() != 1) {
						CodeBlock contents = new CodeBlock();
						contents.blocks.addAll(sElse);
						simplified.elseBlock = contents;
					} else {
						simplified.elseBlock = sElse.get(0);
					}
				}

				result.add(simplified);
				break;
			}
			case RETURN: {
				Return ret = (Return) code;
				Return simplified = new Return();
				simplified.startToken = ret.startToken;

				for (Code c : ret.statements) {
					List<Code> internals = simplifyToRead(c);
					result.addAll(internals.subList(0, internals.size() - 1));
					simplified.statements.add(internals.get(internals.size() - 1));
				}
				result.add(simplified);
				break;
			}
			case STRING_LITERAL:
				result.add(code);
				break;
			case VARIABLE_READ:
				result.add(code);
				break;
			case WHILE_STATEMENT: {
				WhileStatement whileState = (WhileStatement) code;
				WhileStatement simplified = new WhileStatement();

				List<Code> internals = simplifyToRead(whileState.arguments);

				result.addAll(internals.subList(0, internals.size() - 1));
				simplified.arguments = internals.get(internals.size() - 1);

				List<Code> sContents = simplify(whileState.contents);
				CodeBlock contents = new CodeBlock();
				contents.blocks.addAll(sContents);
				contents.blocks.addAll(internals.subList(0, internals.size() - 1).stream().map(c -> {
					//In while update block generated assignments need to be overwrites not definitions
					if (c instanceof Assignment a) {
						Assignment alt = new Assignment();
						alt.sources = a.sources;
						alt.startToken = a.startToken;
						alt.names = a.names.stream().map(d -> new Destination(d.name)).toList();
						return alt;
					}
					return c;
				}).toList());

				simplified.contents = contents;

				result.add(simplified);
				break;
			}
			default:
				break;

		}
		return result;
	}

	//This method only handles "simplified" Code objects
	private List<Instruction> flatten(Code code) {
		List<Instruction> result = new ArrayList<>();
		switch (code.getType()) {
			case ASSIGNMENT: {
				Assignment assign = (Assignment) code;
				//after simplify there is only ever one assignment source
				Code source = assign.sources.get(0);
				if (source instanceof FunctionCall call) {
					//Handle function calls, which can have multiple results but must be new declarations.

					List<String> types = call.arguments.stream().map(a -> getTypesFrom(a).get(0)/*arguments should only be variable reads*/).toList();
					GeneralFunction toCall = getFunction(call.name.token, types);
					if (toCall instanceof TrackedFunction func) {
						for (Code c : call.arguments) {
							if (c instanceof VariableRead read) {
								PushCopyInstruction inst = new PushCopyInstruction();
								inst.source = read.startToken;
								inst.stackName = read.name.token;
								result.add(inst);

							} else {
								throw new TokenException("Argument not a read", c.startToken);
							}
						}
						CallInstruction callinst = new CallInstruction();
						callinst.source = source.startToken;
						callinst.target = func;
						func.callers.add(callinst);//register caller for return jump
						result.add(callinst);

						CallCleanupInstruction inst = new CallCleanupInstruction();
						inst.source = source.startToken;
						inst.from = func;
						inst.call = callinst;
						callinst.cleanup = inst;
						result.add(inst);

						//name all returned stack entries based on their assignments.
						int index = 0;
						int offset = types.size() - 1;
						for (String s : types) {
							NameStackEntryInstruction inst2 = new NameStackEntryInstruction();
							Destination target = assign.names.get(index);

							inst2.source = target.name;
							inst2.offset = offset;
							inst2.name = target.name.token;
							inst2.type = s;
							result.add(inst2);
							offset--;
							index++;
						}
					} else if (toCall instanceof TypeCast cast) {
						//function call is a cast
						{
							//push copy of variable
							PushCopyInstruction inst = new PushCopyInstruction();
							inst.source = call.startToken;
							inst.stackName = ((VariableRead) call.arguments.get(0)).name.token;
							result.add(inst);
						}
						{
							//rename copy
							NameStackEntryInstruction inst = new NameStackEntryInstruction();
							inst.source = assign.startToken;
							inst.name = assign.names.get(0).name.token;
							inst.offset = 0;
							inst.type = assign.names.get(0).type.token;
							result.add(inst);
						}
					} else if (toCall instanceof DefaultFunction def) {
						try {
							DefaultFunctionInstruction inst = def.type.apply(call);
							inst.source = call.startToken;
							result.add(inst);

							//name returned stack values
							int index = 0;
							int offset = types.size() - 1;
							for (String s : def.returnTypes) {
								NameStackEntryInstruction inst2 = new NameStackEntryInstruction();
								Destination target = assign.names.get(index);

								inst2.source = target.name;
								inst2.offset = offset;
								inst2.name = target.name.token;
								inst2.type = s;
								result.add(inst2);
								offset--;
								index++;
							}
						} catch (Exception e) {
							if (e instanceof TokenException) {
								throw new RuntimeException(e);
							} else {
								e.printStackTrace();
								throw new TokenException("Error: " + e, call.startToken);
							}
						}
					} else {
						throw new TokenException("Unknown function type " + (toCall != null ? toCall.getClass() : "null"), source.startToken);
					}
				} else {
					//Not a function call, and has exactly one result.
					Destination target = assign.names.get(0);
					if (target.type != null) {
						//This is a new variable, make a stack entry for it.
						PushInstruction inst = new PushInstruction();
						inst.source = target.type;
						result.add(inst);
						NameStackEntryInstruction inst2 = new NameStackEntryInstruction();
						inst2.source = target.name;
						inst2.offset = 0;
						inst2.name = target.name.token;
						inst2.type = target.type.token;
						result.add(inst2);
					}
					if (source instanceof StringLiteral s) {
						//direct string
						StringInstruction inst = new StringInstruction();
						inst.source = s.startToken;
						inst.symbols = Utils.splitString(s.contents.token);
						inst.stackName = target.name.token;
						result.add(inst);
					} else if (source instanceof CharacterLiteral s) {
						//direct character
						CharInstruction inst = new CharInstruction();
						inst.source = s.startToken;
						inst.symbol = s.contents.token;
						inst.stackName = target.name.token;
						result.add(inst);
					} else if (source instanceof VariableRead s) {
						//read/copy
						CopyInstruction inst = new CopyInstruction();
						inst.source = s.startToken;
						inst.stackName = s.name.token;
						inst.target = target.name.token;
						result.add(inst);
					} else {
						throw new TokenException("Unknown flatten assignment", source.startToken);
					}
				}
				break;
			}
			case CHARACTER_LITERAL:
				throw new TokenException("Orphaned character", code.startToken);
			case CODE_BLOCK: {
				CodeBlock block = (CodeBlock) code;
				ScopeBlockInstruction inst = new ScopeBlockInstruction();
				inst.source = block.startToken;
				for (Code c : block.blocks) {
					inst.children.addAll(flatten(c));
				}
				result.add(inst);
			}
			case COMPILER_INFO:
				break;
			case FUNCTION_CALL: {
				FunctionCall call = (FunctionCall) code;
				List<String> types = call.arguments.stream().map(a -> getTypesFrom(a).get(0)/*arguments should only be variable reads*/).toList();
				GeneralFunction toCall = getFunction(call.name.token, types);
				if (toCall instanceof TrackedFunction func) {
					for (Code c : call.arguments) {
						if (c instanceof VariableRead read) {
							PushCopyInstruction inst = new PushCopyInstruction();
							inst.source = read.startToken;
							inst.stackName = read.name.token;
							result.add(inst);

						} else {
							throw new TokenException("Argument not a read", c.startToken);
						}
					}
					CallInstruction callinst = new CallInstruction();
					callinst.source = call.startToken;
					callinst.target = func;
					func.callers.add(callinst);//register caller for return jump
					result.add(callinst);

					CallCleanupInstruction inst = new CallCleanupInstruction();
					inst.source = call.startToken;
					inst.from = func;
					inst.call = callinst;
					callinst.cleanup = inst;
					result.add(inst);

					//pop off all return values
					for (@SuppressWarnings("unused")
					Token s : func.def.returnTypes) {
						PopInstruction inst2 = new PopInstruction();
						inst2.source = call.startToken;
						result.add(inst2);
					}
				} else if (toCall instanceof TypeCast cast) {
					//function call is a cast
					throw new TokenException("Type cast return value is not used", call.startToken);
				} else if (toCall instanceof DefaultFunction def) {
					try {
						DefaultFunctionInstruction inst = def.type.apply(call);
						inst.source = call.startToken;
						result.add(inst);
						//pop off return values
						for (@SuppressWarnings("unused")
						String s : def.returnTypes) {
							PopInstruction inst2 = new PopInstruction();
							inst2.source = call.startToken;
							result.add(inst2);
						}
					} catch (Exception e) {
						if (e instanceof TokenException) {
							throw new RuntimeException(e);
						} else {
							e.printStackTrace();
							throw new TokenException("Error: " + e, call.startToken);
						}
					}
				} else {
					throw new TokenException("Unknown function type " + (toCall != null ? toCall.getClass() : "null"), call.startToken);
				}
				break;
			}
			case FUNCTION_DEFINITION:
				throw new TokenException("Unable to flatten function definition", code.startToken);
			case IF_STATEMENT: {
				IfStatement ifState = (IfStatement) code;
				//simplification should yield always a read here
				VariableRead read = (VariableRead) ifState.arguments;
				IfInstruction inst = new IfInstruction();
				inst.source = ifState.startToken;
				inst.stackName = read.name.token;
				{
					List<Instruction> flatContents = flatten(ifState.contents);
					if (flatContents.size() != 1) {
						ScopeBlockInstruction trueCase = new ScopeBlockInstruction();
						trueCase.children.addAll(flatContents);
						inst.caseTrue = trueCase;
					} else {
						inst.caseTrue = flatContents.get(0);
					}
				}
				if (ifState.elseBlock != null) {
					List<Instruction> flatElseBlock = flatten(ifState.elseBlock);
					if (flatElseBlock.size() != 1) {
						ScopeBlockInstruction falseCase = new ScopeBlockInstruction();
						falseCase.children.addAll(flatten(ifState.elseBlock));
						inst.caseFalse = falseCase;
					} else {
						inst.caseFalse = flatElseBlock.get(0);
					}
				}
				result.add(inst);
				break;
			}
			case RETURN: {
				Return ret = (Return) code;
				ReturnInstruction inst = new ReturnInstruction();
				inst.source = ret.startToken;
				for (Code c : ret.statements) {
					PushCopyInstruction copy = new PushCopyInstruction();
					copy.source = c.startToken;
					//Should always be returning a variable read from simplify
					copy.stackName = ((VariableRead) c).name.token;
					result.add(copy);
					inst.returnSources.add(c.startToken);
					inst.returnTypes.add(getTypesFrom(c).get(0));
				}
				result.add(inst);

				break;
			}
			case STRING_LITERAL:
				throw new TokenException("Orphaned string", code.startToken);
			case VARIABLE_READ:
				throw new TokenException("Orphaned variable read", code.startToken);
			case WHILE_STATEMENT: {
				WhileStatement whileState = (WhileStatement) code;
				//simplification should yield always a read here
				VariableRead read = (VariableRead) whileState.arguments;
				WhileInstruction inst = new WhileInstruction();
				inst.source = whileState.startToken;
				inst.stackName = read.name.token;
				{
					List<Instruction> flatContents = flatten(whileState.contents);
					if (flatContents.size() != 1) {
						ScopeBlockInstruction trueCase = new ScopeBlockInstruction();
						trueCase.children.addAll(flatContents);
						inst.caseTrue = trueCase;
					} else {
						inst.caseTrue = flatContents.get(0);
					}
				}
				result.add(inst);
				break;
			}
			default:
				throw new TokenException("Cannot flatten unknown type " + code.getType(), code.startToken);
		}
		return result;
	}

	private List<Instruction> wrapFunction(TrackedFunction func, List<Instruction> instructions) {
		List<Instruction> results = new ArrayList<>();

		int offset = func.def.argumentTypes.size() - 1;
		for (int i = 0; i < func.def.argumentTypes.size(); i++) {
			NameStackEntryInstruction inst = new NameStackEntryInstruction();
			inst.source = func.def.arguments.get(i);
			inst.offset = offset;
			inst.name = func.def.arguments.get(i).token;
			inst.type = func.def.argumentTypes.get(i).token;
			results.add(inst);
			offset--;
		}
		for (int i = 0; i < instructions.size(); i++) {
			alterReturnsFor(instructions.get(i), func);
			results.add(instructions.get(i));
		}
		insertDeletes(results, new HashSet<>(), new HashSet<>());
		return results;
	}

	private void alterReturnsFor(Instruction inst, TrackedFunction func) {
		if (inst instanceof ReturnInstruction r) {
			r.from = func;
			if (r.returnTypes.size() != func.getReturnTypes().size()) {
				throw new TokenException("Incorrect number of return types in function " + func.def.name, r.source);
			}
			for (int i = 0; i < r.returnTypes.size(); i++) {
				if (!r.returnTypes.get(i).equals(func.def.returnTypes.get(i).token)) {
					throw new TokenException("Incorrect return types in function " + func.def.name, r.returnSources.get(i));
				}
			}
		}
		if (inst instanceof ScopeBlockInstruction b) {
			for (Instruction i : b.children) {
				alterReturnsFor(i, func);
			}
		}
		if (inst instanceof WhileInstruction w) {
			alterReturnsFor(w.caseTrue, func);
		}
		if (inst instanceof IfInstruction f) {
			alterReturnsFor(f.caseTrue, func);
			if (f.caseFalse != null) alterReturnsFor(f.caseFalse, func);

		}
	}

	private void insertDeletes(List<Instruction> insts, Set<String> used, Set<String> protect) {
		for (int j = 0; j < insts.size(); j++) {
			Instruction inst = insts.get(j);

			used.addAll(inst.createsStackEntries());

			Set<String> childProtect = new HashSet<>(protect);
			childProtect.addAll(used);
			System.out.println(inst.getClass() + " " + childProtect);

			if (inst instanceof ScopeBlockInstruction b) {
				insertDeletes(b.children, new HashSet<>(), childProtect);
			}
			if (inst instanceof WhileInstruction w) {
				if (w.caseTrue instanceof ScopeBlockInstruction b) {
					insertDeletes(b.children, new HashSet<>(), childProtect);
				}
			}
			if (inst instanceof IfInstruction f) {
				if (f.caseTrue instanceof ScopeBlockInstruction b) {
					insertDeletes(b.children, new HashSet<>(), childProtect);
				}
				if (f.caseFalse != null) {
					if (f.caseFalse instanceof ScopeBlockInstruction b) {
						insertDeletes(b.children, new HashSet<>(), childProtect);
					}
				}
			}

			List<String> remove = new ArrayList<>();
			List<String> usedLater = getUsed(insts.subList(j + 1, insts.size()));
			usedLater.addAll(protect);
			for (String s : used) {
				if (!usedLater.contains(s)) {
					remove.add(s);
					DeleteInstruction del = new DeleteInstruction();
					del.source = inst.source;
					del.stackName = s;
					insts.add(j + 1, del);
					j++;
				}
			}
			used.removeAll(remove);
		}
	}

	private List<String> getUsed(List<Instruction> inst) {
		List<String> result = new ArrayList<>();
		for (Instruction i : inst) {
			result.addAll(i.usesStackEntries());
		}
		return result;
	}

	private interface GeneralFunction {

		List<String> getReturnTypes();

	}

	record DefaultFunction(String name, List<String> args, List<String> returnTypes, Function<FunctionCall, ? extends DefaultFunctionInstruction> type) implements GeneralFunction {

		public List<String> getReturnTypes() {
			return returnTypes;
		}

	}

	record TypeCast(String type) implements GeneralFunction {

		public List<String> getReturnTypes() {
			return arraylist(type);
		}

	}

	private class TrackedFunction implements GeneralFunction {

		FunctionDefinition def;
		List<CallInstruction> callers = new ArrayList<>();

		public TrackedFunction(FunctionDefinition def) {
			this.def = def;
		}

		public List<String> getReturnTypes() {
			return def.returnTypes.stream().map(t -> t.token).toList();
		}

		public String toString() {
			return "TrackedFunction [name=" + def.name + ", args=" + def.argumentTypes + ", returns=" + def.returnTypes + "]";
		}
	}

	private abstract class Instruction {

		Token source;

		public abstract List<String> usesStackEntries();

		public List<String> createsStackEntries() {
			return usesStackEntries();
		}

	}

	private class ScopeBlockInstruction extends Instruction {

		//subset of instructions with a subscope
		List<Instruction> children = new ArrayList<>();

		public String toString() {
			return "ScopeBlockInstruction [\n" + children.stream().map(Object::toString).reduce((s1, s2) -> s1 + "\n" + s2).orElse("") + "\n]";
		}

		public List<String> usesStackEntries() {
			List<String> results = new ArrayList<>();
			for (Instruction i : children) {
				results.addAll(i.usesStackEntries());
			}
			return results;
		}

		public List<String> createsStackEntries() {
			List<String> results = new ArrayList<>();
			return results;
		}

	}

	private class NameStackEntryInstruction extends Instruction {

		//name stack entries for future instructions
		int offset;
		String name;
		String type;

		public String toString() {
			return "NameStackEntryInstruction [offset=" + offset + ", name=" + name + ", type=" + type + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(name);
		}

	}

	private class CallInstruction extends Instruction {

		public CallCleanupInstruction cleanup;
		//prep for return and jump into a function
		TrackedFunction target;

		public String toString() {
			return "CallInstruction [target=" + target + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist();
		}

	}

	private class CallCleanupInstruction extends Instruction {

		//cleanup after jumping back from a function
		TrackedFunction from;
		CallInstruction call;

		public String toString() {
			return "CallCleanupInstruction [from=" + from + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist();
		}

	}

	private class CopyInstruction extends Instruction {

		//Copy between two stack positions.
		String stackName;
		String target;

		public String toString() {
			return "CopyInstruction [stackName=" + stackName + ", target=" + target + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName, target);
		}

	}

	private class PushCopyInstruction extends Instruction {

		//Copy between two stack positions.
		String stackName;

		public String toString() {
			return "PushCopyInstruction [stackName=" + stackName + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class PushInstruction extends Instruction {

		public String toString() {
			return "PushInstruction []";
		}
		//Create a stack entry

		public List<String> usesStackEntries() {
			return arraylist();
		}

	}

	private class DeleteInstruction extends Instruction {

		//Remove a stack entry
		String stackName;

		public String toString() {
			return "DeleteInstruction [stackName=" + stackName + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class PopInstruction extends Instruction {

		public String toString() {
			return "PopInstruction []";
		}
		//Remove the last stack entry

		public List<String> usesStackEntries() {
			return arraylist();
		}

	}

	private class ReturnInstruction extends Instruction {

		//return from a function
		TrackedFunction from;
		List<String> returnTypes = new ArrayList<>();
		List<Token> returnSources = new ArrayList<>();

		public String toString() {
			return "ReturnInstruction [from=" + from + ", types=" + returnTypes + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist();
		}

	}

	private class IfInstruction extends Instruction {

		//branch based on a stack value
		String stackName;
		Instruction caseTrue;
		Instruction caseFalse;

		public String toString() {
			return "IfInstruction [stackName=" + stackName + ", caseTrue=" + caseTrue + ", caseFalse=" + caseFalse + "]";
		}

		public List<String> usesStackEntries() {
			List<String> result = arraylist(stackName);
			result.addAll(caseTrue.usesStackEntries());
			if (caseFalse != null) result.addAll(caseFalse.usesStackEntries());
			return result;
		}

		public List<String> createsStackEntries() {
			List<String> result = arraylist(stackName);
			return result;
		}

	}

	private class WhileInstruction extends Instruction {

		//loop based on a stack value
		String stackName;
		Instruction caseTrue;

		public String toString() {
			return "WhileInstruction [stackName=" + stackName + ", caseTrue=" + caseTrue + "]";
		}

		public List<String> usesStackEntries() {
			List<String> result = arraylist(stackName);
			result.addAll(caseTrue.usesStackEntries());
			return result;
		}

		public List<String> createsStackEntries() {
			List<String> result = arraylist(stackName);
			return result;
		}

	}

	private class CharInstruction extends Instruction {

		//set a stack entry to a symbol
		String stackName;
		String symbol = "";

		public String toString() {
			return "CharInstruction [stackName=" + stackName + ", symbol=" + symbol + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class StringInstruction extends Instruction {

		//set a stack entry to a list of symbols
		String stackName;
		List<String> symbols = new ArrayList<>();

		public String toString() {
			return "StringInstruction [stackName=" + stackName + ", symbols=" + symbols + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	//		functions.put(new MethodSignature("atEquals", arraylist("any", "any")), //
	//		functions.put(new MethodSignature("atEqualsInline", arraylist("any", "any")), //
	//		functions.put(new MethodSignature("next", arraylist("any")), //
	//		functions.put(new MethodSignature("reset", arraylist("any")), //
	//		functions.put(new MethodSignature("previous", arraylist("any")), //
	//		functions.put(new MethodSignature("start", arraylist("any")), //
	//		functions.put(new MethodSignature("end", arraylist("any")), //
	//		functions.put(new MethodSignature("insert", arraylist("any", "any")), //
	//		functions.put(new MethodSignature("insertInline", arraylist("any", "any")), //
	//		functions.put(new MethodSignature("set", arraylist("any", "any")), //
	//		functions.put(new MethodSignature("setInline", arraylist("any", "any")), //
	//		functions.put(new MethodSignature("remove", arraylist("any")), //
	//		functions.put(new MethodSignature("debug_print", arraylist("any")), //
	//		functions.put(new MethodSignature("accept", arraylist("any")), //
	//		functions.put(new MethodSignature("reject", arraylist("any")), //

	private abstract class DefaultFunctionInstruction extends Instruction {

		public DefaultFunctionInstruction(FunctionCall call) {

		}
	}

	private class AtEqualsInstruction extends DefaultFunctionInstruction {

		public AtEqualsInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
			stackName2 = ((VariableRead) call.arguments.get(1)).name.token;
		}

		//compare two values on the stack
		String stackName;
		String stackName2;

		public String toString() {
			return "AtEqualsInstruction [stackName=" + stackName + ", stackName2=" + stackName2 + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName, stackName2);
		}

	}

	private class AtEqualsInlineInstruction extends DefaultFunctionInstruction {

		public AtEqualsInlineInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
			symbol = ((CharacterLiteral) call.arguments.get(1)).contents.token;
		}

		//compare one value on the stack
		String stackName;
		String symbol;

		public String toString() {
			return "AtEqualsInlineInstruction [stackName=" + stackName + ", symbol=" + symbol + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class NextInstruction extends DefaultFunctionInstruction {

		public NextInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
		}

		//move a cursor on the stack
		String stackName;

		public String toString() {
			return "NextInstruction [stackName=" + stackName + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class ResetInstruction extends DefaultFunctionInstruction {

		public ResetInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
		}

		//move a cursor on the stack
		String stackName;

		public String toString() {
			return "ResetInstruction [stackName=" + stackName + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class PreviousInstruction extends DefaultFunctionInstruction {

		public PreviousInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
		}

		//move a cursor on the stack
		String stackName;

		public String toString() {
			return "PreviousInstruction [stackName=" + stackName + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class StartInstruction extends DefaultFunctionInstruction {

		public StartInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
		}

		//check if the cursor of a stack entry is at the start
		String stackName;

		public String toString() {
			return "StartInstruction [stackName=" + stackName + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class EndInstruction extends DefaultFunctionInstruction {

		public EndInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
		}

		//check if the cursor of a stack entry is at the end
		String stackName;

		public String toString() {
			return "EndInstruction [stackName=" + stackName + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class InsertInstruction extends DefaultFunctionInstruction {

		public InsertInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
			source = ((VariableRead) call.arguments.get(1)).name.token;
		}

		//insert a symbol into a stack entry from another stack entry
		String stackName;
		String source;

		public String toString() {
			return "InsertInstruction [stackName=" + stackName + ", source=" + source + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName, source);
		}

	}

	private class InsertInlineInstruction extends DefaultFunctionInstruction {

		public InsertInlineInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
			symbol = ((CharacterLiteral) call.arguments.get(1)).contents.token;
		}

		//insert a symbol into a stack entry from code
		String stackName;
		String symbol;

		public String toString() {
			return "InsertInlineInstruction [stackName=" + stackName + ", symbol=" + symbol + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class SetInstruction extends DefaultFunctionInstruction {

		public SetInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
			source = ((VariableRead) call.arguments.get(1)).name.token;
		}

		//set a symbol into a stack entry from another stack entry
		String stackName;
		String source;

		public String toString() {
			return "SetInstruction [stackName=" + stackName + ", source=" + source + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName, source);
		}

	}

	private class SetInlineInstruction extends DefaultFunctionInstruction {

		public SetInlineInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
			symbol = ((CharacterLiteral) call.arguments.get(1)).contents.token;
		}

		//set a symbol into a stack entry from code
		String stackName;
		String symbol;

		public String toString() {
			return "SetInlineInstruction [stackName=" + stackName + ", symbol=" + symbol + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class RemoveInstruction extends DefaultFunctionInstruction {

		public RemoveInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
		}

		//remove a symbol from a stack entry
		String stackName;

		public String toString() {
			return "RemoveInstruction [stackName=" + stackName + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class DebugPrintInstruction extends DefaultFunctionInstruction {

		public DebugPrintInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
		}

		//copy the contents of a stack entry to the debug print.
		String stackName;

		public String toString() {
			return "DebugPrintInstruction [stackName=" + stackName + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class AcceptInstruction extends DefaultFunctionInstruction {

		public AcceptInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
		}

		//copy the contents of a stack entry to output.
		String stackName;

		public String toString() {
			return "AcceptInstruction [stackName=" + stackName + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}

	private class RejectInstruction extends DefaultFunctionInstruction {

		public RejectInstruction(FunctionCall call) {
			super(call);
			stackName = ((VariableRead) call.arguments.get(0)).name.token;
		}

		//copy the contents of a stack entry to output.
		String stackName;

		public String toString() {
			return "RejectInstruction [stackName=" + stackName + "]";
		}

		public List<String> usesStackEntries() {
			return arraylist(stackName);
		}

	}
}
