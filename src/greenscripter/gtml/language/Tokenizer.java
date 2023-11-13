package greenscripter.gtml.language;

import java.util.ArrayList;
import java.util.List;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Tokenizer {

	List<Token> tokens = new ArrayList<>();

	public static void main(String[] args) throws IOException {
		Tokenizer t = new Tokenizer(new File("testcode.gtml"));
		for (Token s : t.tokens) {
			System.out.println(s.getErrored());
		}
	}

	public Tokenizer(File file) throws IOException {
		try (BufferedReader input = new BufferedReader(new FileReader(file))) {
			String line;
			int lineNumber = 0;
			boolean inComment = false;
			while ((line = input.readLine()) != null) {
				lineNumber++;
				line = line.strip();
				Token part = new Token("", line, lineNumber, 0);
				boolean inDoubleQuotes = false;
				boolean inSingleQuotes = false;
				for (int i = 0; i < line.length(); i++) {
					if (inDoubleQuotes) { // Double Quotes
						if (line.charAt(i) == '\\') {
							if (i + 1 < line.length()) {
								if (line.charAt(i + 1) == 'n') {
									part.token += "\n";
								} else {
									part.token += line.charAt(i + 1);
								}
								i++;
							} else {
								throw new RuntimeException("Invalid escaping.");
							}
						} else if (line.charAt(i) == '"') {
							inDoubleQuotes = false;
							tokens.add(part);
							part = new Token("", line, lineNumber, i + 1);
							tokens.add(new Token("\"", line, lineNumber, i));
						} else {
							part.token += line.charAt(i);
						}
					} else if (inSingleQuotes) { // Single Quotes
						if (line.charAt(i) == '\\') {
							if (i + 1 < line.length()) {
								if (line.charAt(i + 1) == 'n') {
									part.token += "\n";
								} else {
									part.token += line.charAt(i + 1);
								}
								i++;
							} else {
								throw new RuntimeException("Invalid escaping.");
							}
						} else if (line.charAt(i) == '\'') {
							inSingleQuotes = false;
							tokens.add(part);
							part = new Token("", line, lineNumber, i + 1);
							tokens.add(new Token("\'", line, lineNumber, i));
						} else {
							part.token += line.charAt(i);
						}
					} else if (inComment) { // Multi-line Comments
						if (line.charAt(i) == '*') {
							if (i + 1 < line.length()) {
								if (line.charAt(i + 1) == '/') {
									inComment = false;
									i++;
								}
							}
						}
					} else if (line.charAt(i) == '"') { // Double Quotes
						inDoubleQuotes = true;
						if (!part.is("")) tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token("\"", line, lineNumber, i));
					} else if (line.charAt(i) == '\'') { // Single Quotes
						inSingleQuotes = true;
						if (!part.is("")) tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token("\'", line, lineNumber, i));
					} else if (line.charAt(i) == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
						inComment = true;
						if (!part.is("")) tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
					} else if (line.charAt(i) == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') { // Single Line Comments
						if (!part.is("")) tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						break;
					} else if (line.charAt(i) == '(') { // Split on characters
						if (!part.is("")) tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token("(", line, lineNumber, i));
					} else if (line.charAt(i) == ')') {
						if (!part.is("")) tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token(")", line, lineNumber, i));
					} else if (line.charAt(i) == '}') {
						if (!part.is("")) tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token("}", line, lineNumber, i));
					} else if (line.charAt(i) == '{') {
						if (!part.is("")) tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token("{", line, lineNumber, i));
					} else if (line.charAt(i) == '#') {
						if (!part.is("")) tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token("#", line, lineNumber, i));
					} else if (line.charAt(i) == ',') {
						if (!part.is("")) tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token(",", line, lineNumber, i));
					} else if (line.charAt(i) == ' ' || line.charAt(i) == '\t') { // Split on blanks
						if (!part.is("")) tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
					} else { // Normal Characters
						part.token += line.charAt(i);
					}
				}
				if (!part.is("")) tokens.add(part);
			}

		}
	}

	public TokenIterator getIterator() {
		return new TokenIterator();
	}

	public class TokenIterator {

		private int start;
		private int end;
		private int at;

		public TokenIterator() {
			this(0, tokens.size(), -1);
		}

		private TokenIterator(int start, int end, int at) {
			this.start = start;
			this.end = end;
			this.at = at;
		}

		public Token next() {
			if (at >= end - 1) {
				return null;
			}
			at++;
			Token token = tokens.get(at);
			return token;
		}

		public Token throwingNext(String message) {
			if (at >= end - 1) {
				throw new TokenException(message, this.getEndErrorToken());
			}
			at++;
			Token token = tokens.get(at);
			return token;
		}

		public boolean hasNext() {
			if (at >= end - 1) {
				return false;
			}
			return true;
		}

		public Token get() {
			if (at < start || at >= end) {
				return null;
			}
			Token token = tokens.get(at);
			return token;
		}

		public Token previous() {
			if (at < start) {
				return null;
			}
			Token token = tokens.get(at);
			at--;
			return token;
		}

		public TokenIterator segment() {
			return new TokenIterator(at, end, at);
		}

		public TokenIterator copy() {
			return new TokenIterator(start, end, at);
		}

		public TokenIterator endAt(TokenIterator other) {
			end = other.at;
			return this;
		}

		public void fastForwardTo(TokenIterator other) {
			if (Tokenizer.this != other.getParent()) {
				throw new IllegalArgumentException("Iterators have different parents.");
			}
			if (other.at > end) {
				throw new IndexOutOfBoundsException(at);
			}
			if (other.at > at) {
				at = other.at;
			}
		}

		public String toString() {
			return tokens.subList(start + 1, end).toString();
		}

		public Token getEndErrorToken() {
			if (tokens.isEmpty()) return new Token("No Such Token", "No Such Token", 0, 0);
			if (end >= tokens.size()) {
				return tokens.get(tokens.size() - 1);
			}
			return tokens.get(end);
		}

		private Tokenizer getParent() {
			return Tokenizer.this;
		}
	}

	public static class Token {

		public String token;
		public String lineContent;
		public int line;
		public int pos;

		public Token() {

		}

		public Token(String token, String lineContent, int line, int pos) {
			super();
			this.token = token;
			this.lineContent = lineContent;
			this.line = line;
			this.pos = pos;
		}

		public String toString() {
			return token;
		}

		public String getErrored() {
			StringBuilder sb = new StringBuilder();
			sb.append("line ");
			sb.append(line);
			sb.append(": ");
			sb.append(lineContent);
			sb.append("\n");
			int prefix = ("line " + line + ": ").length();
			for (int i = 0; i < prefix + lineContent.length(); i++) {
				if (i - prefix == pos) {
					sb.append("^");
				} else if (i - prefix > pos && i - prefix < pos + token.length()) {
					sb.append("^");
				} else {
					sb.append(" ");
				}
			}
			return sb.toString();
		}

		public boolean is(String token) {
			return this.token.equals(token);
		}

		public Token forceIs(String token) {
			if (!this.is(token)) {
				throw new TokenException("Unexpected " + this.token + ", expected " + token, this);
			}
			return this;
		}

		public boolean isName() {
			if (token.equals("while")) return false;
			if (token.equals("if")) return false;
			if (token.equals("else")) return false;
			if (token.equals("return")) return false;
			if (token.equals("func")) return false;
			return token.matches("[a-zA-Z][a-zA-Z0-9_]*");
		}

		public Token forceName() {
			if (!this.isName()) {
				throw new TokenException("Invalid identifier " + token, this);
			}
			return this;
		}

		public boolean isSameLine(Token other) {
			return other.line != this.line;
		}

		public Token forceSameLine(Token other) {
			if (isSameLine(other)) {
				throw new TokenException("Tokens must be on the same line " + "\n" + other.getErrored(), this);
			}
			return this;
		}

	}

}
