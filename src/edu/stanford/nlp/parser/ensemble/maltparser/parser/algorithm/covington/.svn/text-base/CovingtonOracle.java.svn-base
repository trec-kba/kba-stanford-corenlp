package edu.stanford.nlp.parser.ensemble.maltparser.parser.algorithm.covington;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.DependencyStructure;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.node.DependencyNode;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.DependencyParserConfig;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.Oracle;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.ParserConfiguration;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.GuideUserHistory;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideUserAction;
/**
 * @author Johan Hall
 *
 */
public class CovingtonOracle extends Oracle {
	public CovingtonOracle(DependencyParserConfig manager, GuideUserHistory history) throws MaltChainedException {
		super(manager, history);
		setGuideName("NonProjective");
	}
	
	public GuideUserAction predict(DependencyStructure gold, ParserConfiguration config) throws MaltChainedException {
		CovingtonConfig covingtonConfig = (CovingtonConfig)config;
		DependencyNode leftTarget = covingtonConfig.getLeftTarget();
		int leftTargetIndex = leftTarget.getIndex();
		int rightTargetIndex = covingtonConfig.getRightTarget().getIndex();
		
		if (!leftTarget.isRoot() && gold.getTokenNode(leftTargetIndex).getHead().getIndex() == rightTargetIndex) {
			return updateActionContainers(NonProjective.LEFTARC, gold.getTokenNode(leftTargetIndex).getHeadEdge().getLabelSet());
		} else if (gold.getTokenNode(rightTargetIndex).getHead().getIndex() == leftTargetIndex) {
			return updateActionContainers(NonProjective.RIGHTARC, gold.getTokenNode(rightTargetIndex).getHeadEdge().getLabelSet());
		} else if (covingtonConfig.isAllowShift() == true && (!(gold.getTokenNode(rightTargetIndex).hasLeftDependent() 
				&& gold.getTokenNode(rightTargetIndex).getLeftmostDependent().getIndex() < leftTargetIndex)
				&& !(gold.getTokenNode(rightTargetIndex).getHead().getIndex() < leftTargetIndex 
						&& (!gold.getTokenNode(rightTargetIndex).getHead().isRoot() || covingtonConfig.getLeftstop() == 0)))) {
			return updateActionContainers(NonProjective.SHIFT, null);
		} else {
			return updateActionContainers(NonProjective.NOARC, null);
		}
	}
	
	public void finalizeSentence(DependencyStructure dependencyGraph) throws MaltChainedException {
		
	}
	
	public void terminate() throws MaltChainedException {
		
	}
}
