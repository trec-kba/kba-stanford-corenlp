package edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.feature;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.function.FeatureFunction;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.function.Modifiable;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.value.FeatureValue;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.value.SingleFeatureValue;
import edu.stanford.nlp.parser.ensemble.maltparser.core.io.dataformat.ColumnDescription;
import edu.stanford.nlp.parser.ensemble.maltparser.core.symbol.SymbolTable;
import edu.stanford.nlp.parser.ensemble.maltparser.core.symbol.nullvalue.NullValues.NullValueId;

/**
*
*
* @author Johan Hall
*/
public abstract class ColumnFeature implements FeatureFunction, Modifiable {
	protected ColumnDescription column;
	protected SingleFeatureValue featureValue;
	
	public ColumnFeature() throws MaltChainedException {
		featureValue = new SingleFeatureValue(this);
	}
	
	public abstract void update() throws MaltChainedException;
	public abstract void initialize(Object[] arguments) throws MaltChainedException;
	public abstract Class<?>[] getParameterTypes();
	
	public String getSymbol(int value) throws MaltChainedException {
		return column.getSymbolTable().getSymbolCodeToString(value);
	}
	
	public int getCode(String value) throws MaltChainedException {
		return column.getSymbolTable().getSymbolStringToCode(value);
	}
	
	public ColumnDescription getColumn() {
		return column;
	}
	
	protected void setColumn(ColumnDescription column) {
		this.column = column;
	}
	
	public void updateCardinality() {
		featureValue.setCardinality(column.getSymbolTable().getValueCounter()); 
	}
	
	public void setFeatureValue(int value) throws MaltChainedException {
		if (column.getSymbolTable().getSymbolCodeToString(value) == null) {
			featureValue.setCode(value);
			featureValue.setKnown(column.getSymbolTable().getKnown(value));
			featureValue.setSymbol(column.getSymbolTable().getNullValueSymbol(NullValueId.NO_NODE));
			featureValue.setNullValue(true);
		} else {
			featureValue.setCode(value);
			featureValue.setKnown(column.getSymbolTable().getKnown(value));
			featureValue.setSymbol(column.getSymbolTable().getSymbolCodeToString(value));
			featureValue.setNullValue(column.getSymbolTable().isNullValue(value));
		}
	}
	
	public void setFeatureValue(String value) throws MaltChainedException {
		if (column.getSymbolTable().getSymbolStringToCode(value) < 0) {
			featureValue.setCode(column.getSymbolTable().getNullValueCode(NullValueId.NO_NODE));
			featureValue.setKnown(column.getSymbolTable().getKnown(value));
			featureValue.setSymbol(value);
			featureValue.setNullValue(true);
		} else {
			featureValue.setCode(column.getSymbolTable().getSymbolStringToCode(value));
			featureValue.setKnown(column.getSymbolTable().getKnown(value));
			featureValue.setSymbol(value);
			featureValue.setNullValue(column.getSymbolTable().isNullValue(value));
		}
	}
	
	public FeatureValue getFeatureValue() {
		return featureValue;
	}
	
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		return obj.toString().equals(this.toString());
	}

	public String getColumnName() {
		return column.getName();
	}
	
	public SymbolTable getSymbolTable() {
		return column.getSymbolTable();
	}
	
	public String toString() {
		return column.getName();
	}
}
