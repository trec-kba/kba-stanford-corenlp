package edu.stanford.nlp.parser.ensemble.maltparser.parser.guide;



import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.DependencyStructure;
/**
*
* @author Johan Hall
* @since 1.0
**/
public interface Model {
	public void finalizeSentence(DependencyStructure dependencyGraph) throws MaltChainedException;
	public void noMoreInstances() throws MaltChainedException;
	public void terminate() throws MaltChainedException;
	
	public ClassifierGuide getGuide();
	public String getModelName() throws MaltChainedException;
}
