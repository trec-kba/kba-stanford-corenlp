package edu.stanford.nlp.parser.ensemble.maltparser.core.flow.system.elem;

import org.w3c.dom.Element;

import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.flow.system.FlowChartSystem;
/**
*
*
* @author Johan Hall
*/
public class ChartAttribute {
	private String name;
	private String defaultValue;
	
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	public void read(Element attrElem, FlowChartSystem flowChartSystem) throws MaltChainedException {
		setName(attrElem.getAttribute("name"));
		setDefaultValue(attrElem.getAttribute("default"));
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(name);
		sb.append(' ');
		sb.append(defaultValue);
		return sb.toString();
	}
}
