package edu.stanford.nlp.parser.ensemble.maltparser.parser.algorithm.stack;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.Algorithm;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.TransitionSystem;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.OracleGuide;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.GuideUserHistory;
/**
 * @author Johan Hall
 *
 */
public class StackSwapLazyFactory extends StackFactory {
	public StackSwapLazyFactory(Algorithm algorithm) {
		super(algorithm);
	}
	
	public TransitionSystem makeTransitionSystem() throws MaltChainedException {
		if (manager.getConfigLogger().isInfoEnabled()) {
			manager.getConfigLogger().info("  Transition system    : Non-Projective\n");
		}
		return new NonProjective();
	}
	
	public OracleGuide makeOracleGuide(GuideUserHistory history) throws MaltChainedException {
		if (manager.getConfigLogger().isInfoEnabled()) {
			manager.getConfigLogger().info("  Oracle               : Swap-Lazy\n");
		}
		return new SwapLazyOracle(manager, history);
	}
}
