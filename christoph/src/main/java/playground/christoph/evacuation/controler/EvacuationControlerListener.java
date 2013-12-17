/* *********************************************************************** *
 * project: org.matsim.*
 * EvacuationControlerListener.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

package playground.christoph.evacuation.controler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.multimodal.MultiModalControlerListener;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.mobsim.framework.MobsimFactory;
import org.matsim.core.mobsim.qsim.qnetsimengine.JointDepartureOrganizer;
import org.matsim.core.mobsim.qsim.qnetsimengine.MissedJointDepartureWriter;
import org.matsim.core.mobsim.qsim.qnetsimengine.PassengerQNetsimEngine;
import org.matsim.core.router.RoutingContext;
import org.matsim.core.router.RoutingContextImpl;
import org.matsim.core.router.TripRouterFactory;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutilityFactory;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.FastAStarLandmarksFactory;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterNetwork;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.withinday.controller.WithinDayControlerListener;
import org.matsim.withinday.mobsim.MobsimDataProvider;
import org.matsim.withinday.mobsim.WithinDayEngine;
import org.matsim.withinday.replanning.identifiers.ActivityPerformingIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.LeaveLinkIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.LegPerformingIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.filter.ActivityStartingFilterFactory;
import org.matsim.withinday.replanning.identifiers.filter.EarliestLinkExitTimeFilterFactory;
import org.matsim.withinday.replanning.identifiers.filter.ProbabilityFilterFactory;
import org.matsim.withinday.replanning.identifiers.filter.TransportModeFilterFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.AgentFilterFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringActivityIdentifier;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringActivityIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringLegIdentifier;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringLegIdentifierFactory;
import org.matsim.withinday.replanning.replanners.CurrentLegReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringActivityReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringLegReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayReplannerFactory;
import org.matsim.withinday.trafficmonitoring.LinkEnteredProvider;
import org.opengis.feature.simple.SimpleFeature;

import playground.christoph.analysis.PassengerVolumesAnalyzer;
import playground.christoph.evacuation.analysis.AgentsInEvacuationAreaActivityCounter;
import playground.christoph.evacuation.analysis.AgentsInEvacuationAreaCounter;
import playground.christoph.evacuation.analysis.AgentsReturnHomeCounter;
import playground.christoph.evacuation.analysis.CoordAnalyzer;
import playground.christoph.evacuation.analysis.DetailedAgentsTracker;
import playground.christoph.evacuation.analysis.EvacuationTimePicture;
import playground.christoph.evacuation.analysis.LinkVolumesWriter;
import playground.christoph.evacuation.config.EvacuationConfig;
import playground.christoph.evacuation.mobsim.EvacuationQSimFactory;
import playground.christoph.evacuation.mobsim.HouseholdDepartureManager;
import playground.christoph.evacuation.mobsim.HouseholdsTracker;
import playground.christoph.evacuation.mobsim.InformedHouseholdsTracker;
import playground.christoph.evacuation.mobsim.ReplanningTracker;
import playground.christoph.evacuation.mobsim.VehiclesTracker;
import playground.christoph.evacuation.mobsim.decisiondata.DecisionDataGrabber;
import playground.christoph.evacuation.mobsim.decisiondata.DecisionDataProvider;
import playground.christoph.evacuation.mobsim.decisionmodel.DecisionModelRunner;
import playground.christoph.evacuation.pt.EvacuationTransitRouterFactory;
import playground.christoph.evacuation.pt.TransitRouterNetworkReaderMatsimV1;
import playground.christoph.evacuation.router.LeastCostPathCalculatorSelectorFactory;
import playground.christoph.evacuation.router.RandomCompassRouterFactory;
import playground.christoph.evacuation.router.util.AffectedAreaPenaltyCalculator;
import playground.christoph.evacuation.router.util.FuzzyTravelTimeEstimatorFactory;
import playground.christoph.evacuation.router.util.PenaltyTravelCostFactory;
import playground.christoph.evacuation.withinday.replanning.identifiers.AgentsToDropOffIdentifierFactory;
import playground.christoph.evacuation.withinday.replanning.identifiers.AgentsToPickupIdentifierFactory;
import playground.christoph.evacuation.withinday.replanning.identifiers.JoinedHouseholdsIdentifierFactory;
import playground.christoph.evacuation.withinday.replanning.identifiers.filters.AffectedAgentsFilter;
import playground.christoph.evacuation.withinday.replanning.identifiers.filters.AffectedAgentsFilterFactory;
import playground.christoph.evacuation.withinday.replanning.identifiers.filters.InformedAgentsFilter;
import playground.christoph.evacuation.withinday.replanning.identifiers.filters.InformedAgentsFilterFactory;
import playground.christoph.evacuation.withinday.replanning.replanners.CurrentLegToMeetingPointReplannerFactory;
import playground.christoph.evacuation.withinday.replanning.replanners.DropOffAgentReplannerFactory;
import playground.christoph.evacuation.withinday.replanning.replanners.PickupAgentReplannerFactory;
import playground.christoph.evacuation.withinday.replanning.utils.ModeAvailabilityChecker;
import playground.christoph.evacuation.withinday.replanning.utils.SHPFileUtil;
import playground.christoph.evacuation.withinday.replanning.utils.SelectHouseholdMeetingPoint;

import com.vividsolutions.jts.geom.Geometry;

public class EvacuationControlerListener implements StartupListener {

	private static final Logger log = Logger.getLogger(EvacuationControlerListener.class);
	
	private final WithinDayControlerListener withinDayControlerListener;
	private final MultiModalControlerListener multiModalControlerListener;
	
	/*
	 * Data collectors and providers
	 */
	private ReplanningTracker replanningTracker;
	private JointDepartureOrganizer jointDepartureOrganizer;
	private MissedJointDepartureWriter missedJointDepartureWriter;
	private VehiclesTracker vehiclesTracker;
	private HouseholdsTracker householdsTracker;
	private InformedHouseholdsTracker informedHouseholdsTracker;
	private DecisionDataGrabber decisionDataGrabber;
	private DecisionModelRunner decisionModelRunner;
	private LinkEnteredProvider linkEnteredProvider;
	private HouseholdDepartureManager householdDepartureManager;
	
	/*
	 * Geography related stuff
	 */
	private CoordAnalyzer coordAnalyzer;
	private AffectedAreaPenaltyCalculator penaltyCalculator;
	private Geometry affectedArea;
	
	private ModeAvailabilityChecker modeAvailabilityChecker;
	private SelectHouseholdMeetingPoint selectHouseholdMeetingPoint;
	
	/*
	 * Analysis modules
	 */
	private EvacuationTimePicture evacuationTimePicture;
	private AgentsReturnHomeCounter agentsReturnHomeCounter;
	private AgentsInEvacuationAreaCounter agentsInEvacuationAreaCounter;
	private AgentsInEvacuationAreaActivityCounter agentsInEvacuationAreaActivityCounter;
	private DetailedAgentsTracker detailedAgentsTracker;
	private LinkVolumesWriter linkVolumesWriter;
	
	/*
	 * Identifiers
	 */
	private DuringActivityIdentifier joinedHouseholdsIdentifier;
	private DuringActivityIdentifier activityPerformingIdentifier;
	private DuringLegIdentifier legPerformingIdentifier;
	private DuringLegIdentifier agentsToDropOffIdentifier;
	private DuringLegIdentifier agentsToPickupIdentifier;
	private DuringLegIdentifier duringLegRerouteIdentifier;
	
	/*
	 * ReplannerFactories
	 */
	private WithinDayDuringActivityReplannerFactory currentActivityToMeetingPointReplannerFactory;
	private WithinDayDuringActivityReplannerFactory joinedHouseholdsReplannerFactory;
	private WithinDayDuringLegReplannerFactory currentLegToMeetingPointReplannerFactory;
	private WithinDayDuringLegReplannerFactory dropOffAgentsReplannerFactory;
	private WithinDayDuringLegReplannerFactory pickupAgentsReplannerFactory;
	private WithinDayDuringLegReplannerFactory duringLegRerouteReplannerFactory;
	
	/*
	 * WithinDayTripRouter Stuff
	 */
	private Map<String, TravelTime> withinDayTravelTimes;
	private TripRouterFactory withinDayTripRouterFactory;
	private LeastCostPathCalculatorFactory withinDayLeastCostPathCalculatorFactory;
	private TravelDisutilityFactory withinDayTravelDisutilityFactory;
	
	/*
	 * Replanners that are used to adapt agent's plans for the first time. They can be disabled
	 * after all agents have been informed and have adapted their plans.
	 */
	private List<WithinDayReplannerFactory<?>> initialReplannerFactories;
	
	/*
	 * Data
	 */
	
	private final FixedOrderControlerListener fixedOrderControlerListener = new FixedOrderControlerListener();
	
	public EvacuationControlerListener(WithinDayControlerListener withinDayControlerListener, 
			MultiModalControlerListener multiModalControlerListener) {
		this.withinDayControlerListener = withinDayControlerListener;
		this.multiModalControlerListener = multiModalControlerListener;
	}
	
	@Override
	public void notifyStartup(StartupEvent event) {

		// register FixedOrderControlerListener
		event.getControler().addControlerListener(this.fixedOrderControlerListener);
		
		// load household object attributes
//		this.householdObjectAttributes = new ObjectAttributes();
//		new ObjectAttributesXmlReader(this.householdObjectAttributes).parse(EvacuationConfig.householdObjectAttributesFile);
		
		this.initGeographyStuff(event.getControler().getScenario());
		
		this.initDataGrabbersAndProviders(event.getControler());
		
		this.initAnalysisStuff(event.getControler());
		
//		this.initReplanningStuff(event.getControler());
		
		/*
		 * Use a MobsimFactory which creates vehicles according to available vehicles per
		 * household and adds the replanning Manager as mobsim engine.
		 */
		Scenario scenario = event.getControler().getScenario();
		MobsimFactory mobsimFactory = new EvacuationQSimFactory(this.withinDayControlerListener.getWithinDayEngine(), 
				((ScenarioImpl) scenario).getHouseholds().getHouseholdAttributes(), this.jointDepartureOrganizer, 
				this.multiModalControlerListener.getMultiModalTravelTimes());
		event.getControler().setMobsimFactory(mobsimFactory);
	}

	private void initGeographyStuff(Scenario scenario) {
		
		Set<SimpleFeature> features = new HashSet<SimpleFeature>();
		SHPFileUtil util = new SHPFileUtil();
		for (String file : EvacuationConfig.evacuationArea) {
			features.addAll(ShapeFileReader.getAllFeatures(file));		
		}
		this.affectedArea = util.mergeGeometries(features);
		log.info("Size of affected area: " + affectedArea.getArea());
		
		this.penaltyCalculator = new AffectedAreaPenaltyCalculator(scenario.getNetwork(), affectedArea, 
				EvacuationConfig.affectedAreaDistanceBuffer, EvacuationConfig.affectedAreaTimePenaltyFactor);
		
		this.coordAnalyzer = new CoordAnalyzer(affectedArea);
	}
	
	private void initDataGrabbersAndProviders(Controler controler) {
		
		Scenario scenario = controler.getScenario();
		
		this.jointDepartureOrganizer = new JointDepartureOrganizer();
		this.missedJointDepartureWriter = new MissedJointDepartureWriter(this.jointDepartureOrganizer);
		this.fixedOrderControlerListener.addControlerListener(this.missedJointDepartureWriter);
		
		this.informedHouseholdsTracker = new InformedHouseholdsTracker(controler.getPopulation(),
				((ScenarioImpl) controler.getScenario()).getHouseholds());
		this.withinDayControlerListener.getFixedOrderSimulationListener().addSimulationListener(informedHouseholdsTracker);
		controler.getEvents().addHandler(this.informedHouseholdsTracker);
		
		this.replanningTracker = new ReplanningTracker(this.informedHouseholdsTracker);
		controler.getEvents().addHandler(this.replanningTracker);
		
		this.householdsTracker = new HouseholdsTracker(scenario);
		controler.getEvents().addHandler(this.householdsTracker);
		this.fixedOrderControlerListener.addControlerListener(this.householdsTracker);
		this.withinDayControlerListener.getFixedOrderSimulationListener().addSimulationListener(householdsTracker);
		
		this.decisionDataGrabber = new DecisionDataGrabber(scenario, this.coordAnalyzer.createInstance(), 
				this.householdsTracker, ((ScenarioImpl) scenario).getHouseholds().getHouseholdAttributes());
		
		this.decisionModelRunner = new DecisionModelRunner(scenario, this.decisionDataGrabber, this.informedHouseholdsTracker);
		this.withinDayControlerListener.getFixedOrderSimulationListener().addSimulationListener(this.decisionModelRunner);
		this.fixedOrderControlerListener.addControlerListener(this.decisionModelRunner);
		
		this.vehiclesTracker = new VehiclesTracker(this.withinDayControlerListener.getMobsimDataProvider());
		controler.getEvents().addHandler(vehiclesTracker);

		this.modeAvailabilityChecker = new ModeAvailabilityChecker(scenario, this.withinDayControlerListener.getMobsimDataProvider());
				
		this.linkEnteredProvider = new LinkEnteredProvider();
		controler.getEvents().addHandler(this.linkEnteredProvider);
		this.withinDayControlerListener.getFixedOrderSimulationListener().addSimulationListener(this.linkEnteredProvider);
		
		// workaround
//		scenario.getConfig().scenario().setUseTransit(false);
		
		this.initWithinDayTravelTimes(controler);
		
		this.selectHouseholdMeetingPoint = new SelectHouseholdMeetingPoint(scenario, this.withinDayTravelTimes, 
				this.coordAnalyzer.createInstance(), this.affectedArea, this.informedHouseholdsTracker, 
				this.decisionModelRunner, this.withinDayControlerListener.getMobsimDataProvider());
		this.withinDayControlerListener.getFixedOrderSimulationListener().addSimulationListener(this.selectHouseholdMeetingPoint);
		this.fixedOrderControlerListener.addControlerListener(this.selectHouseholdMeetingPoint);
		
		this.householdDepartureManager = new HouseholdDepartureManager(scenario, this.coordAnalyzer.createInstance(), 
				this.householdsTracker, this.informedHouseholdsTracker, this.decisionModelRunner.getDecisionDataProvider());
	}
	
	private void initAnalysisStuff(Controler controler) {
		
		Scenario scenario = controler.getScenario();
		
		/*
		 * Create the set of analyzed modes.
		 */
		Set<String> analyzedModes = new HashSet<String>();
		analyzedModes.add(TransportMode.bike);
		analyzedModes.add(TransportMode.car);
		analyzedModes.add(TransportMode.pt);
		analyzedModes.add(TransportMode.ride);
		analyzedModes.add(TransportMode.walk);
		analyzedModes.add(PassengerQNetsimEngine.PASSENGER_TRANSPORT_MODE);

		// Create txt and kmz files containing distribution of evacuation times. 
		if (EvacuationConfig.createEvacuationTimePicture) {
			evacuationTimePicture = new EvacuationTimePicture(scenario, this.coordAnalyzer.createInstance(), this.householdsTracker, 
					this.withinDayControlerListener.getMobsimDataProvider());
			controler.addControlerListener(this.evacuationTimePicture);
			this.withinDayControlerListener.getFixedOrderSimulationListener().addSimulationListener(this.evacuationTimePicture);
			controler.getEvents().addHandler(this.evacuationTimePicture);	
		}
		
		// Create and add an AgentsInEvacuationAreaCounter.
		if (EvacuationConfig.countAgentsInEvacuationArea) {
			double scaleFactor = 1 / scenario.getConfig().qsim().getFlowCapFactor();
			
			agentsInEvacuationAreaCounter = new AgentsInEvacuationAreaCounter(scenario, analyzedModes, this.coordAnalyzer.createInstance(), 
					this.decisionModelRunner.getDecisionDataProvider(), scaleFactor);
			this.fixedOrderControlerListener.addControlerListener(this.agentsInEvacuationAreaCounter);
			this.withinDayControlerListener.getFixedOrderSimulationListener().addSimulationListener(this.agentsInEvacuationAreaCounter);
			controler.getEvents().addHandler(this.agentsInEvacuationAreaCounter);
			
			agentsInEvacuationAreaActivityCounter = new AgentsInEvacuationAreaActivityCounter(scenario, this.coordAnalyzer.createInstance(), 
					this.decisionModelRunner.getDecisionDataProvider(), scaleFactor);
			this.fixedOrderControlerListener.addControlerListener(this.agentsInEvacuationAreaActivityCounter);
			this.withinDayControlerListener.getFixedOrderSimulationListener().addSimulationListener(this.agentsInEvacuationAreaActivityCounter);
			controler.getEvents().addHandler(this.agentsInEvacuationAreaActivityCounter);
			
			this.agentsReturnHomeCounter = new AgentsReturnHomeCounter(scenario, analyzedModes, this.coordAnalyzer.createInstance(), 
					this.decisionModelRunner.getDecisionDataProvider(), scaleFactor);
			this.fixedOrderControlerListener.addControlerListener(this.agentsReturnHomeCounter);
			this.withinDayControlerListener.getFixedOrderSimulationListener().addSimulationListener(this.agentsReturnHomeCounter);
			controler.getEvents().addHandler(this.agentsReturnHomeCounter);
		}
		
		this.detailedAgentsTracker = new DetailedAgentsTracker(scenario, this.householdsTracker, 
				this.decisionModelRunner.getDecisionDataProvider(), this.coordAnalyzer);
		this.fixedOrderControlerListener.addControlerListener(this.detailedAgentsTracker);
		this.withinDayControlerListener.getFixedOrderSimulationListener().addSimulationListener(this.detailedAgentsTracker);
		controler.getEvents().addHandler(this.detailedAgentsTracker);
		
		int timeSlice = 900;
		int maxTime = 36 * 3600;
		VolumesAnalyzer volumesAnalyzer = new PassengerVolumesAnalyzer(timeSlice, maxTime, scenario.getNetwork());
		controler.getEvents().addHandler(volumesAnalyzer);
		double scaleFactor = 1 / scenario.getConfig().qsim().getFlowCapFactor();
		this.linkVolumesWriter = new LinkVolumesWriter(volumesAnalyzer, scenario.getNetwork(), timeSlice, maxTime, scaleFactor, true);
		this.fixedOrderControlerListener.addControlerListener(this.linkVolumesWriter);
	}
	
	private void initWithinDayTravelTimes(Controler controler) {
		
		Scenario scenario = controler.getScenario();
		
		this.withinDayTravelTimes = new HashMap<String, TravelTime>();
		withinDayTravelTimes.putAll(this.multiModalControlerListener.getMultiModalTravelTimes());
		withinDayTravelTimes.put(TransportMode.car, this.withinDayControlerListener.getTravelTimeCollector());
		
		/*
		 * If fuzzy travel times should be used, wrap each TravelTime into a
		 * FuzzyTravelTime object.
		 */
		if (EvacuationConfig.useFuzzyTravelTimes) {
			Map<String, TravelTime> fuzziedTravelTimes = new HashMap<String, TravelTime>();
			for (Entry<String, TravelTime> entry : this.withinDayTravelTimes.entrySet()) {
				String mode = entry.getKey();
				TravelTime travelTime = entry.getValue();
				FuzzyTravelTimeEstimatorFactory fuzzyTravelTimeEstimatorFactory = new FuzzyTravelTimeEstimatorFactory(scenario, 
						travelTime, this.householdsTracker, this.withinDayControlerListener.getMobsimDataProvider());
				TravelTime fuzziedTravelTime = fuzzyTravelTimeEstimatorFactory.createTravelTime();
				fuzziedTravelTimes.put(mode, fuzziedTravelTime);
			}
			this.withinDayTravelTimes = fuzziedTravelTimes;
		}
	}
	
	private void initReplanningStuff(Controler controler) {
		this.initWithinDayTripRouterFactory(controler);
		this.initIdentifiers(controler);
		this.initReplanners(controler);
	}
	
	private void initWithinDayTripRouterFactory(Controler controler) {
		
		Config config = controler.getConfig();
		Scenario scenario = controler.getScenario();
		
		/*
		 * Add time dependent penalties to travel costs within the affected area.
		 */
		TravelDisutilityFactory costFactory = new OnlyTimeDependentTravelDisutilityFactory();
		this.withinDayTravelDisutilityFactory = new PenaltyTravelCostFactory(costFactory, penaltyCalculator);
		
		LeastCostPathCalculatorFactory nonPanicFactory = new FastAStarLandmarksFactory(scenario.getNetwork(), 
				new FreespeedTravelTimeAndDisutility(scenario.getConfig().planCalcScore()));
		LeastCostPathCalculatorFactory panicFactory = new RandomCompassRouterFactory(EvacuationConfig.tabuSearch, 
				EvacuationConfig.compassProbability);
		this.withinDayLeastCostPathCalculatorFactory = new LeastCostPathCalculatorSelectorFactory(nonPanicFactory, panicFactory, 
				this.decisionModelRunner.getDecisionDataProvider());
		
		new TransitScheduleReader(scenario).readFile(config.transit().getTransitScheduleFile());
		TransitRouterNetwork routerNetwork = new TransitRouterNetwork();
		new TransitRouterNetworkReaderMatsimV1(scenario, routerNetwork).parse(EvacuationConfig.transitRouterFile);
		
		TransitRouterConfig transitRouterConfig = new TransitRouterConfig(config.planCalcScore(),
				config.plansCalcRoute(), config.transitRouter(), config.vspExperimental());
		EvacuationTransitRouterFactory evacuationTransitRouterFactory = 
				new EvacuationTransitRouterFactory(config, this.withinDayTravelTimes.get(TransportMode.walk), 
						routerNetwork, transitRouterConfig);
		
		// TODO: EvacuationTransitRouterFactory is not a TransitRouterFactory so far!
		this.withinDayTripRouterFactory = new EvacuationTripRouterFactory(scenario, this.withinDayTravelTimes, 
				this.withinDayTravelDisutilityFactory, this.withinDayLeastCostPathCalculatorFactory, null);		
	}
	
	private void initIdentifiers(Controler controler) {
		
		Scenario scenario = controler.getScenario();
		MobsimDataProvider mobsimDataProvider = this.withinDayControlerListener.getMobsimDataProvider();
		/*
		 * Initialize AgentFilters
		 */
		InformedAgentsFilterFactory initialReplanningFilterFactory = new InformedAgentsFilterFactory(this.informedHouseholdsTracker, 
				this.replanningTracker, InformedAgentsFilter.FilterType.InitialReplanning);
		InformedAgentsFilterFactory notInitialReplanningFilterFactory = new InformedAgentsFilterFactory(this.informedHouseholdsTracker, 
				this.replanningTracker, InformedAgentsFilter.FilterType.NotInitialReplanning);
		
		AffectedAgentsFilterFactory affectedAgentsFilterFactory = new AffectedAgentsFilterFactory(scenario, this.householdsTracker, 
				mobsimDataProvider, coordAnalyzer, AffectedAgentsFilter.FilterType.NotAffected);
		TransportModeFilterFactory carOnlyTransportModeFilterFactory = new TransportModeFilterFactory(
				CollectionUtils.stringToSet(TransportMode.car), mobsimDataProvider);
		TransportModeFilterFactory walkOnlyTransportModeFilterFactory = new TransportModeFilterFactory(
				CollectionUtils.stringToSet(TransportMode.walk), mobsimDataProvider);
		
		EarliestLinkExitTimeFilterFactory earliestLinkExitTimeFilterFactory = new EarliestLinkExitTimeFilterFactory(
				this.withinDayControlerListener.getEarliestLinkExitTimeProvider());
		ActivityStartingFilterFactory activityStartingFilterFactory = new ActivityStartingFilterFactory(mobsimDataProvider);
		
		Set<String> nonPTModes = new HashSet<String>();
		nonPTModes.add(TransportMode.car);
		nonPTModes.add(TransportMode.ride);
		nonPTModes.add(TransportMode.bike);
		nonPTModes.add(TransportMode.walk);
		AgentFilterFactory nonPTLegAgentsFilterFactory = new TransportModeFilterFactory(nonPTModes, mobsimDataProvider);
		
		DuringActivityIdentifierFactory duringActivityFactory;
		DuringLegIdentifierFactory duringLegFactory;
		
		/*
		 * During Activity Identifiers
		 */
		duringActivityFactory = new ActivityPerformingIdentifierFactory(this.withinDayControlerListener.getActivityReplanningMap(), 
				this.withinDayControlerListener.getMobsimDataProvider());
		duringActivityFactory.addAgentFilterFactory(initialReplanningFilterFactory);
		this.activityPerformingIdentifier = duringActivityFactory.createIdentifier();
		
		duringActivityFactory = new JoinedHouseholdsIdentifierFactory(scenario, this.selectHouseholdMeetingPoint, 
				this.modeAvailabilityChecker.createInstance(), this.jointDepartureOrganizer, mobsimDataProvider, 
				this.householdDepartureManager);
		duringActivityFactory.addAgentFilterFactory(notInitialReplanningFilterFactory);
		this.joinedHouseholdsIdentifier = duringActivityFactory.createIdentifier();
		
		/*
		 * During Leg Identifiers
		 */
		duringLegFactory = new LegPerformingIdentifierFactory(this.withinDayControlerListener.getLinkReplanningMap(), mobsimDataProvider);
		duringLegFactory.addAgentFilterFactory(initialReplanningFilterFactory);
		this.legPerformingIdentifier = duringLegFactory.createIdentifier();

		duringLegFactory = new AgentsToDropOffIdentifierFactory(mobsimDataProvider, linkEnteredProvider, jointDepartureOrganizer, 
				affectedAgentsFilterFactory, carOnlyTransportModeFilterFactory, notInitialReplanningFilterFactory, 
				earliestLinkExitTimeFilterFactory);
		this.agentsToDropOffIdentifier = duringLegFactory.createIdentifier();
	
		duringLegFactory = new AgentsToPickupIdentifierFactory(scenario, this.coordAnalyzer, this.vehiclesTracker,
				mobsimDataProvider, this.withinDayControlerListener.getEarliestLinkExitTimeProvider(), this.informedHouseholdsTracker, 
				this.decisionModelRunner.getDecisionDataProvider(), this.jointDepartureOrganizer, affectedAgentsFilterFactory, 
				walkOnlyTransportModeFilterFactory, notInitialReplanningFilterFactory, activityStartingFilterFactory); 
		duringLegFactory.addAgentFilterFactory(notInitialReplanningFilterFactory);
		this.agentsToPickupIdentifier = duringLegFactory.createIdentifier();
		
//		Set<String> duringLegRerouteTransportModes = new HashSet<String>();
//		duringLegRerouteTransportModes.add(TransportMode.car);
//		this.duringLegRerouteIdentifier = new LeaveLinkIdentifierFactory(this.getLinkReplanningMap(), duringLegRerouteTransportModes).createIdentifier();
		// replan all transport modes except PT
		duringLegFactory = new LeaveLinkIdentifierFactory(this.withinDayControlerListener.getLinkReplanningMap(), mobsimDataProvider); 
		duringLegFactory.addAgentFilterFactory(notInitialReplanningFilterFactory);
		duringLegFactory.addAgentFilterFactory(nonPTLegAgentsFilterFactory);
		duringLegFactory.addAgentFilterFactory(new ProbabilityFilterFactory(EvacuationConfig.duringLegReroutingShare));
		this.duringLegRerouteIdentifier = duringLegFactory.createIdentifier();
	}
	
	/*
	 * New Routers for the Replanning are used instead of using the controler's.
	 * By doing this every person can use a personalized Router.
	 */
	private void initReplanners(Controler controler) {
		
		Scenario scenario = controler.getScenario();
		WithinDayEngine withinDayEngine = this.withinDayControlerListener.getWithinDayEngine();
		DecisionDataProvider decisionDataProvider = this.decisionModelRunner.getDecisionDataProvider();
		TripRouterFactory tripRouterFactory = this.withinDayTripRouterFactory;	
		
		TravelDisutility travelDisutility = this.withinDayTravelDisutilityFactory.createTravelDisutility(
				this.withinDayTravelTimes.get(TransportMode.car), scenario.getConfig().planCalcScore()); 
		RoutingContext routingContext = new RoutingContextImpl(travelDisutility, this.withinDayTravelTimes.get(TransportMode.car));
		
		/*
		 * During Activity Replanners
		 */
//		this.currentActivityToMeetingPointReplannerFactory = new CurrentActivityToMeetingPointReplannerFactory(scenario, 
//				withinDayEngine, decisionDataProvider, this.modeAvailabilityChecker, 
//				(SwissPTTravelTime) ptTravelTime, tripRouterFactory, routingContext);
//		this.currentActivityToMeetingPointReplannerFactory.addIdentifier(this.activityPerformingIdentifier);
//		withinDayEngine.addTimedDuringActivityReplannerFactory(this.currentActivityToMeetingPointReplannerFactory, EvacuationConfig.evacuationTime, Double.MAX_VALUE);
		
//		this.joinedHouseholdsReplannerFactory = new JoinedHouseholdsReplannerFactory(scenario, withinDayEngine, 
//				decisionDataProvider,
//				(JoinedHouseholdsIdentifier) joinedHouseholdsIdentifier, (SwissPTTravelTime) this.ptTravelTime, tripRouterFactory, routingContext);
//		this.joinedHouseholdsReplannerFactory.addIdentifier(joinedHouseholdsIdentifier);
//		withinDayEngine.addTimedDuringActivityReplannerFactory(this.joinedHouseholdsReplannerFactory, EvacuationConfig.evacuationTime, Double.MAX_VALUE);

		/*
		 * During Leg Replanners
		 */
		this.currentLegToMeetingPointReplannerFactory = new CurrentLegToMeetingPointReplannerFactory(scenario, withinDayEngine,
				decisionDataProvider, tripRouterFactory, routingContext);
		this.currentLegToMeetingPointReplannerFactory.addIdentifier(this.legPerformingIdentifier);
		withinDayEngine.addTimedDuringLegReplannerFactory(this.currentLegToMeetingPointReplannerFactory, EvacuationConfig.evacuationTime, Double.MAX_VALUE);

		this.dropOffAgentsReplannerFactory = new DropOffAgentReplannerFactory(scenario, withinDayEngine, tripRouterFactory, routingContext);
		this.dropOffAgentsReplannerFactory.addIdentifier(this.agentsToDropOffIdentifier);
		withinDayEngine.addTimedDuringLegReplannerFactory(this.dropOffAgentsReplannerFactory, EvacuationConfig.evacuationTime, Double.MAX_VALUE);
		
		this.pickupAgentsReplannerFactory = new PickupAgentReplannerFactory(scenario, withinDayEngine);
		this.pickupAgentsReplannerFactory.addIdentifier(this.agentsToPickupIdentifier);
		withinDayEngine.addTimedDuringLegReplannerFactory(this.pickupAgentsReplannerFactory, EvacuationConfig.evacuationTime, Double.MAX_VALUE);
		
		this.duringLegRerouteReplannerFactory = new CurrentLegReplannerFactory(scenario, withinDayEngine, tripRouterFactory, routingContext);
		this.duringLegRerouteReplannerFactory.addIdentifier(this.duringLegRerouteIdentifier);
		withinDayEngine.addTimedDuringLegReplannerFactory(this.duringLegRerouteReplannerFactory, EvacuationConfig.evacuationTime, Double.MAX_VALUE);
		
		
		/*
		 * Collect Replanners that can be disabled after all agents have been informed.
		 */
		this.initialReplannerFactories = new ArrayList<WithinDayReplannerFactory<?>>();
		this.initialReplannerFactories.add(this.currentActivityToMeetingPointReplannerFactory);
		this.initialReplannerFactories.add(this.currentLegToMeetingPointReplannerFactory);
	}
}
