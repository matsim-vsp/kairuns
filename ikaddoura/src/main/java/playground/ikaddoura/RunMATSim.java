/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.ikaddoura;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.otfvis.OTFVisLiveModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

/**
* @author ikaddoura
*/

public class RunMATSim {

	private static final Logger log = Logger.getLogger(RunMATSim.class);

	private static String configFile;
	private static String outputDirectory;
	private static String runId;
		
	private static boolean otfvis;
	
	private final String crs = TransformationFactory.DHDN_GK4;
	
	public static void main(String[] args) {
		if (args.length > 0) {
			
			configFile = args[0];		
			log.info("configFile: "+ configFile);
			
			outputDirectory = args[1];
			log.info("outputDirectory: "+ outputDirectory);
			
			runId = args[2];
			log.info("runId: "+ runId);
			
			otfvis = false;
			
		} else {
			
			configFile = "../../runs-svn/incidents-longterm-shortterm/input/config-longterm.xml";
			outputDirectory = "../../runs-svn/incidents-longterm-shortterm/output/test9/";
			runId = "test-run";
			
			otfvis = false;
		}
		
		RunMATSim runner = new RunMATSim();
		runner.run(configFile, outputDirectory, runId);
		
		
	}

	public void run(String configFile, String outputDirectory, String runId) {
		
		Config config = ConfigUtils.loadConfig(configFile);
		
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.failIfDirectoryExists);
		config.controler().setOutputDirectory(outputDirectory);
		config.controler().setRunId(runId);
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Controler controler = new Controler(scenario);
		
		if (otfvis) controler.addOverridingModule(new OTFVisLiveModule());
		
		controler.run();
		
	}

}

