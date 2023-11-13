package greenscripter.gtml.language;

import greenscripter.gtml.language.Tokenizer.Token;

public class TokenException extends RuntimeException {

	public TokenException(String message, Token t) {
		super(message + "\n" + t.getErrored() + "\n");
	}
}
