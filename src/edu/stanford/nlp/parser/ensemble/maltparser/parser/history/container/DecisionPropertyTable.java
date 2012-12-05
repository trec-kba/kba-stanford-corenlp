package edu.stanford.nlp.parser.ensemble.maltparser.parser.history.container;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.symbol.Table;
/**
*
* @author Johan Hall
* @since 1.1
**/
public interface DecisionPropertyTable {
	public boolean continueWithNextDecision(int code) throws MaltChainedException;
	public boolean continueWithNextDecision(String symbol) throws MaltChainedException;
	public Table getTableForNextDecision(int code) throws MaltChainedException;
	public Table getTableForNextDecision(String symbol) throws MaltChainedException;
}
