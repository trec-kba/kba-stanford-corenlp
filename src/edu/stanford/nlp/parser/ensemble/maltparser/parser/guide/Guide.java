package edu.stanford.nlp.parser.ensemble.maltparser.parser.guide;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.DependencyStructure;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.DependencyParserConfig;
/**
*
* @author Johan Hall
* @since 1.1
**/
public interface Guide {
//	public enum GuideMode { BATCH, ONLINE, CLASSIFY}
	
//	public void addInstance(GuideDecision decision) throws MaltChainedException;
	public void finalizeSentence(DependencyStructure dependencyGraph) throws MaltChainedException;
//	public void noMoreInstances() throws MaltChainedException;
	public void terminate() throws MaltChainedException;
	
//	public void predict(GuideDecision decision) throws MaltChainedException;
//	public boolean predictFromKBestList(GuideDecision decision) throws MaltChainedException;
	
	public DependencyParserConfig getConfiguration();
//	public GuideMode getGuideMode();
//	public GuideHistory getHistory();
//	public FeatureModelManager getFeatureModelManager();
	public String getGuideName();
	public void setGuideName(String guideName);
}
