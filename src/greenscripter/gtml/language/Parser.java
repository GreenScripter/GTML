package greenscripter.gtml.language;

import java.util.ArrayList;
import java.util.List;

import java.io.File;
import java.io.IOException;

import greenscripter.gtml.language.Tokenizer.Token;
import greenscripter.gtml.language.Tokenizer.TokenIterator;

public class Parser {

	public static void main(String[] args) throws IOException {
		Tokenizer tok = new Tokenizer(new File("testcode.gtml"));
		new Parser(tok);
		TokenIterator it = tok.getIterator();
		System.out.println(CodeBlock.indentedToString(new CodeBlock(it).toString()));
		System.out.println(new CodeBlock(tok.getIterator()).write(true));
	}

	public CodeBlock code;

	public Parser() {

	}

	public Parser(File f) throws IOException {
		this(new Tokenizer(f));
	}

	public Parser(Tokenizer tokenizer) {
		code = new CodeBlock(tokenizer.getIterator());
	}

	private static TokenIterator getParentheses(TokenIterator iterator) {
		Token start = iterator.get();
		TokenIterator child = iterator.segment();
		int layer = 1;
		while (layer > 0) {
			Token token = iterator.next();
			if (token == null) {
				throw new TokenException("No matching parentheses", start);
			} else if (token.token.equals("\"") || token.token.equals("\'")) {
				iterator.next();
				Token endQuote = iterator.next();
				if (endQuote == null) {
					throw new TokenException("No matching quotes", start);
				}
				endQuote.forceIs(token.token);
			} else if (token.token.equals("(")) {
				layer++;
			} else if (token.token.equals(")")) {
				layer--;
			}
		}
		return child.endAt(iterator);
	}

	private static TokenIterator getBrackets(TokenIterator iterator) {
		Token start = iterator.get();
		TokenIterator child = iterator.segment();
		int layer = 1;
		while (layer > 0) {
			Token token = iterator.next();
			if (token == null) {
				throw new TokenException("No matching brackets", start);
			} else if (token.token.equals("\"") || token.token.equals("\'")) {
				iterator.next();
				Token endQuote = iterator.next();
				if (endQuote == null) {
					throw new TokenException("No matching quotes", start);
				}
				endQuote.forceIs(token.token);
			} else if (token.token.equals("{")) {
				layer++;
			} else if (token.token.equals("}")) {
				layer--;
			}
		}
		return child.endAt(iterator);
	}

	private static Code parse(TokenIterator tokens) {
		TokenIterator inspector = tokens.copy();
		Token next = inspector.next();
		if (next == null) return null;
		if (next.is("#")) return new CompilerInfo(tokens);
		if (next.is("{")) {
			tokens.next().forceIs("{");
			return new CodeBlock(getBrackets(tokens));
		}
		if (next.isName()) {
			Token after = inspector.next();
			if (after == null) {
				return new VariableRead(tokens);
			}
			if (after.is("(")) {
				return new FunctionCall(tokens);
			} else {
				try {
					TokenIterator tester = tokens.copy();
					Assignment a = new Assignment(tester);
					tokens.fastForwardTo(tester);
					return a;
				} catch (Assignment.AssignmentFail e) {
					return new VariableRead(tokens);
				}
			}
		}
		if (next.is("while") || next.is("if")) {
			return new ControlStatement(tokens);
		}
		if (next.is("else")) {
			return new ElseStatement(tokens);
		}
		if (next.is("func")) {
			return new FunctionDefinition(tokens);
		}
		if (next.is("\"")) {
			return new StringLiteral(tokens);
		}
		if (next.is("\'")) {
			return new CharacterLiteral(tokens);
		}
		if (next.is("(")) {
			tokens.next().forceIs("(");
			TokenIterator it = getParentheses(tokens);
			Code c = parse(it);
			if (it.hasNext()) {
				throw new TokenException("Extra content in parentheses", it.next());
			}
			return c;
		}
		if (next.is("return")) {
			return new Return(tokens);
		}
		throw new TokenException("Unexpected content", next);
	}

	public static abstract class Code {

		public abstract String write();
	}

	public static class CodeBlock extends Code {

