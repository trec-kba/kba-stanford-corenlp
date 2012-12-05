package edu.stanford.nlp.parser.ensemble.maltparser.parser.history;



import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideUserAction;

/**
 * 
 * @author Johan Hall
*/
public abstract class HistoryStructure {
	public HistoryStructure() {}

	public abstract HistoryNode getNewHistoryNode(HistoryNode previousNode, GuideUserAction action) throws MaltChainedException;
	public abstract void clear() throws MaltChainedException;
	public abstract void toFile() throws MaltChainedException;
	public abstract void close() throws MaltChainedException;
}
