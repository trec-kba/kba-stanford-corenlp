package edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.node;

import java.util.SortedSet;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.headrules.HeadRules;

public interface NonTerminalNode extends PhraseStructureNode {
	public TokenNode identifyHead(HeadRules headRules) throws MaltChainedException;
	public TokenNode getLexicalHead(HeadRules headRules) throws MaltChainedException;
	public TokenNode getLexicalHead() throws MaltChainedException;
	public PhraseStructureNode getHeadChild(HeadRules headRules) throws MaltChainedException;
	public PhraseStructureNode getHeadChild() throws MaltChainedException;
	public SortedSet<PhraseStructureNode> getChildren();
	public PhraseStructureNode getChild(int index);
	public PhraseStructureNode getLeftChild();
	public PhraseStructureNode getRightChild();
	public int nChildren();
	public boolean hasNonTerminalChildren();
	public boolean hasTerminalChildren();
	public int getHeight();
	public boolean isContinuous();
	public boolean isContinuousExcludeTerminalsAttachToRoot();
	//public void reArrangeChildrenAccordingToLeftAndRightProperDesendant() throws MaltChainedException;
}
