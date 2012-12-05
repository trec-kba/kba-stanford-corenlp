package edu.stanford.nlp.parser.ensemble.maltparser.parser.guide;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.DependencyStructure;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.ParserConfiguration;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideUserAction;

public interface OracleGuide extends Guide {
	public GuideUserAction predict(DependencyStructure gold, ParserConfiguration config) throws MaltChainedException;
}
