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
package scenarios.illustrative.gridlock;

import analysis.signals.SignalAnalysisListener;
import analysis.signals.SignalAnalysisWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.contrib.signals.analysis.SignalAnalysisTool;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.controller.fixedTime.DefaultPlanbasedSignalSystemController;
import org.matsim.contrib.signals.controller.laemmerFix.LaemmerConfigGroup;
import org.matsim.contrib.signals.controller.laemmerFix.LaemmerSignalController;
import org.matsim.contrib.signals.controller.sylvia.SylviaConfigGroup;
import org.matsim.contrib.signals.controller.sylvia.SylviaPreprocessData;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalControlData;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalControlDataFactory;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalControlDataFactoryImpl;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalControlDataImpl;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalPlanData;
import org.matsim.contrib.signals.data.signalgroups.v20.*;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemControllerData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsDataFactory;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsDataFactoryImpl;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalPlan;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.contrib.signals.utils.SignalUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultSelector;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.lanes.*;
import signals.downstreamSensor.DownstreamPlanbasedSignalController;
import signals.gershenson.GershensonConfig;
import signals.gershenson.GershensonSignalController;
import signals.laemmerFlex.FullyAdaptiveLaemmerSignalController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author tthunig
 *
 */
public class RunGridLock {

	private static final Logger log = Logger.getLogger(RunGridLock.class);
	
	private enum SignalType { NONE, PLANBASED, DOWNSTREAM, SYLVIA, LAEMMER}
	private static final SignalType SIGNALTYPE = SignalType.LAEMMER;
	
	private enum SignalBasis { GREEN, CONFLICTING, TWO_ALTERNATING }
	private static final SignalBasis SIGNALBASIS = SignalBasis.TWO_ALTERNATING;
	
	private static final double MIDDLE_LINK_CAP = 3600;
	// no grid lock for 3600 and planbased signals: they let only 1800 vehicles enter the system
	
	private static final int DEMAND_START_TIME_OFFSET  = 0; // choose 0 if both streams should start at the same time
	private enum DemandIntensity { CONSTANT, INCREASING, PERIODIC}
	private static final DemandIntensity DEMAND_INTENSITY  = DemandIntensity.INCREASING;

