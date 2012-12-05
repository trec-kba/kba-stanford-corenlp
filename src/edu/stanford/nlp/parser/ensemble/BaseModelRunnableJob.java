package edu.stanford.nlp.parser.ensemble;

import java.io.File;

import edu.stanford.nlp.parser.ensemble.maltparser.core.helper.SystemLogger;

public class BaseModelRunnableJob {
	protected Ensemble ensemble;
	
	protected int baseModelIndex;
	
	protected String baseModel;
	
	protected File workingDirectory;
	
	protected boolean leftToRight;
	
	protected BaseModelRunnableJob(Ensemble ensemble, int index) {
		this.ensemble = ensemble;
		this.baseModelIndex = index;
		this.baseModel = ensemble.baseModels[baseModelIndex];
		this.leftToRight = baseModel.toLowerCase().endsWith("ltr");
	}
	
	void createWorkingDirectory() {
		String dn = ensemble.workingDirectory + File.separator + baseModel;
		workingDirectory = new File(dn);
		if(workingDirectory.exists()) throw new RuntimeException("ERROR: Working directory already exists!");
		if(! workingDirectory.mkdir()) throw new RuntimeException("ERROR: Cannot create working directory!");
		workingDirectory.deleteOnExit();
		SystemLogger.logger().debug("Working directory for job #" + baseModelIndex + " set to " + workingDirectory.getAbsolutePath() + "\n");
	}
}
