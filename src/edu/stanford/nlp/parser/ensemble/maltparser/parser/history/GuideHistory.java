package edu.stanford.nlp.parser.ensemble.maltparser.parser.history;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.symbol.TableHandler;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideDecision;

/**
*
* @author Johan Hall
* @since 1.1
**/
public interface GuideHistory {
	public GuideDecision getEmptyGuideDecision() throws MaltChainedException; // During classification time
	public int getNumberOfDecisions();
	public TableHandler getTableHandler(String name);
	public void setKBestListClass(Class<?> kBestListClass) throws MaltChainedException;
	public Class<?> getKBestListClass();
	public int getKBestSize();
	public void setKBestSize(int kBestSize);
	public void setSeparator(String separator) throws MaltChainedException;
}
