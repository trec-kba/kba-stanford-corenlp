package edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.feature;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.FeatureException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.function.AddressFunction;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.function.FeatureFunction;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.value.AddressValue;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.value.FeatureValue;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.value.SingleFeatureValue;
import edu.stanford.nlp.parser.ensemble.maltparser.core.io.dataformat.ColumnDescription;
import edu.stanford.nlp.parser.ensemble.maltparser.core.io.dataformat.DataFormatInstance;
import edu.stanford.nlp.parser.ensemble.maltparser.core.symbol.SymbolTable;
import edu.stanford.nlp.parser.ensemble.maltparser.core.symbol.SymbolTableHandler;
import edu.stanford.nlp.parser.ensemble.maltparser.core.symbol.nullvalue.NullValues.NullValueId;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.node.DependencyNode;
/**
*
* @author Johan Hall
* @since 1.1
**/
public class InputArcDirFeature implements FeatureFunction {
	protected ColumnDescription column;
	protected DataFormatInstance dataFormatInstance;
	protected SymbolTableHandler tableHandler;
	protected SymbolTable table;
	protected SingleFeatureValue featureValue;
	protected AddressFunction addressFunction;
	
	public InputArcDirFeature(DataFormatInstance dataFormatInstance, SymbolTableHandler tableHandler) throws MaltChainedException {
		super();
		setDataFormatInstance(dataFormatInstance);
		setTableHandler(tableHandler);
		setFeatureValue(new SingleFeatureValue(this));
	}
	
	public void initialize(Object[] arguments) throws MaltChainedException {
		if (arguments.length != 2) {
			throw new FeatureException("Could not initialize InputArcDirFeature: number of arguments are not correct. ");
		}
		if (!(arguments[0] instanceof String)) {
			throw new FeatureException("Could not initialize InputArcDirFeature: the first argument is not a string. ");
		}
		if (!(arguments[1] instanceof AddressFunction)) {
			throw new FeatureException("Could not initialize InputArcDirFeature: the second argument is not an address function. ");
		}
		setColumn(dataFormatInstance.getColumnDescriptionByName((String)arguments[0]));
		setSymbolTable(tableHandler.addSymbolTable("ARCDIR_"+column.getName(),ColumnDescription.INPUT, "one"));
		table.addSymbol("LEFT");
		table.addSymbol("RIGHT");
		table.addSymbol("ROOT");
		setAddressFunction((AddressFunction)arguments[1]);
	}
	
	public Class<?>[] getParameterTypes() {
		Class<?>[] paramTypes = { java.lang.String.class, edu.stanford.nlp.parser.ensemble.maltparser.core.feature.function.AddressFunction.class };
		return paramTypes;
	}
	
	public int getCode(String symbol) throws MaltChainedException {
		return table.getSymbolStringToCode(symbol);
	}
	
	public String getSymbol(int code) throws MaltChainedException {
		return table.getSymbolCodeToString(code);
	}
	
	public FeatureValue getFeatureValue() {
		return featureValue;
	}

	public void updateCardinality() throws MaltChainedException {
		featureValue.setCardinality(table.getValueCounter());
	}

	public void update() throws MaltChainedException {
		AddressValue a = addressFunction.getAddressValue();
		if (a.getAddress() != null && a.getAddressClass() == edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.node.DependencyNode.class) {
			DependencyNode node = (DependencyNode)a.getAddress();
			try {
				int index = Integer.parseInt(node.getLabelSymbol(column.getSymbolTable()));
				if (node.isRoot()) {
					featureValue.setCode(table.getNullValueCode(NullValueId.ROOT_NODE));
					featureValue.setSymbol(table.getNullValueSymbol(NullValueId.ROOT_NODE));
					featureValue.setKnown(true);
					featureValue.setNullValue(true);
				} else if (index == 0) {
					featureValue.setCode(table.getSymbolStringToCode("ROOT"));
					featureValue.setSymbol("ROOT");
					featureValue.setKnown(true);
					featureValue.setNullValue(false);
				} else if (index < node.getIndex()) {
					featureValue.setCode(table.getSymbolStringToCode("LEFT"));
					featureValue.setSymbol("LEFT");
					featureValue.setKnown(true);
					featureValue.setNullValue(false);
				} else if (index > node.getIndex()) {
					featureValue.setCode(table.getSymbolStringToCode("RIGHT"));
					featureValue.setSymbol("RIGHT");
					featureValue.setKnown(true);
					featureValue.setNullValue(false);
				}
			} catch (NumberFormatException e) {
				throw new FeatureException("The index of the feature must be an integer value. ", e);
			}
		} else {
			featureValue.setCode(table.getNullValueCode(NullValueId.NO_NODE));
			featureValue.setSymbol(table.getNullValueSymbol(NullValueId.NO_NODE));
			featureValue.setKnown(true);
			featureValue.setNullValue(true);
		}
	}

	public AddressFunction getAddressFunction() {
		return addressFunction;
	}

	public void setAddressFunction(AddressFunction addressFunction) {
		this.addressFunction = addressFunction;
	}

	public ColumnDescription getColumn() {
		return column;
	}

	public void setColumn(ColumnDescription column) throws MaltChainedException {
		if (column.getType() != ColumnDescription.INTEGER) {
			throw new FeatureException("InputArc feature column must be of type integer. ");
		}
		this.column = column;
	}

	public DataFormatInstance getDataFormatInstance() {
		return dataFormatInstance;
	}

	public void setDataFormatInstance(DataFormatInstance dataFormatInstance) {
		this.dataFormatInstance = dataFormatInstance;
	}

	public void setFeatureValue(SingleFeatureValue featureValue) {
		this.featureValue = featureValue;
	}
	
	public SymbolTable getSymbolTable() {
		return table;
	}

	public void setSymbolTable(SymbolTable table) {
		this.table = table;
	}
	
	public SymbolTableHandler getTableHandler() {
		return tableHandler;
	}

	public void setTableHandler(SymbolTableHandler tableHandler) {
		this.tableHandler = tableHandler;
	}
	
	public boolean equals(Object obj) {
		if (!(obj instanceof InputArcDirFeature)) {
			return false;
		}
		if (!obj.toString().equals(this.toString())) {
			return false;
		}
		return true;
	}
	
	public String toString() {
		return "InputArcDir(" + column.getName() + ", " + addressFunction.toString() + ")";
	}
}
