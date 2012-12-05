package edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.instance;



import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.FeatureVector;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.Model;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.SingleDecision;

public interface InstanceModel extends Model {
	public void addInstance(SingleDecision decision) throws MaltChainedException;
	public boolean predict(SingleDecision decision) throws MaltChainedException;
	public FeatureVector predictExtract(SingleDecision decision) throws MaltChainedException;
	public FeatureVector extract() throws MaltChainedException;
	public void train() throws MaltChainedException;
	public void increaseFrequency();
	public void decreaseFrequency();
}
