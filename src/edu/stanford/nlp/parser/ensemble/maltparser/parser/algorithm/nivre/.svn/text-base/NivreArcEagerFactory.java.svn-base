package edu.stanford.nlp.parser.ensemble.maltparser.parser.algorithm.nivre;



import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.Algorithm;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.TransitionSystem;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.OracleGuide;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.GuideUserHistory;
/**
 * @author Johan Hall
 *
 */
public class NivreArcEagerFactory extends NivreFactory {
	public NivreArcEagerFactory(Algorithm algorithm) {
		super(algorithm);
	}
	
	public TransitionSystem makeTransitionSystem() throws MaltChainedException {
		if (manager.getConfigLogger().isInfoEnabled()) {
			manager.getConfigLogger().info("  Transition system    : Arc-Eager\n");
		}
		return new ArcEager();
	}
	
	public OracleGuide makeOracleGuide(GuideUserHistory history) throws MaltChainedException {
		if (manager.getConfigLogger().isInfoEnabled()) {
			manager.getConfigLogger().info("  Oracle               : Arc-Eager\n");
		}
		return new ArcEagerOracle(manager, history);
	}
}
