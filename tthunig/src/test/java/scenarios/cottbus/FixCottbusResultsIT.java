/*
 *  *********************************************************************** *
 *  * project: org.matsim.*
 *  * DefaultControlerModules.java
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  * copyright       : (C) 2014 by the members listed in the COPYING, *
 *  *                   LICENSE and WARRANTY file.                            *
 *  * email           : info at matsim dot org                                *
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  *   This program is free software; you can redistribute it and/or modify  *
 *  *   it under the terms of the GNU General Public License as published by  *
 *  *   the Free Software Foundation; either version 2 of the License, or     *
 *  *   (at your option) any later version.                                   *
 *  *   See also COPYING, LICENSE and WARRANTY file                           *
 *  *                                                                         *
 *  * ***********************************************************************
 */
package scenarios.cottbus;

import analysis.TtTotalTravelTime;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.config.groups.TravelTimeCalculatorConfigGroup.TravelTimeCalculatorType;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultSelector;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.MatsimTestUtils;
import scenarios.cottbus.TtRunCottbusSimulation.NetworkType;
import scenarios.cottbus.TtRunCottbusSimulation.PopulationType;
import scenarios.cottbus.TtRunCottbusSimulation.SignalType;

/**
 * @author tthunig
 *
 */
public class FixCottbusResultsIT {
	
	private static final Logger log = Logger.getLogger(FixCottbusResultsIT.class);

	@Rule
	public MatsimTestUtils testUtils = new MatsimTestUtils();
	
	@Test
	public void testBC(){		
		fixResults(NetworkType.V1, PopulationType.WoMines, SignalType.MS, 65234080.0);
	}

	@Test
	public void testBCContinuedFreeRouteChoice(){
		fixResults(NetworkType.BTU_NET, PopulationType.BTU_POP_MATSIM_ROUTES, SignalType.MS_BTU_OPT, 1088905.0);
	}
	
	@Test
	public void testBCContinuedFixedRouteSet(){
		fixResults(NetworkType.BTU_NET, PopulationType.BTU_POP_BTU_ROUTES, SignalType.MS_BTU_OPT, 1068388.0);
	}	
	
	private void fixResults(NetworkType netType, PopulationType popType, SignalType signalType, double expectedTotalTt) {
		Config config = defineConfig(netType, popType, signalType);
		
		Scenario scenario = ScenarioUtils.loadScenario(config);	
		// add missing scenario elements
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());
		
		Controler controler = new Controler(scenario);
		// add missing modules
		Signals.configure( controler );

		TtTotalTravelTime handler = new TtTotalTravelTime();
		controler.addOverridingModule(new AbstractModule() {			
			@Override
			public void install() {
				this.addEventHandlerBinding().toInstance(handler);
			}
		});
		
		controler.run();
		
		// check travel time
		log.info("networkType: " + netType + ", populationType" + popType + ", signalType: " + signalType + ", expectedTotalTt: " + expectedTotalTt + ", experiencedTotalTt: " + handler.getTotalTt());
		Assert.assertEquals(expectedTotalTt, handler.getTotalTt(), MatsimTestUtils.EPSILON);	
	}

	private Config defineConfig(NetworkType netType, PopulationType popType, SignalType signalType) {
		Config config = ConfigUtils.createConfig();
		
		config.controler().setOutputDirectory(testUtils.getOutputDirectory());

		if (netType.equals(NetworkType.BTU_NET)){
			config.network().setInputFile(testUtils.getClassInputDirectory() + "btuOpt/network_small_simplified.xml.gz");
			config.network().setLaneDefinitionsFile(testUtils.getClassInputDirectory() + "btuOpt/lanes_network_small.xml.gz");
			if (popType.equals(PopulationType.BTU_POP_MATSIM_ROUTES))
				config.plans().setInputFile(testUtils.getClassInputDirectory() + "btuOpt/trip_plans_from_morning_peak_ks_commodities_minFlow50.0.xml");
			else if (popType.equals(PopulationType.BTU_POP_BTU_ROUTES))
				config.plans().setInputFile(testUtils.getClassInputDirectory() + "btuOpt/2015-03-10_sameEndTimes_ksOptRouteChoice_paths.xml");
			else
				throw new UnsupportedOperationException("Combination of population " + popType + " and network " + netType + " not supported");
		} else if (netType.equals(NetworkType.V1)){
			config.network().setInputFile(testUtils.getClassInputDirectory() + "matsimData/network_wgs84_utm33n.xml.gz");
			config.network().setLaneDefinitionsFile(testUtils.getClassInputDirectory() + "matsimData/lanes.xml");
			if (popType.equals(PopulationType.WoMines)){
				// use routes from a 100th iteration as initial plans
				config.plans().setInputFile(testUtils.getClassInputDirectory() + "matsimData/commuter_population_wgs84_utm33n_car_only_100it_MS_cap0.7.xml.gz");
			}
		}
		
		// set number of iterations
		config.controler().setLastIteration(0);

		// able or enable signals and lanes
		config.qsim().setUseLanes( true );
		SignalSystemsConfigGroup signalConfigGroup = ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class);
		signalConfigGroup.setUseSignalSystems( true );
		// set signal files
		if (netType.equals(NetworkType.V1)){
			signalConfigGroup.setSignalSystemFile(testUtils.getClassInputDirectory() + "matsimData/signal_systems_no_13.xml");
		} else if (netType.equals(NetworkType.BTU_NET)){ 
			signalConfigGroup.setSignalSystemFile(testUtils.getClassInputDirectory() + "btuOpt/signal_systems_no_13_btuNet.xml");
		}
		signalConfigGroup.setSignalGroupsFile(testUtils.getClassInputDirectory() + "matsimData/signal_groups_no_13.xml");
		if (signalType.equals(SignalType.MS)){
			signalConfigGroup.setSignalControlFile(testUtils.getClassInputDirectory() + "matsimData/signal_control_no_13.xml");
		} else if (signalType.equals(SignalType.MS_BTU_OPT)){ 
			signalConfigGroup.setSignalControlFile(testUtils.getClassInputDirectory() + "btuOpt/signal_control_opt.xml");
		}

		config.qsim().setUsingFastCapacityUpdate(false);
		
		// set brain exp beta
