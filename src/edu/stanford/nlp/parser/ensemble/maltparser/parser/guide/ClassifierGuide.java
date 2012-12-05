package edu.stanford.nlp.parser.ensemble.maltparser.parser.guide;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.FeatureModelManager;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.FeatureVector;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.GuideHistory;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideDecision;

public interface ClassifierGuide extends Guide {
	public enum GuideMode { BATCH, CLASSIFY}
	
	public void addInstance(GuideDecision decision) throws MaltChainedException;
	public void noMoreInstances() throws MaltChainedException;
	public void predict(GuideDecision decision) throws MaltChainedException;
	public FeatureVector predictExtract(GuideDecision decision) throws MaltChainedException;
	public FeatureVector extract() throws MaltChainedException;
	public boolean predictFromKBestList(GuideDecision decision) throws MaltChainedException;
	
	public GuideMode getGuideMode();
	public GuideHistory getHistory();
	public FeatureModelManager getFeatureModelManager();
}
