package edu.stanford.nlp.parser.ensemble.maltparser.parser;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.AbstractFeatureFactory;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.OracleGuide;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.GuideUserHistory;
/**
 * @author Johan Hall
 *
 */
public interface AbstractParserFactory extends AbstractFeatureFactory {
	/**
	 * Creates a parser configuration
	 * 
	 * @return a parser configuration
	 * @throws MaltChainedException
	 */
	public ParserConfiguration makeParserConfiguration() throws MaltChainedException;
	/**
	 * Creates a transition system
	 * 
	 * @return a transition system
	 * @throws MaltChainedException
	 */
	public TransitionSystem makeTransitionSystem() throws MaltChainedException;
	/**
	 * Creates an oracle guide
	 * 
	 * @param history a reference to the history
	 * @return  an oracle guide
	 * @throws MaltChainedException
	 */
	public OracleGuide makeOracleGuide(GuideUserHistory history) throws MaltChainedException;
}
