package edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.node;

import java.util.Iterator;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.Element;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.edge.Edge;

public interface Node extends ComparableNode, Element {
	public void addIncomingEdge(Edge in) throws MaltChainedException;
	public void addOutgoingEdge(Edge out) throws MaltChainedException;
	public void removeIncomingEdge(Edge in) throws MaltChainedException;
	public void removeOutgoingEdge(Edge out) throws MaltChainedException;
	public Iterator<Edge> getIncomingEdgeIterator();
	public Iterator<Edge> getOutgoingEdgeIterator();
	public void setIndex(int index) throws MaltChainedException;
}
