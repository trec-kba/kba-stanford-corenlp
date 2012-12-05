package edu.stanford.nlp.parser.ensemble.maltparser.parser;


import edu.stanford.nlp.parser.ensemble.maltparser.core.config.Configuration;
import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.DependencyStructure;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.ClassifierGuide;
/**
 * @author Johan Hall
 *
 */
public interface DependencyParserConfig extends Configuration {
	public void parse(DependencyStructure graph) throws MaltChainedException;
	public void oracleParse(DependencyStructure goldGraph, DependencyStructure oracleGraph) throws MaltChainedException;
	public ClassifierGuide getGuide();
	public Algorithm getAlgorithm();
}