	public static void main(String[] args) {
		Config config = defineConfig();
		
//		OTFVisConfigGroup otfvisConfig = ConfigUtils.addOrGetModule(config, OTFVisConfigGroup.class ) ;
//		otfvisConfig.setDrawTime(true);
//		otfvisConfig.setAgentSize(80f);
		
		LaemmerConfigGroup laemmerConfigGroup = ConfigUtils.addOrGetModule(config, LaemmerConfigGroup.class);
		laemmerConfigGroup.setIntergreenTime(1);
		laemmerConfigGroup.setDesiredCycleTime(60);
		laemmerConfigGroup.setMaxCycleTime(90);
		laemmerConfigGroup.setCheckDownstream(false); // TODO try this out
		
		SylviaConfigGroup sylviaConfig = ConfigUtils.addOrGetModule(config, SylviaConfigGroup.class);
//		sylviaConfig.setUseFixedTimeCycleAsMaximalExtension(false);
//		sylviaConfig.setSignalGroupMaxGreenScale(2);
//		sylviaConfig.setCheckDownstream(true);

		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		createGridLockNetworkAndLanes(scenario.getNetwork(), scenario.getLanes());
		createTwoStreamPopulation(scenario.getPopulation(), Id.createLinkId("0_1"), Id.createLinkId("2_1"), Id.createLinkId("5_4"), Id.createLinkId("3_4"), true);
		if (!SIGNALTYPE.equals(SignalType.NONE)){
			createGridlockSignals(scenario);
		}
		
		Controler controler = new Controler(scenario);
//		controler.addOverridingModule( new OTFVisWithSignalsLiveModule() ) ;
		if (!SIGNALTYPE.equals(SignalType.NONE)) {
			// add signal module
//			SignalsModule signalsModule = new SignalsModule();
			Signals.Configurator configurator = new Signals.Configurator( controler ) ;
			// the signals module works for planbased, sylvia and laemmer signal controller
			// by default and is pluggable for your own signal controller like this:
			configurator.addSignalControllerFactory(DownstreamPlanbasedSignalController.IDENTIFIER,
					DownstreamPlanbasedSignalController.DownstreamFactory.class);
			configurator.addSignalControllerFactory(FullyAdaptiveLaemmerSignalController.IDENTIFIER,
					FullyAdaptiveLaemmerSignalController.LaemmerFlexFactory.class);
			configurator.addSignalControllerFactory(GershensonSignalController.IDENTIFIER,
					GershensonSignalController.GershensonFactory.class);
//			controler.addOverridingModule(signalsModule);

			controler.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
					// bind gershenson config
					GershensonConfig gershensonConfig = new GershensonConfig();
					bind(GershensonConfig.class).toInstance(gershensonConfig);
					
					// TODO inflow and outflow analysis so far in TtRunPostAnalysis

					// bind tool to analyze signals
					this.bind(SignalAnalysisTool.class);
					this.bind(SignalAnalysisWriter.class);
					this.addControlerListenerBinding().to(SignalAnalysisListener.class);
				}
			});
		}

		controler.run();
	}
	
	private static Config defineConfig() {
		Config config = ConfigUtils.createConfig();
		config.controler().setOutputDirectory("../../runs-svn/gridlock/laemmer/"+SIGNALTYPE+"_basis"+SIGNALBASIS+MIDDLE_LINK_CAP+"_demand"+DEMAND_INTENSITY+"_offset"+DEMAND_START_TIME_OFFSET+"/");

		// set number of iterations
		config.controler().setLastIteration(0);

		// able or enable signals and lanes
		if (!SIGNALTYPE.equals(SignalType.NONE)) {
			SignalSystemsConfigGroup signalConfigGroup = ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class);
			signalConfigGroup.setUseSignalSystems(true);
		}
		config.qsim().setUseLanes(true);

		// // set brain exp beta
		// config.planCalcScore().setBrainExpBeta( 2 );

		// // set travelTimeBinSize (only has effect if reRoute is used)
		// config.travelTimeCalculator().setTraveltimeBinSize( 10 );

		// config.travelTimeCalculator().setTravelTimeCalculatorType(TravelTimeCalculatorType.TravelTimeCalculatorHashMap.toString());
		// // hash map and array produce same results. only difference: memory and time.
		// // for small time bins and sparse values hash map is better. theresa, may'15

		// define strategies:
		// {
		// StrategySettings strat = new StrategySettings();
		// strat.setStrategyName(DefaultStrategy.ReRoute.toString());
		// strat.setWeight(0.1);
		// strat.setDisableAfter(config.controler().getLastIteration() - 50);
		// config.strategy().addStrategySettings(strat);
		// }
		// {
		// StrategySettings strat = new StrategySettings();
		// strat.setStrategyName(DefaultSelector.ChangeExpBeta.toString());
		// strat.setWeight(0.9);
		// strat.setDisableAfter(config.controler().getLastIteration());
		// config.strategy().addStrategySettings(strat);
		// }
		{
			StrategySettings strat = new StrategySettings();
			strat.setStrategyName(DefaultSelector.KeepLastSelected);
			strat.setWeight(0.9);
			strat.setDisableAfter(config.controler().getLastIteration());
			config.strategy().addStrategySettings(strat);
		}

		// choose maximal number of plans per agent. 0 means unlimited
		config.strategy().setMaxAgentPlanMemorySize(1);

		// TODO use lower values?
		config.qsim().setStuckTime(3600);
		config.qsim().setRemoveStuckVehicles(false);
		
		config.qsim().setUsingFastCapacityUpdate(false);

		config.qsim().setStartTime(0);
		config.qsim().setEndTime(4 * 3600);

		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);

		config.vspExperimental().setWritingOutputEvents(true);
		config.planCalcScore().setWriteExperiencedPlans(true);
		config.controler().setCreateGraphs(true);

		config.controler().setWriteEventsInterval(1);
		config.controler().setWritePlansInterval(config.controler().getLastIteration());

		// define activity types
		{
			ActivityParams dummyAct = new ActivityParams("dummy");
			dummyAct.setTypicalDuration(12 * 3600);
			config.planCalcScore().addActivityParams(dummyAct);
		}

		return config;
	}

	private static void createGridLockNetworkAndLanes(Network net, Lanes lanes) {
		NetworkFactory netFac = net.getFactory();

		net.addNode(netFac.createNode(Id.createNodeId(0), new Coord(-3000, 0)));
		net.addNode(netFac.createNode(Id.createNodeId(1), new Coord(-2000, 0)));
		net.addNode(netFac.createNode(Id.createNodeId(2), new Coord(-1000, 0)));
		net.addNode(netFac.createNode(Id.createNodeId(3), new Coord(0, 0)));
		net.addNode(netFac.createNode(Id.createNodeId(4), new Coord(1000, 0)));
		net.addNode(netFac.createNode(Id.createNodeId(5), new Coord(2000, 0)));

		String[] links = { "0_1", "1_2", "2_1", "2_3", "3_2", "3_4", "4_3", "5_4" };

		for (String linkId : links) {
			String fromNodeId = linkId.split("_")[0];
			String toNodeId = linkId.split("_")[1];
			Link link = netFac.createLink(Id.createLinkId(linkId), net.getNodes().get(Id.createNodeId(fromNodeId)), net.getNodes().get(Id.createNodeId(toNodeId)));
			link.setCapacity(3600);
			link.setLength(1000);
			link.setFreespeed(10);
			net.addLink(link);
		}
		// create bottleneck at link 2_3 and 3_2
		net.getLinks().get(Id.createLinkId("2_3")).setCapacity(MIDDLE_LINK_CAP);
		net.getLinks().get(Id.createLinkId("3_2")).setCapacity(MIDDLE_LINK_CAP);

		LanesFactory lanesFac = lanes.getFactory();

		// create link assignment of link 2_3
		LanesToLinkAssignment linkAssignment2_3 = lanesFac.createLanesToLinkAssignment(Id.createLinkId("2_3"));
		LanesUtils.createAndAddLane(linkAssignment2_3, lanesFac, Id.create("2_3.ol", Lane.class), MIDDLE_LINK_CAP, 1000, 0, 2, null,
				Arrays.asList(Id.create("2_3.l", Lane.class), Id.create("2_3.s", Lane.class)));
		LanesUtils.createAndAddLane(linkAssignment2_3, lanesFac, Id.create("2_3.l", Lane.class), MIDDLE_LINK_CAP, 500, 1, 1, Collections.singletonList(Id.createLinkId("3_2")), null);
		LanesUtils.createAndAddLane(linkAssignment2_3, lanesFac, Id.create("2_3.s", Lane.class), MIDDLE_LINK_CAP, 500, 0, 1, Collections.singletonList(Id.createLinkId("3_4")), null);
		lanes.addLanesToLinkAssignment(linkAssignment2_3);

		// create link assignment of link 3_2
		LanesToLinkAssignment linkAssignment3_2 = lanesFac.createLanesToLinkAssignment(Id.createLinkId("3_2"));
		LanesUtils.createAndAddLane(linkAssignment3_2, lanesFac, Id.create("3_2.ol", Lane.class), MIDDLE_LINK_CAP, 1000, 0, 2, null,
				Arrays.asList(Id.create("3_2.l", Lane.class), Id.create("3_2.s", Lane.class)));
		LanesUtils.createAndAddLane(linkAssignment3_2, lanesFac, Id.create("3_2.l", Lane.class), MIDDLE_LINK_CAP, 500, 1, 1, Collections.singletonList(Id.createLinkId("2_3")), null);
		LanesUtils.createAndAddLane(linkAssignment3_2, lanesFac, Id.create("3_2.s", Lane.class), MIDDLE_LINK_CAP, 500, 0, 1, Collections.singletonList(Id.createLinkId("2_1")), null);
		lanes.addLanesToLinkAssignment(linkAssignment3_2);
	}
	
	private static void createSingleStreamPopulation(Population pop, Id<Link> fromLinkId, Id<Link> toLinkId, boolean initRoutes, int startTime) {
		PopulationFactory fac = pop.getFactory();
		
		int simTime = 3600;
		int currentActEnd = startTime;
		for (int i = 0; currentActEnd < startTime + simTime; i++) {
			// create a person (the i-th person)
			Person person = fac.createPerson(Id.createPersonId(fromLinkId + "-" + toLinkId + "-" + i));
			pop.addPerson(person);

			// create a plan for the person that contains all this information
			Plan plan = fac.createPlan();
			person.addPlan(plan);

			// create a start activity at the from link
			Activity startAct = fac.createActivityFromLinkId("dummy", fromLinkId);
			startAct.setEndTime(currentActEnd);
			plan.addActivity(startAct);
			if (DEMAND_INTENSITY.equals(DemandIntensity.CONSTANT)){
				// distribute agents uniformly during one hour.
				currentActEnd++;
			} else if (DEMAND_INTENSITY.equals(DemandIntensity.INCREASING)) {
//				if (currentActEnd < simTime/3){
//					currentActEnd += 4;
//				} else if (currentActEnd < (simTime/3)*2){
//					currentActEnd += 3;
//				} else {
//					currentActEnd += 2;
//				}
				
				if (currentActEnd < simTime/4){
					currentActEnd += 4;
				} else if (currentActEnd < (simTime/4)*2){
					currentActEnd += 3;
				} else if (currentActEnd < (simTime/4)*3){
					currentActEnd += 2;
				} else {
					currentActEnd += 1;
				}
				
//				if (currentActEnd < simTime/5){
//					currentActEnd += 5;
//				} else if (currentActEnd < (simTime/5)*2){
//					currentActEnd += 4;
//				} else if (currentActEnd < (simTime/5)*3){
//					currentActEnd += 3;
//				} else if (currentActEnd < (simTime/5)*4){
//					currentActEnd += 2;
//				} else {
//					currentActEnd += 1;
//				}
			} else {
				throw new UnsupportedOperationException("The choosen demand intensity " + DEMAND_INTENSITY + " is not yet supported.");
			}
			
			// create a dummy leg
			Leg leg = fac.createLeg(TransportMode.car);
			if (initRoutes) {
				// create routes for the agents. otherwise the router will not directly find the right one's because it is not sensitive for turning restrictions of signals
				List<Id<Link>> path = new ArrayList<>();
				if (fromLinkId.equals(Id.createLinkId("0_1"))) {
					path.add(Id.createLinkId("1_2"));
					path.add(Id.createLinkId("2_3"));
					path.add(Id.createLinkId("3_2"));
				} else { // fromLinkId equals 5_4
					path.add(Id.createLinkId("4_3"));
					path.add(Id.createLinkId("3_2"));
					path.add(Id.createLinkId("2_3"));
				}
				leg.setRoute(RouteUtils.createLinkNetworkRouteImpl(fromLinkId, path, toLinkId));
			}
			plan.addLeg(leg);

			// create a drain activity at the to link
			Activity drainAct = fac.createActivityFromLinkId("dummy", toLinkId);
			plan.addActivity(drainAct);
		}
	}

	private static void createTwoStreamPopulation(Population pop, Id<Link> fromLinkId1, Id<Link> toLinkId1, Id<Link> fromLinkId2, Id<Link> toLinkId2, boolean initRoutes) {
		createSingleStreamPopulation(pop, fromLinkId1, toLinkId1, initRoutes, 0);
		createSingleStreamPopulation(pop, fromLinkId2, toLinkId2, initRoutes, DEMAND_START_TIME_OFFSET);
	}
	
	private static void createGridlockSignals(Scenario scenario) {
		// add missing signals scenario element
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(scenario.getConfig()).loadSignalsData());
		
		createUTurnNoTurnSystem(scenario, Id.createNodeId(2), Id.createLinkId("1_2"), Id.createLinkId("2_3"), Id.createLinkId("3_2"), Id.createLinkId("2_1"));
		createUTurnNoTurnSystem(scenario, Id.createNodeId(3), Id.createLinkId("4_3"), Id.createLinkId("3_2"), Id.createLinkId("2_3"), Id.createLinkId("3_4"));
	}

	private static void createUTurnNoTurnSystem(Scenario scenario, Id<Node> systemNodeId, Id<Link> incomminigLinkId, Id<Link> incommingToLinkId, Id<Link> TwoLaneLinkId, Id<Link> outgoingLinkId) {
		SignalsData signalsData = (SignalsData) scenario.getScenarioElement(SignalsData.ELEMENT_NAME);
		SignalSystemsData signalSystems = signalsData.getSignalSystemsData();
		SignalSystemsDataFactory sysFac = new SignalSystemsDataFactoryImpl();
		SignalGroupsData signalGroups = signalsData.getSignalGroupsData();
		SignalControlData signalControl = signalsData.getSignalControlData();
		SignalControlDataFactory conFac = new SignalControlDataFactoryImpl();

		// create a temporary, empty signal control object needed for sylvia
		SignalControlData tmpSignalControl = new SignalControlDataImpl();
		
		// create signal system
		Id<SignalSystem> signalSystemId = Id.create("SignalSystem" + systemNodeId, SignalSystem.class);
		SignalSystemData signalSystem = sysFac.createSignalSystemData(signalSystemId);
		signalSystems.addSignalSystemData(signalSystem);

		// create three signals for the system
		SignalData signalIncomming = sysFac.createSignalData(Id.create("Signal" + incomminigLinkId, Signal.class));
		signalSystem.addSignalData(signalIncomming);
		signalIncomming.setLinkId(incomminigLinkId);
		signalIncomming.addTurningMoveRestriction(incommingToLinkId);
		SignalData signalOutgoing = sysFac.createSignalData(Id.create("Signal" + TwoLaneLinkId + ".s", Signal.class));
		signalSystem.addSignalData(signalOutgoing);
		signalOutgoing.setLinkId(TwoLaneLinkId);
		signalOutgoing.addLaneId(Id.create(TwoLaneLinkId + ".s", Lane.class));
		signalOutgoing.addTurningMoveRestriction(outgoingLinkId);
		SignalData signalUTurn = sysFac.createSignalData(Id.create("Signal" + TwoLaneLinkId + ".l", Signal.class));
		signalSystem.addSignalData(signalUTurn);
		signalUTurn.setLinkId(TwoLaneLinkId);
		signalUTurn.addLaneId(Id.create(TwoLaneLinkId + ".l", Lane.class));
		signalUTurn.addTurningMoveRestriction(incommingToLinkId);

		if (!SIGNALTYPE.equals(SignalType.LAEMMER)) {
			// create a group for all signals each (one element groups)
			SignalUtils.createAndAddSignalGroups4Signals(signalGroups, signalSystem);
		} else {
			// define groups later while setting signal control
		}

		// create the signal control
		SignalSystemControllerData signalSystemControl = conFac.createSignalSystemControllerData(signalSystemId);
		// create a plan for the signal system (with defined cycle time and offset 0)
		SignalPlanData signalPlan = SignalUtils.createSignalPlan(conFac, 60, 0, Id.create("SignalPlan1", SignalPlan.class));
		signalSystemControl.addSignalPlanData(signalPlan);
		// specify signal group settings for the single element signal groups
		switch (SIGNALTYPE){
		case DOWNSTREAM:
			signalSystemControl.setControllerIdentifier(DownstreamPlanbasedSignalController.IDENTIFIER);
			switch (SIGNALBASIS){
			case GREEN:
				log.info("Create almost all day green base plan for downstream signal controller");
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalIncomming.getId(), SignalGroup.class), 0, 59));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalOutgoing.getId(), SignalGroup.class), 0, 59));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalUTurn.getId(), SignalGroup.class), 0, 59));
				break;
			case CONFLICTING:
				log.info("Create alternating signal phases for conflicting streams");
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalIncomming.getId(), SignalGroup.class), 0, 29));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalOutgoing.getId(), SignalGroup.class), 0, 59));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalUTurn.getId(), SignalGroup.class), 30, 59));
				break;
			case TWO_ALTERNATING:
				log.info("Create two alternating signal phases like for SYLVIA");
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalIncomming.getId(), SignalGroup.class), 0, 29));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalOutgoing.getId(), SignalGroup.class), 30, 59));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalUTurn.getId(), SignalGroup.class), 30, 59));
				break;
			}
			signalControl.addSignalSystemControllerData(signalSystemControl);
			break;
		case PLANBASED:
			signalSystemControl.setControllerIdentifier(DefaultPlanbasedSignalSystemController.IDENTIFIER);
			switch (SIGNALBASIS){
			case GREEN:
				throw new UnsupportedOperationException("Planbased signals and basis green canot be combined.");
			case CONFLICTING:
				log.info("Create alternating signal phases for conflicting streams");
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalIncomming.getId(), SignalGroup.class), 0, 29));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalOutgoing.getId(), SignalGroup.class), 0, 59));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalUTurn.getId(), SignalGroup.class), 30, 59));
				break;
			case TWO_ALTERNATING:
				log.info("Create two alternating signal phases like for SYLVIA");
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalIncomming.getId(), SignalGroup.class), 0, 29));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalOutgoing.getId(), SignalGroup.class), 30, 59));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalUTurn.getId(), SignalGroup.class), 30, 59));
				break;
			}
			signalControl.addSignalSystemControllerData(signalSystemControl);
			break;
		case LAEMMER:
			signalSystemControl.setControllerIdentifier(LaemmerSignalController.IDENTIFIER);
			switch (SIGNALBASIS){
			case GREEN:
				throw new UnsupportedOperationException("Laemmer can not be combined with basis " + SignalBasis.GREEN + ". It needs information about signal groups.");
			case CONFLICTING:
				throw new UnsupportedOperationException("The current implementation of Laemmer signals can not be combined with basis " + SignalBasis.CONFLICTING + ". It needs disjunct signal groups.");
			case TWO_ALTERNATING:
				log.info("Create two alternating signal phases");
				// create disjunct groups
				SignalGroupData incommingGroup = signalGroups.getFactory().createSignalGroupData(signalSystemId, Id.create("incomming"+systemNodeId, SignalGroup.class));
				incommingGroup.addSignalId(signalIncomming.getId());
				signalGroups.addSignalGroupData(incommingGroup);
				SignalGroupData outgoingUturnGroup = signalGroups.getFactory().createSignalGroupData(signalSystemId, Id.create("outgoingUturn"+systemNodeId, SignalGroup.class));
				outgoingUturnGroup.addSignalId(signalOutgoing.getId());
				outgoingUturnGroup.addSignalId(signalUTurn.getId());
				signalGroups.addSignalGroupData(outgoingUturnGroup);
				// add control
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, incommingGroup.getId(), 0, 29));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, outgoingUturnGroup.getId(), 30, 59));
				break;
			}
			signalControl.addSignalSystemControllerData(signalSystemControl);
			break;
		case SYLVIA:
			signalSystemControl.setControllerIdentifier(DefaultPlanbasedSignalSystemController.IDENTIFIER);
			switch (SIGNALBASIS) {
			case GREEN:
				throw new UnsupportedOperationException("Sylvia can not be combined with basis " + SignalBasis.GREEN);
			case CONFLICTING:
				log.info("Create alternating signal phases for conflicting streams");
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalIncomming.getId(), SignalGroup.class), 0, 29));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalOutgoing.getId(), SignalGroup.class), 0, 59));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalUTurn.getId(), SignalGroup.class), 30, 59));
				break;
			case TWO_ALTERNATING:
				log.warn("outgoing signal time shortend to combine signal phases for sylvia. keep in mind while comparing planbased and sylvia!");
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalIncomming.getId(), SignalGroup.class), 0, 29));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalOutgoing.getId(), SignalGroup.class), 30, 59));
				signalPlan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(conFac, Id.create(signalUTurn.getId(), SignalGroup.class), 30, 59));
				break;
			}
			tmpSignalControl.addSignalSystemControllerData(signalSystemControl);
			// create the sylvia signal control by shorten the temporary signal control
			SylviaPreprocessData.convertSignalControlData(tmpSignalControl, signalControl);
			break;
		default:
			break;
		}
	}
	
}
