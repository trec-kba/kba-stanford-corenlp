package edu.stanford.nlp.parser.ensemble.maltparser.parser.transition;

import java.util.HashMap;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.symbol.Table;
import edu.stanford.nlp.parser.ensemble.maltparser.core.symbol.TableHandler;
/**
*
* @author Johan Hall
* @since 1.1
**/
public class TransitionTableHandler implements TableHandler{
	private final HashMap<String, TransitionTable> transitionTables;

	public TransitionTableHandler() {
		transitionTables = new HashMap<String, TransitionTable>();
	}
	
	public Table addSymbolTable(String tableName) throws MaltChainedException {
		TransitionTable table = transitionTables.get(tableName);
		if (table == null) {
			table = new TransitionTable(tableName);
			transitionTables.put(tableName, table);
		}
		return table;
	}

	public Table getSymbolTable(String tableName) throws MaltChainedException {
		return transitionTables.get(tableName);
	}
}
