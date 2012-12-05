package edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph;

import java.util.HashSet;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.flow.FlowChartInstance;
import edu.stanford.nlp.parser.ensemble.maltparser.core.flow.FlowException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.flow.item.ChartItem;
import edu.stanford.nlp.parser.ensemble.maltparser.core.flow.spec.ChartItemSpecification;
import edu.stanford.nlp.parser.ensemble.maltparser.core.io.dataformat.DataFormatInstance;
import edu.stanford.nlp.parser.ensemble.maltparser.core.io.dataformat.DataFormatSpecification.DataStructure;
import edu.stanford.nlp.parser.ensemble.maltparser.core.io.dataformat.DataFormatSpecification.Dependency;
import edu.stanford.nlp.parser.ensemble.maltparser.core.options.OptionManager;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.ds2ps.LosslessMapping;
/**
*
*
* @author Johan Hall
*/
public class SyntaxGraphChartItem extends ChartItem {
	private String structureName;
	private String taskName;
	private TokenStructure graph;
	
	public SyntaxGraphChartItem() { super(); }
	
	public void initialize(FlowChartInstance flowChartinstance, ChartItemSpecification chartItemSpecification) throws MaltChainedException {
		super.initialize(flowChartinstance, chartItemSpecification);
		
		for (String key : chartItemSpecification.getChartItemAttributes().keySet()) {
			if (key.equals("structure")) {
				structureName = chartItemSpecification.getChartItemAttributes().get(key);
			} else if (key.equals("task")) {
				taskName = chartItemSpecification.getChartItemAttributes().get(key);
			}
		}
		if (structureName == null) {
			structureName = getChartElement("graph").getAttributes().get("structure").getDefaultValue();
		} else if (taskName == null) {
			taskName = getChartElement("graph").getAttributes().get("task").getDefaultValue();
		}
	}
	
	public int preprocess(int signal) throws MaltChainedException {
		if (taskName.equals("create")) {
			boolean phrase = false;
			boolean dependency = false;
			DataFormatInstance dataFormatInstance = null;
			for (String key : flowChartinstance.getDataFormatInstances().keySet()) {
				if (flowChartinstance.getDataFormatInstances().get(key).getDataFormarSpec().getDataStructure() == DataStructure.PHRASE) {
					phrase = true;
				}
				if (flowChartinstance.getDataFormatInstances().get(key).getDataFormarSpec().getDataStructure() == DataStructure.DEPENDENCY) {
					dependency = true;
					dataFormatInstance = flowChartinstance.getDataFormatInstances().get(key);
				}
			}
			
			if (dependency == false && OptionManager.instance().getOptionValue(getOptionContainerIndex(), "config", "flowchart").toString().equals("learn")) {
				dependency = true;
				HashSet<Dependency> deps = flowChartinstance.getDataFormatManager().getInputDataFormatSpec().getDependencies();
				String nullValueStategy = OptionManager.instance().getOptionValue(getOptionContainerIndex(), "singlemalt", "null_value").toString();
				String rootLabels = OptionManager.instance().getOptionValue(getOptionContainerIndex(), "graph", "root_label").toString();
				for (Dependency dep : deps) {
					dataFormatInstance = flowChartinstance.getDataFormatManager().getDataFormatSpec(dep.getDependentOn()).createDataFormatInstance(flowChartinstance.getSymbolTables(), nullValueStategy, rootLabels);
					flowChartinstance.getDataFormatInstances().put(flowChartinstance.getDataFormatManager().getOutputDataFormatSpec().getDataFormatName(), dataFormatInstance);
				}
			}

			if (dependency == true && phrase == false) {
				graph = new DependencyGraph(flowChartinstance.getSymbolTables());
				flowChartinstance.addFlowChartRegistry(edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.DependencyStructure.class, structureName, graph);
			} else if (dependency == true && phrase == true) {
				graph = new MappablePhraseStructureGraph(flowChartinstance.getSymbolTables());
				final DataFormatInstance inFormat = flowChartinstance.getDataFormatInstances().get(flowChartinstance.getDataFormatManager().getInputDataFormatSpec().getDataFormatName());
				final DataFormatInstance outFormat = flowChartinstance.getDataFormatInstances().get(flowChartinstance.getDataFormatManager().getOutputDataFormatSpec().getDataFormatName());

				if (inFormat != null && outFormat != null) {
					LosslessMapping mapping = null;
					if (inFormat.getDataFormarSpec().getDataStructure() == DataStructure.DEPENDENCY) {
						mapping = new LosslessMapping(inFormat, outFormat);
					} else {
						mapping = new LosslessMapping(outFormat, inFormat);
					}
					if (inFormat.getDataFormarSpec().getDataStructure() == DataStructure.PHRASE) {
						mapping.setHeadRules(OptionManager.instance().getOptionValue(getOptionContainerIndex(), "graph", "head_rules").toString());
					}
					((MappablePhraseStructureGraph)graph).setMapping(mapping);
				} else {
					throw new FlowException("Couldn't determine the input and output data format. ");
				}
				flowChartinstance.addFlowChartRegistry(edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.DependencyStructure.class, structureName, graph);
				flowChartinstance.addFlowChartRegistry(edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.PhraseStructure.class, structureName, graph);
			} else if (dependency == false && phrase == true) {
				graph = new PhraseStructureGraph(flowChartinstance.getSymbolTables());
				flowChartinstance.addFlowChartRegistry(edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.PhraseStructure.class, structureName, graph);
			} else {
				graph = new Sentence(flowChartinstance.getSymbolTables());
			}
			
			if (dataFormatInstance != null) {
				((DependencyStructure)graph).setDefaultRootEdgeLabels(
						OptionManager.instance().getOptionValue(getOptionContainerIndex(), "graph", "root_label").toString(), 
						dataFormatInstance.getDependencyEdgeLabelSymbolTables());
			}
			flowChartinstance.addFlowChartRegistry(edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.TokenStructure.class, structureName, graph);
		}
		return signal;
	}
	
	public int process(int signal) throws MaltChainedException {
		return signal;
	}
	
	public int postprocess(int signal) throws MaltChainedException {
		return signal;
	}
	
	public void terminate() throws MaltChainedException {
		if (graph != null) {
			graph.clear();
			graph = null;
		}
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
	
	public int hashCode() {
		return 217 + (null == toString() ? 0 : toString().hashCode());
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("    graph ");
		sb.append("task:");
		sb.append(taskName);
		sb.append(' ');
		sb.append("structure:");
		sb.append(structureName);
		return sb.toString();
	}
}
