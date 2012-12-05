package edu.stanford.nlp.parser.ensemble.maltparser.parser.guide;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideUserAction;
/**
*
* @author Johan Hall
* @since 1.1
**/
public interface Guidable {
	public void setInstance(GuideUserAction action) throws MaltChainedException;
	public void predict(GuideUserAction action) throws MaltChainedException;
	public boolean predictFromKBestList(GuideUserAction action) throws MaltChainedException;
}
