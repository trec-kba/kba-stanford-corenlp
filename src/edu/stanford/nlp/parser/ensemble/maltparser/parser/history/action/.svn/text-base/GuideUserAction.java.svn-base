package edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action;

import java.util.ArrayList;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.GuideUserHistory;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.container.ActionContainer;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.kbest.ScoredKBestList;
/**
*
* @author Johan Hall
* @since 1.1
**/
public interface GuideUserAction {
	public void addAction(ArrayList<ActionContainer> actionContainers) throws MaltChainedException;
	public void addAction(ActionContainer[] actionContainers) throws MaltChainedException;
	public void getAction(ArrayList<ActionContainer> actionContainers) throws MaltChainedException;
	public void getAction(ActionContainer[] actionContainers) throws MaltChainedException;
	public void getKBestLists(ArrayList<ScoredKBestList> kbestListContainers) throws MaltChainedException;
	public void getKBestLists(ScoredKBestList[] kbestListContainers) throws MaltChainedException;
	public int numberOfActions();
	public GuideUserHistory getGuideUserHistory();
}
