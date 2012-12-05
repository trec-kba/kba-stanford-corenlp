package edu.stanford.nlp.parser.ensemble.maltparser.parser;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.LabelSet;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.OracleGuide;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.GuideUserHistory;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideUserAction;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.container.ActionContainer;
/**
 * @author Johan Hall
 *
 */
public abstract class Oracle implements OracleGuide {
	private DependencyParserConfig manager;
	private GuideUserHistory history;
	private String name;
	protected ActionContainer[] actionContainers;
	protected ActionContainer transActionContainer;
	protected ActionContainer[] arcLabelActionContainers;
	
	public Oracle(DependencyParserConfig manager, GuideUserHistory history) throws MaltChainedException {
		this.manager = manager;
		this.history = history;
		initActionContainers();
	}
	
	public void setManager(DependencyParserConfig manager) {
		this.manager = manager;
	}

	public GuideUserHistory getHistory() {
		return history;
	}

	public void setHistory(GuideUserHistory history) {
		this.history = history;
	}

	public DependencyParserConfig getConfiguration() {
		return manager;
	}
	
	public String getGuideName() {
		return name;
	}
	
	public void setGuideName(String guideName) {
		this.name = guideName;
	}
	
	protected GuideUserAction updateActionContainers(int transition, LabelSet arcLabels) throws MaltChainedException {	
		transActionContainer.setAction(transition);

		if (arcLabels == null) {
			for (int i = 0; i < arcLabelActionContainers.length; i++) {
				arcLabelActionContainers[i].setAction(-1);	
			}
		} else {
			for (int i = 0; i < arcLabelActionContainers.length; i++) {
				arcLabelActionContainers[i].setAction(arcLabels.get(arcLabelActionContainers[i].getTable()).shortValue());
			}		
		}
		GuideUserAction oracleAction = history.getEmptyGuideUserAction();
		oracleAction.addAction(actionContainers);
		return oracleAction;
	}
	
	public void initActionContainers() throws MaltChainedException {
		this.actionContainers = history.getActionContainerArray();
		if (actionContainers.length < 1) {
			throw new ParsingException("Problem when initialize the history (sequence of actions). There are no action containers. ");
		}
		int nLabels = 0;
		for (int i = 0; i < actionContainers.length; i++) {
			if (actionContainers[i].getTableContainerName().startsWith("A.")) {
				nLabels++;
			}
		}
		int j = 0;
		for (int i = 0; i < actionContainers.length; i++) {
			if (actionContainers[i].getTableContainerName().equals("T.TRANS")) {
				transActionContainer = actionContainers[i];
			} else if (actionContainers[i].getTableContainerName().startsWith("A.")) {
				if (arcLabelActionContainers == null) {
					arcLabelActionContainers = new ActionContainer[nLabels];
				}
				arcLabelActionContainers[j++] = actionContainers[i];
			}
		}
	}
}
