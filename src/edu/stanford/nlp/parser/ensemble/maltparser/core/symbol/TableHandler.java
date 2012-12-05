package edu.stanford.nlp.parser.ensemble.maltparser.core.symbol;

import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;

public interface TableHandler {
	public Table getSymbolTable(String tableName) throws MaltChainedException;
	public Table addSymbolTable(String tableName) throws MaltChainedException;
}
