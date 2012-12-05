package edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph;

import java.util.Observer;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.pool.ObjectPoolList;
import edu.stanford.nlp.parser.ensemble.maltparser.core.symbol.SymbolTableHandler;
/**
*
*
* @author Johan Hall
*/
public abstract class SyntaxGraph implements LabeledStructure, Structure, Observer {
	protected SymbolTableHandler symbolTables;
	protected ObjectPoolList<LabelSet> labelSetPool;
	protected int numberOfComponents;
	
	public SyntaxGraph(SymbolTableHandler symbolTables) throws MaltChainedException  {
		setSymbolTables(symbolTables);
		labelSetPool = new ObjectPoolList<LabelSet>() {
			protected LabelSet create() { return new LabelSet(6); }
			public void resetObject(LabelSet o) throws MaltChainedException { o.clear(); }
		};	
	}
	
	public SymbolTableHandler getSymbolTables() {
		return symbolTables;
	}

	public void setSymbolTables(SymbolTableHandler symbolTables) {
		this.symbolTables = symbolTables;
	}
	
	public void addLabel(Element element, String labelFunction, String label) throws MaltChainedException {
		element.addLabel(symbolTables.addSymbolTable(labelFunction), label);
	}
	
	public LabelSet checkOutNewLabelSet() throws MaltChainedException {
		return labelSetPool.checkOut();
	}
	
	public void checkInLabelSet(LabelSet labelSet) throws MaltChainedException {
		labelSetPool.checkIn(labelSet);
	}
	
	public void clear() throws MaltChainedException {
		numberOfComponents = 0;
		labelSetPool.checkInAll();
	}
}