//		config.planCalcScore().setBrainExpBeta( 2 );

		// choose between link to link and node to node routing
		// (only has effect if lanes are used)
		boolean link2linkRouting = true;
		config.controler().setLinkToLinkRoutingEnabled(link2linkRouting);
		config.travelTimeCalculator().setCalculateLinkToLinkTravelTimes(link2linkRouting);
		config.travelTimeCalculator().setCalculateLinkTravelTimes(true);

		// set travelTimeBinSize (only has effect if reRoute is used)
//		config.travelTimeCalculator().setTraveltimeBinSize( 10 );
		
		config.travelTimeCalculator().setTravelTimeCalculatorType(TravelTimeCalculatorType.TravelTimeCalculatorHashMap.toString());
		// hash map and array produce same results. only difference: memory and time.
		// for small time bins and sparse values hash map is better. theresa, may'15

		// define strategies
		{
			StrategySettings strat = new StrategySettings();
			strat.setStrategyName(DefaultSelector.KeepLastSelected.toString());
			strat.setWeight(1);
			strat.setDisableAfter(config.controler().getLastIteration());
			config.strategy().addStrategySettings(strat);
		}

		// choose maximal number of plans per agent. 0 means unlimited
		config.strategy().setMaxAgentPlanMemorySize(0); //unlimited because ReRoute is switched off anyway
		
		config.qsim().setStuckTime( 3600 );
		config.qsim().setRemoveStuckVehicles(false);
		
		if (netType.equals(NetworkType.V1)){
			config.qsim().setStorageCapFactor( 0.7 );
			config.qsim().setFlowCapFactor( 0.7 );
		} else { // BTU network
			// use default: 1.0 (BTU network is already scaled down)
		}
		
		// set start and end time to shorten simulation run time
		config.qsim().setStartTime(3600 * 5); 
		config.qsim().setEndTime(3600 * 24);

		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);

		config.vspExperimental().setWritingOutputEvents(false);
		config.planCalcScore().setWriteExperiencedPlans(false);
		config.controler().setCreateGraphs(false);
		config.controler().setDumpDataAtEnd(false);
		config.controler().setWriteEventsInterval(config.controler().getLastIteration());
		config.controler().setWritePlansInterval(config.controler().getLastIteration());

		// define activity types
		{
			ActivityParams dummyAct = new ActivityParams("dummy");
			dummyAct.setTypicalDuration(12 * 3600);
			dummyAct.setOpeningTime(5 * 3600);
			dummyAct.setLatestStartTime(10 * 3600);
			config.planCalcScore().addActivityParams(dummyAct);
		}
		{
			ActivityParams homeAct = new ActivityParams("home");
			homeAct.setTypicalDuration(15.5 * 3600);
			config.planCalcScore().addActivityParams(homeAct);
		}
		{
			ActivityParams workAct = new ActivityParams("work");
			workAct.setTypicalDuration(8.5 * 3600);
			workAct.setOpeningTime(7 * 3600);
			workAct.setClosingTime(17.5 * 3600);
			config.planCalcScore().addActivityParams(workAct);
		}
		
		return config;
	}
	
}