		public List<Code> blocks = new ArrayList<>();

		public CodeBlock(TokenIterator tokens) {
			while (tokens.hasNext()) {
				Code c = parse(tokens);
				if (c == null) {
					throw new TokenException("Unexpected token ", tokens.next());
				}
				blocks.add(c);
			}
		}

		public String toString() {
			return "CodeBlock [\n" + blocks.stream().map(Object::toString).reduce((s1, s2) -> s1 + "\n" + s2).orElse("") + "\n]";
		}

		public static String indentedToString(String s) {
			String result = "";
			int layer = 0;
			for (int i = 0; i < s.length(); i++) {
				if (s.charAt(i) == '[') {
					layer++;
				}
				if (s.charAt(i) == ']') {
					layer--;
				}
				result += s.charAt(i);
				if (s.charAt(i) == '\n') {
					for (int j = 0; j < layer; j++) {
						result += '\t';
					}
				}
			}

			return result;
		}

		public String write() {
			return write(false);
		}

		public String write(boolean direct) {
			StringBuilder sb = new StringBuilder();
			if (!direct) sb.append("{\n");
			for (int i = 0; i < this.blocks.size(); i++) {
				Code c = this.blocks.get(i);
				String child = c.write();
				sb.append(child);
				if (i + 1 < this.blocks.size()) {
					if (this.blocks.get(i + 1) instanceof ElseStatement) {
						sb.append(" ");
					} else {
						sb.append("\n");
					}
				}
			}
			if (direct) return sb.toString();
			return sb.toString().replace("\n", "\n\t") + "\n}";
		}

	}

	public static class FunctionCall extends Code {

		public Token name;
		public List<Code> arguments = new ArrayList<>();

		public FunctionCall(TokenIterator tokens) {
			name = tokens.throwingNext("No function name").forceName();

			tokens.throwingNext("No arguments for function").forceIs("(").forceSameLine(name);

			TokenIterator subTokens = getParentheses(tokens);
			while (subTokens.hasNext()) {
				arguments.add(parse(subTokens));
				if (subTokens.hasNext()) {
					subTokens.next().forceIs(",");
					if (!subTokens.hasNext()) {
						throw new TokenException("Missing function argument", subTokens.getEndErrorToken());
					}
				}
			}
		}

		public String toString() {
			return "FunctionCall [name=" + name + " args=" + arguments + "]";
		}

		public String write() {
			StringBuilder sb = new StringBuilder();
			sb.append(name);
			sb.append("(");
			for (Code c : arguments) {
				sb.append(c.write());
				sb.append(", ");
			}
			if (!arguments.isEmpty()) {
				sb.setLength(sb.length() - 2);
			}
			sb.append(")");
			return sb.toString();
		}
	}

	public static class CompilerInfo extends Code {

		public Token name;
		public List<Token> contents = new ArrayList<>();

		public CompilerInfo(TokenIterator tokens) {
			Token tmp = tokens.throwingNext("Invalid compiler info").forceIs("#");
			name = tokens.throwingNext("No name token").forceSameLine(tmp).forceName();

			Token parenthesis = tokens.copy().next();
			if (parenthesis != null && parenthesis.is("(")) {
				tokens.next().forceSameLine(name);
				TokenIterator contents = getParentheses(tokens);
				while (contents.next() != null) {
					if (contents.get().is("\"")) {
						this.contents.add(contents.throwingNext("Missing string contents"));
						contents.throwingNext("Missing closing quote").forceIs("\"");
					} else if (contents.get().is("\'")) {
						this.contents.add(contents.throwingNext("Missing character contents"));
						contents.throwingNext("Missing closing quote").forceIs("\'");
					} else {
						this.contents.add(contents.get());
					}
					if (contents.hasNext()) {
						contents.next().forceIs(",");
					}
				}
			}
		}

		public String toString() {
			return "CompilerInfo [name=" + name + ", contents=" + contents + "]";
		}

		public String write() {
			return "#" + name + "(" + contents.stream().map(Token::toString).reduce((s1, s2) -> s1 + ", " + s2).orElse("") + ")";
		}

	}

