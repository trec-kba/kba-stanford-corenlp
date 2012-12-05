package edu.stanford.nlp.parser.ensemble.maltparser.parser.algorithm.nivre;


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
public class ArcStandardOracle extends Oracle {
	
	public ArcStandardOracle(DependencyParserConfig manager, GuideUserHistory history) throws MaltChainedException {
		super(manager, history);
		setGuideName("ArcStandard");
	}
	
	public GuideUserAction predict(DependencyStructure gold, ParserConfiguration config) throws MaltChainedException {
		NivreConfig nivreConfig = (NivreConfig)config;
		DependencyNode stackPeek = nivreConfig.getStack().peek();
		int stackPeekIndex = stackPeek.getIndex();
		int inputPeekIndex = nivreConfig.getInput().peek().getIndex();
		
		if (nivreConfig.getRootHandling() != NivreConfig.NORMAL && stackPeek.isRoot()) {
			return updateActionContainers(ArcStandard.SHIFT, null);
		}
		if (!stackPeek.isRoot() && gold.getTokenNode(stackPeekIndex).getHead().getIndex() == inputPeekIndex) {
			return updateActionContainers(ArcStandard.LEFTARC, gold.getTokenNode(stackPeekIndex).getHeadEdge().getLabelSet());
		} else if (gold.getTokenNode(inputPeekIndex).getHead().getIndex() == stackPeekIndex && checkRightDependent(gold, nivreConfig.getDependencyGraph(), inputPeekIndex)) {
			return updateActionContainers(ArcStandard.RIGHTARC, gold.getTokenNode(inputPeekIndex).getHeadEdge().getLabelSet());
		} else {
			return updateActionContainers(ArcStandard.SHIFT, null);
		}
	}
	
	private boolean checkRightDependent(DependencyStructure gold, DependencyStructure parseDependencyGraph, int inputPeekIndex) throws MaltChainedException {
		if (gold.getTokenNode(inputPeekIndex).getRightmostDependent() == null) {
			return true;
		} else if (parseDependencyGraph.getTokenNode(inputPeekIndex).getRightmostDependent() != null) {
			if (gold.getTokenNode(inputPeekIndex).getRightmostDependent().getIndex() == parseDependencyGraph.getTokenNode(inputPeekIndex).getRightmostDependent().getIndex()) {
				return true;
			}
		}
		return false;
	}
	
	public void finalizeSentence(DependencyStructure dependencyGraph) throws MaltChainedException {
		
	}
	
	public void terminate() throws MaltChainedException {
		
	}
}
