package edu.stanford.nlp.parser.ensemble.maltparser.parser.history;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideUserAction;
/**
 * 
 * @author Johan Hall
*/
public interface HistoryNode {
	public HistoryNode getPreviousNode();
	public GuideUserAction getAction();
	public void setAction(GuideUserAction action);
	public void setPreviousNode(HistoryNode node);
//	public double getScore();
//	public void setScore(double score);
	public int getPosition();
	public void clear() throws MaltChainedException;
}