	public static class ControlStatement extends Code {

		public Token name;
		public Code arguments;
		public Code contents;

		public ControlStatement(TokenIterator tokens) {
			name = tokens.throwingNext("Missing control type");
			tokens.throwingNext("No arguments for control block").forceIs("(");

			TokenIterator subTokens = getParentheses(tokens);
			arguments = parse(subTokens);
			Token block = tokens.copy().next();
			if (block != null && block.is("{")) {
				block.forceSameLine(name);
			}
			contents = parse(tokens);
		}

		public String toString() {
			return "ControlStatement [name=" + name + ", arguments=" + arguments + ", contents=" + contents + "]";
		}

		public String write() {
			return name + " (" + arguments.write() + ") " + contents.write();
		}

	}

	public static class ElseStatement extends Code {

		public Code contents;

		public ElseStatement(TokenIterator tokens) {
			tokens.throwingNext("Missing else text").forceIs("else");

			contents = parse(tokens);
		}

		public String toString() {
			return "ElseStatement [" + "contents=" + contents + "]";
		}

		public String write() {
			return "else " + contents.write();
		}
	}

	public static class FunctionDefinition extends Code {

		public List<Token> returnTypes = new ArrayList<>();
		public Token name;
		public List<Token> arguments = new ArrayList<>();
		public List<Token> argumentTypes = new ArrayList<>();
		public Code body;

		public FunctionDefinition(TokenIterator tokens) {
			Token decl = tokens.throwingNext("Missing function declaration").forceIs("func");
			name = tokens.throwingNext("Missing function name").forceName().forceSameLine(decl);

			tokens.throwingNext("No argument definition for function").forceIs("(");

			TokenIterator subTokens = getParentheses(tokens);

			while (subTokens.hasNext()) {
				argumentTypes.add(subTokens.next().forceName());
				arguments.add(subTokens.throwingNext("Missing argument name").forceName());
			}

			Token returns = tokens.copy().next();
			if (returns != null && returns.is("->")) {
				tokens.next();
				returnTypes.add(tokens.throwingNext("Missing return type").forceName());
				Token comma;
				while ((comma = tokens.copy().next()) != null && comma.is(",")) {
					tokens.throwingNext("Disappearing comma").forceIs(",");
					returnTypes.add(tokens.throwingNext("Missing return type").forceName());
				}
			}
			Token block = tokens.copy().next();
			if (block != null && block.is("{")) {
				block.forceSameLine(name);
			}
			body = parse(tokens);
			if (body == null) {
				throw new TokenException("Missing function body", tokens.getEndErrorToken());
			}

		}

		public String toString() {
			return "FunctionDefinition [name=" + name + " argTypes=" + argumentTypes + " args=" + arguments + " body=" + body + " returnTypes=" + returnTypes + "]";
		}

		public String write() {
			StringBuilder sb = new StringBuilder();
			sb.append("func ");
			sb.append(name);
			sb.append("(");
			for (int i = 0; i < arguments.size(); i++) {
				sb.append(argumentTypes.get(i));
				sb.append(" ");
				sb.append(arguments.get(i));
				if (i + 1 < arguments.size()) {
					sb.append(", ");
				}
			}
			sb.append(")");
			if (!returnTypes.isEmpty()) {
				sb.append(" -> ");
				for (int i = 0; i < returnTypes.size(); i++) {
					sb.append(returnTypes.get(i));
					if (i + 1 < returnTypes.size()) {
						sb.append(", ");
					}
				}
			}
			sb.append(" ");

			sb.append(body.write());

			return sb.toString();
		}
	}

	public static class VariableRead extends Code {

		public Token name;

		public VariableRead(TokenIterator tokens) {
			name = tokens.throwingNext("Missing variable name").forceName();
		}

		public String toString() {
			return "VariableRead [name=" + name + "]";
		}

		public String write() {
			return name.toString();
		}

	}

	public static class Assignment extends Code {

		public List<Destination> names = new ArrayList<>();
		public List<Code> sources = new ArrayList<>();

