package edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.node;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.symbol.SymbolTable;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.edge.Edge;


public interface PhraseStructureNode extends ComparableNode {
	public PhraseStructureNode getParent();
	public Edge getParentEdge() throws MaltChainedException;
	public String getParentEdgeLabelSymbol(SymbolTable table) throws MaltChainedException;
	public int getParentEdgeLabelCode(SymbolTable table) throws MaltChainedException;
	public boolean hasParentEdgeLabel(SymbolTable table) throws MaltChainedException;
}
