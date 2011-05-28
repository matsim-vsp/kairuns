/* *********************************************************************** *
 * project: org.matsim.*
 * CostNavigationRouteFactory
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

package playground.christoph.router;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.replanning.modules.AbstractMultithreadedModule;
import org.matsim.core.router.costcalculators.TravelCostCalculatorFactory;
import org.matsim.core.router.util.PersonalizableTravelTime;
import org.matsim.ptproject.qsim.interfaces.AgentCounterI;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringLegReplanner;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringLegReplannerFactory;

public class CostNavigationRouteFactory extends WithinDayDuringLegReplannerFactory {

	private Scenario scenario;
	private TravelCostCalculatorFactory travelCostFactory;
	private PersonalizableTravelTime travelTime;
	private CostNavigationTravelTimeLogger costNavigationTravelTimeLogger;
	
	public CostNavigationRouteFactory(Scenario scenario, AgentCounterI agentCounter, AbstractMultithreadedModule abstractMultithreadedModule, double replanningProbability,
			CostNavigationTravelTimeLogger costNavigationTravelTimeLogger, TravelCostCalculatorFactory travelCostFactory, PersonalizableTravelTime travelTime) {
		super(agentCounter, abstractMultithreadedModule, replanningProbability);
		this.scenario = scenario;
		this.costNavigationTravelTimeLogger = costNavigationTravelTimeLogger;
		this.travelCostFactory = travelCostFactory;
		this.travelTime = travelTime;
	}

	@Override
	public WithinDayDuringLegReplanner createReplanner() {
		WithinDayDuringLegReplanner replanner = new CostNavigationRoute(super.getId(), scenario, costNavigationTravelTimeLogger, travelCostFactory, travelTime);
		super.initNewInstance(replanner);
		return replanner;
	}
}