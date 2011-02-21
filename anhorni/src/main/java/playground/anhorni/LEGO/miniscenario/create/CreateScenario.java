/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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

package playground.anhorni.LEGO.miniscenario.create;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.ScenarioImpl;
import org.matsim.core.config.Config;
import org.matsim.core.scenario.ScenarioLoaderImpl;

import playground.anhorni.LEGO.miniscenario.ConfigReader;
import playground.anhorni.random.RandomFromVarDistr;

public class CreateScenario {

	private final static Logger log = Logger.getLogger(CreateScenario.class);
	private ScenarioImpl scenario = new ScenarioImpl();	
	private ConfigReader configReader = new ConfigReader();
	private RandomFromVarDistr rnd;
	
	private long seed;
	private String outPath = "src/main/java/playground/anhorni/input/LEGO/";
		
	public static void main(final String[] args) {
		CreateScenario scenarioCreator = new CreateScenario();	
		scenarioCreator.init();	
		scenarioCreator.setSeedFromConfig();
		scenarioCreator.run();			
		log.info("Scenario creation finished \n ----------------------------------------------------");
	}
	
	public void init() {
		configReader.read();			
		rnd = new RandomFromVarDistr();
	}
	
	public void run() {
		this.create();
	}
	
	private void create() {			
		CreateNetwork networkCreator = new CreateNetwork();
		networkCreator.createNetwork(this.scenario, this.configReader);
		
		ScenarioImpl scenario = new ScenarioLoaderImpl(configReader.getPath() + "config.xml").getScenario();
		Config config = scenario.getConfig();
				
		CreatePopulation populationCreator = new CreatePopulation();
		populationCreator.createPopulation(this.scenario, this.configReader, rnd, config);	
		log.info("Writing population ...");
		populationCreator.write(this.outPath);
		log.info("Writing network ...");
		networkCreator.write(this.outPath);
	}

	public void setSeed(long seed) {
		this.seed = seed;
		rnd.setSeed(this.seed);
	}
	
	public void setSeedFromConfig() {
		this.seed = configReader.getRandomSeed();
		rnd.setSeed(this.seed);
	}

	public void setOutPath(String outPath) {
		this.outPath = outPath;
	}
}
