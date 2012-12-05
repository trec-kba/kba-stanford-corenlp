package edu.stanford.nlp.parser.ensemble;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.parser.ensemble.maltparser.Constants;
import edu.stanford.nlp.parser.ensemble.maltparser.MaltConsoleEngine;
import edu.stanford.nlp.parser.ensemble.maltparser.core.helper.SystemLogger;
import edu.stanford.nlp.parser.ensemble.utils.Now;

public class RunnableTrainJob extends BaseModelRunnableJob implements Runnable {
	public RunnableTrainJob(Ensemble ensemble, int index) {
		super(ensemble, index);
	}

	@Override
	public void run() {
		SystemLogger.logger().info("Starting job " + ensemble.baseModels[baseModelIndex] + " at " + new Now() + "...\n");
		createWorkingDirectory();
		
		// args for malt
		String [] params = makeMaltEngineParameters();
		
		// run malt
		MaltConsoleEngine engine = new MaltConsoleEngine();
		engine.startEngine(params);
		
		// move model file from working directory to model directory
		File origModel = new File(workingDirectory + File.separator + ensemble.modelName + "-" + baseModel + ".mco");
		File savedModel = new File(ensemble.modelDirectory + File.separator + ensemble.modelName + "-" + baseModel + ".mco");
		if(origModel.renameTo(savedModel)){
			SystemLogger.logger().info("Model file for job " + baseModel + " saved as: " + savedModel.getAbsolutePath() + "\n");
		} else {
			SystemLogger.logger().error("ERROR: failed to save model file for job " + baseModel + ". The actual model file might be here: " + origModel.getAbsolutePath() + "\n");
		}
				
		ensemble.threadFinished();
		SystemLogger.logger().info("Ended job " + baseModel + " at " + new Now() + ".\n");
	}

	private String [] makeMaltEngineParameters() {
		List<String> pars = new ArrayList<String>();
		
		pars.add("-m");
		pars.add("learn");

		pars.add("-c");
		pars.add(ensemble.modelName + "-" + baseModel);
		
		pars.add("-l");
		pars.add("liblinear");
		
		pars.add("-llo");
		pars.add(ensemble.libLinearOptions);
		
		pars.add("-llv");
		pars.add(ensemble.libLinearLogLevel);
		
		pars.add("-v");
		pars.add(ensemble.logLevel);
		
		pars.add("-d");
		pars.add(ensemble.dataSplitColumn);
		
		pars.add("-T");
		pars.add(Integer.toString(ensemble.dataSplitThreshold));

		pars.add("-s");
		if(baseModel.startsWith("nivre")) pars.add("Input[0]");
		else if(baseModel.startsWith("cov")) pars.add("Right[0]");
		else throw new RuntimeException("Unknown base model: " + baseModel); 
		
		pars.add("-a");
		int dashPos = baseModel.lastIndexOf("-");
		assert(dashPos > 0 && dashPos < baseModel.length());
		pars.add(baseModel.substring(0, dashPos));
		
		pars.add("-w");
		pars.add(workingDirectory.getAbsolutePath());
		
		pars.add("-i");
		if(leftToRight) pars.add(ensemble.trainCorpus);
		else{
			File origFile = new File(ensemble.trainCorpus);
			File reversedFile = new File(ensemble.workingDirectory + File.separator + origFile.getName() + ".reversed");
			pars.add(reversedFile.getAbsolutePath());
		}
		
		if(ensemble.libLinearTrain != null && ensemble.libLinearTrain.length() > 0){
			pars.add("-llx");
			pars.add(ensemble.libLinearTrain);
		}
		
		if(Const.TRAIN_EXTENDED) {
			pars.add("-F");
			if(baseModel.equalsIgnoreCase("nivreeager-ltr")) pars.add(Constants.APPDATA_PATH + "/features/ExtendedNivreEagerFHI2.xml");
			else if(baseModel.equalsIgnoreCase("nivreeager-rtl")) pars.add(Constants.APPDATA_PATH + "/features/ExtendedNivreEagerFHI3.xml");
			else if(baseModel.equalsIgnoreCase("nivrestandard-ltr")) pars.add(Constants.APPDATA_PATH + "/features/ExtendedNivreStandardFHISPSI.xml");
			else if(baseModel.equalsIgnoreCase("nivrestandard-rtl")) pars.add(Constants.APPDATA_PATH + "/features/ExtendedNivreStandardFIPSI.xml");
			else if(baseModel.equalsIgnoreCase("covnonproj-ltr")) pars.add(Constants.APPDATA_PATH + "/features/ExtendedCovingtonNonProjectiveFHLRPLR.xml");
			else if(baseModel.equalsIgnoreCase("covnonproj-rtl")) pars.add(Constants.APPDATA_PATH + "/features/ExtendedCovingtonNonProjectiveFHLRPLR.xml");
			else throw new RuntimeException("Unknown base model: " + baseModel);
		}
			
		String [] parameters = new String[pars.size()];
		pars.toArray(parameters);
		return parameters;
	}
}
