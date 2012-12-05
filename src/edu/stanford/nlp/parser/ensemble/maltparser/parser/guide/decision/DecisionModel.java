package edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.decision;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.FeatureModel;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.FeatureVector;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.Model;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideDecision;
/**
*
* @author Johan Hall
* @since 1.1
**/
public interface DecisionModel extends Model {
	public void updateFeatureModel() throws MaltChainedException;
	public void updateCardinality() throws MaltChainedException;
	
	public void addInstance(GuideDecision decision) throws MaltChainedException;
	public boolean predict(GuideDecision decision) throws MaltChainedException;
	public FeatureVector predictExtract(GuideDecision decision) throws MaltChainedException;
	public FeatureVector extract() throws MaltChainedException;
	public boolean predictFromKBestList(GuideDecision decision) throws MaltChainedException;
	
	public FeatureModel getFeatureModel();
	public int getDecisionIndex();
}
