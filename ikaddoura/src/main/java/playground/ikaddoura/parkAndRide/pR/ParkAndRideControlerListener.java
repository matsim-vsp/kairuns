/* *********************************************************************** *
 * project: org.matsim.*
 * MyControlerListener.java
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
package playground.ikaddoura.parkAndRide.pR;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.StrategyManager;
import org.matsim.core.replanning.modules.ReRoute;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.pt.replanning.TransitActsRemoverStrategy;

//import playground.ikaddoura.parkAndRide.pRstrategy.ParkAndRideRemoveStrategy;
import playground.ikaddoura.parkAndRide.pRstrategy.ParkAndRideChangeLocationStrategy;
import playground.ikaddoura.parkAndRide.pRstrategy.PlanStrategyImpl_parkAndRide;
import playground.ikaddoura.parkAndRide.pRstrategy.PlanStrategyImpl_work;
import playground.ikaddoura.parkAndRide.pRstrategy.PrWeight;
import playground.ikaddoura.parkAndRide.pRstrategy.ParkAndRideAddStrategy;
import playground.ikaddoura.parkAndRide.pRstrategy.ParkAndRideTimeAllocationMutator;

/**
 * @author Ihab
 *
 */
public class ParkAndRideControlerListener implements StartupListener {
	
	Controler controler;
	AdaptiveCapacityControl adaptiveControl;
	private Map<Id, ParkAndRideFacility> id2prFacility = new HashMap<Id, ParkAndRideFacility>();
	private Map<Id, List<PrWeight>> personId2prWeights = new HashMap<Id, List<PrWeight>>();
	private double addPRProb;
	private int addPRDisable;
	private double changeLocationProb;
	private int changeLocationDisable;
//	private double removePRProb = 0.;
//	private int removePRDisable = 0;
	private double timeAllocationProb;
	private int timeAllocationDisable;
	private int gravity;
	
	ParkAndRideControlerListener(Controler ctl, AdaptiveCapacityControl adaptiveControl, Map<Id, ParkAndRideFacility> id2prFacility, double addPRProb, int addPRDisable, double changeLocationProb, int changeLocationDisable ,double timeAllocationProb, int timeAllocationDisable, int gravity) {
		this.controler = ctl;
		this.adaptiveControl = adaptiveControl;
		this.id2prFacility = id2prFacility;
		
		this.addPRProb = addPRProb;
		this.addPRDisable = addPRDisable;
		
		this.changeLocationProb = changeLocationProb;
		this.changeLocationDisable = changeLocationDisable;
		
		this.timeAllocationProb = timeAllocationProb;
		this.timeAllocationDisable = timeAllocationDisable;
		
		this.gravity = gravity;
	}

	@Override
	public void notifyStartup(StartupEvent event) {
		event.getControler().getEvents().addHandler(adaptiveControl);
		
		PlanStrategy strategyAddPR = new PlanStrategyImpl_work(new RandomPlanSelector());
		strategyAddPR.addStrategyModule(new TransitActsRemoverStrategy(controler.getConfig()));
		strategyAddPR.addStrategyModule(new ParkAndRideAddStrategy(controler, id2prFacility, personId2prWeights, gravity)); // adds P+R to a randomly chosen home-work-home sequence
		strategyAddPR.addStrategyModule(new ReRoute(controler));
		
		PlanStrategy strategyChangeLocation = new PlanStrategyImpl_parkAndRide(new RandomPlanSelector());
		strategyChangeLocation.addStrategyModule(new TransitActsRemoverStrategy(controler.getConfig()));
		strategyChangeLocation.addStrategyModule(new ParkAndRideChangeLocationStrategy(controler, id2prFacility, personId2prWeights, gravity)); // change the P+R location of a randomly chosen home-work-home sequence
		strategyChangeLocation.addStrategyModule(new ReRoute(controler));
		
		PlanStrategy strategyTimeAllocation = new PlanStrategyImpl_parkAndRide(new RandomPlanSelector());
		strategyTimeAllocation.addStrategyModule(new ParkAndRideTimeAllocationMutator(controler.getConfig())); // TimeAllocation, not changing "parkAndRide" and "pt interaction"
		strategyTimeAllocation.addStrategyModule(new ReRoute(controler));
		
//		PlanStrategy strategyRemovePR = new PlanStrategyImpl_work(new RandomPlanSelector());
//		strategyRemovePR.addStrategyModule(new TransitActsRemoverStrategy(controler.getConfig()));
//		strategyRemovePR.addStrategyModule(new ParkAndRideRemoveStrategy(controler)); // removes P+R from a randomly chosen home-work-home sequence
//		strategyRemovePR.addStrategyModule(new ReRoute(controler));

		StrategyManager manager = this.controler.getStrategyManager() ;
	
		manager.addStrategy(strategyAddPR, this.addPRProb);
		manager.addChangeRequest(this.addPRDisable, strategyAddPR, 0.);
		
		manager.addStrategy(strategyChangeLocation, this.changeLocationProb);
		manager.addChangeRequest(this.changeLocationDisable, strategyChangeLocation, 0.);
		
		manager.addStrategy(strategyTimeAllocation, this.timeAllocationProb);
		manager.addChangeRequest(this.timeAllocationDisable, strategyTimeAllocation, 0.);
		
//		manager.addStrategy(strategyRemovePR, this.removePRProb);
//		manager.addChangeRequest(this.removePRDisable, strategyRemovePR, 0.);
		
	}

}
