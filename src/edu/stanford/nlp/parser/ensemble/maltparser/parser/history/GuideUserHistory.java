package edu.stanford.nlp.parser.ensemble.maltparser.parser.history;

import java.util.ArrayList;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideUserAction;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.container.ActionContainer;
/**
*
* @author Johan Hall
* @since 1.1
**/
public interface GuideUserHistory {
	public GuideUserAction getEmptyGuideUserAction() throws MaltChainedException; 
	public ArrayList<ActionContainer> getActionContainers();
	public ActionContainer[] getActionContainerArray();
	public int getNumberOfDecisions();
	public void clear() throws MaltChainedException; 
}
