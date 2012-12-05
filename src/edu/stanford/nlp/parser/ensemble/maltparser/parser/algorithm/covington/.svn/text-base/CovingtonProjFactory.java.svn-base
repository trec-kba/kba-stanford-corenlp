package edu.stanford.nlp.parser.ensemble.maltparser.parser.algorithm.covington;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.Algorithm;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.TransitionSystem;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.OracleGuide;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.GuideUserHistory;
/**
 * @author Johan Hall
 *
 */
public class CovingtonProjFactory  extends CovingtonFactory {
	public CovingtonProjFactory(Algorithm algorithm) {
		super(algorithm);
	}
	
	public TransitionSystem makeTransitionSystem() throws MaltChainedException {
		if (manager.getConfigLogger().isInfoEnabled()) {
			manager.getConfigLogger().info("  Transition system    : Projective\n");
		}
		return new Projective();
	}
	
	public OracleGuide makeOracleGuide(GuideUserHistory history) throws MaltChainedException {
		if (manager.getConfigLogger().isInfoEnabled()) {
			manager.getConfigLogger().info("  Oracle               : Covington\n");
		}
		return new CovingtonOracle(manager, history);
	}
}
