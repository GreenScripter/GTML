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
		new Tokenizer(new File("testcode.gtml"));
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
						tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token("\"", line, lineNumber, i));
					} else if (line.charAt(i) == '\'') { // Single Quotes
						inSingleQuotes = true;
						tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token("\'", line, lineNumber, i));
					} else if (line.charAt(i) == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
						inComment = true;
						tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
					} else if (line.charAt(i) == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') { // Single Line Comments
						tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						break;
					} else if (line.charAt(i) == '(') { // Split on characters
						tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token("(", line, lineNumber, i));
					} else if (line.charAt(i) == ')') {
						tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token(")", line, lineNumber, i));
					} else if (line.charAt(i) == '}') {
						tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token("}", line, lineNumber, i));
					} else if (line.charAt(i) == '{') {
						tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token("{", line, lineNumber, i));
					} else if (line.charAt(i) == '#') {
						tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token("#", line, lineNumber, i));
					} else if (line.charAt(i) == ',') {
						tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
						tokens.add(new Token(",", line, lineNumber, i));
					} else if (line.charAt(i) == ' ' || line.charAt(i) == '\t') { // Split on blanks
						tokens.add(part);
						part = new Token("", line, lineNumber, i + 1);
					} else { // Normal Characters
						part.token += line.charAt(i);
					}
				}
				tokens.add(part);
			}

		}
		tokens.removeIf(t -> t.token.isEmpty());
		for (Token s : tokens) {
			System.out.println(s.getErrored());
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

	}

}