		public Assignment(TokenIterator tokens) {
			try {
				Token next;
				while (!(next = tokens.throwingNext("Missing assignment infomation")).is("=")) {
					if (next.is(",")) {
						next = tokens.throwingNext("Trailing assignment comma");
					}
					Token after = tokens.copy().throwingNext("Missing assignment infomation");
					if (!after.is("=") && !after.is(",")) {
						tokens.next().forceName();
						names.add(new Destination(next, after));
					} else {
						names.add(new Destination(next));
						if (after.is(",")) {
							tokens.next();
						}
					}
				}
			} catch (TokenException e) {
				throw new AssignmentFail(e);
			}
			while (true) {
				Code c = parse(tokens);
				if (c == null) throw new TokenException("Missing assignment result", tokens.getEndErrorToken());
				sources.add(c);
				if (tokens.hasNext() && tokens.copy().next().is(",")) {
					tokens.next();
				} else {
					break;
				}
			}

		}

		static class AssignmentFail extends RuntimeException {

			public AssignmentFail(Exception e) {
				super(e);
			}

		}

		public static class Destination {

			public Token type;
			public Token name;

			public Destination(Token name) {
				this.name = name;
			}

			public Destination(Token type, Token name) {
				this.type = type;
				this.name = name;
			}

			public String toString() {
				return "Destination [" + (type != null ? "type=" + type + ", " : "") + (name != null ? "name=" + name : "") + "]";
			}

			public String write() {
				if (type != null)
					return type + " " + name;
				else
					return name.toString();
			}

		}

		public String toString() {
			return "Assignment [names=" + names + ", sources=" + sources + "]";
		}

		public String write() {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < names.size(); i++) {
				sb.append(names.get(i).write());
				if (i + 1 < names.size()) {
					sb.append(", ");
				}
			}
			sb.append(" = ");
			for (int i = 0; i < sources.size(); i++) {
				String source = sources.get(i).write();
				if (source.contains(",")) {
					//					sb.append("(");
					sb.append(source);
					//					sb.append(")");
				} else {
					sb.append(source);
				}
				if (i + 1 < sources.size()) {
					sb.append(", ");
				}
			}
			return sb.toString();
		}

	}

	public static class StringLiteral extends Code {

		public Token contents;

		public StringLiteral(TokenIterator tokens) {
			tokens.throwingNext("Missing string start quotes").is("\"");
			contents = tokens.throwingNext("Missing string contents");
			tokens.throwingNext("Missing string end quotes").is("\"");
		}

		public String toString() {
			return "StringLiteral [contents=" + contents + "]";
		}

		public String write() {
			return "\"" + (contents.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")) + "\"";
		}

	}

	public static class CharacterLiteral extends Code {

		public Token contents;

		public CharacterLiteral(TokenIterator tokens) {
			tokens.throwingNext("Missing character start quotes").is("\'");
			contents = tokens.throwingNext("Missing character contents");
			tokens.throwingNext("Missing character end quotes").is("\'");
		}

		public String toString() {
			return "CharacterLiteral [contents=" + contents + "]";
		}

		public String write() {
			return "\'" + (contents.toString().replace("\\", "\\\\").replace("\'", "\\\'").replace("\n", "\\n")) + "\'";
		}
	}

	public static class Return extends Code {

		public List<Code> statements = new ArrayList<>();

		public Return(TokenIterator tokens) {
			tokens.throwingNext("Missing return statment").forceIs("return");

			do {
				Code c = parse(tokens);
				if (c == null) throw new TokenException("Missing return value", tokens.getEndErrorToken());
				statements.add(c);
			} while (tokens.hasNext() && (tokens.copy().next().is(",") && tokens.next().equals(tokens.get())));
		}

		public String toString() {
			return "Return [statements=" + statements + "]";
		}

		public String write() {
			StringBuilder sb = new StringBuilder();
			sb.append("return ");
			for (int i = 0; i < statements.size(); i++) {
				String source = statements.get(i).write();
				if (source.contains(",")) {
					//					sb.append("(");
					sb.append(source);
					//					sb.append(")");
				} else {
					sb.append(source);
				}
				if (i + 1 < statements.size()) {
					sb.append(", ");
				}
			}
			return sb.toString();
		}
	}

}
