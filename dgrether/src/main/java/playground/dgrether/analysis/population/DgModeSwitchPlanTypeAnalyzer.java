/* *********************************************************************** *
 * project: org.matsim.*
 * DgModeSwitchAnalyzer
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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
package playground.dgrether.analysis.population;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.population.PlanImpl.DeprecatedConstants;
import org.matsim.core.utils.collections.Tuple;


/**
 * Create distinct groups from a given population by mode choice.
 * @author dgrether
 */
public class DgModeSwitchPlanTypeAnalyzer {
	
	private DgAnalysisPopulation pop;
	
	private Map<Tuple<String, String>, DgAnalysisPopulation> classifiedPops;

	public DgModeSwitchPlanTypeAnalyzer(DgAnalysisPopulation ana, Id runId1, Id runId2){
		this.pop = ana;
		this.classifiedPops = new HashMap<Tuple<String, String>, DgAnalysisPopulation>();
		this.classifyPopulationByPlanType(runId1, runId2);
	}
	
	private void classifyPopulationByPlanType(Id runId1, Id runId2){		
		for (DgPersonData d : pop.getPersonData().values()) {
			DgPlanData planDataRun1 = d.getPlanData().get(runId1);
			DgPlanData planDataRun2 = d.getPlanData().get(runId2);

			Tuple<String, String> modeSwitchTuple = new Tuple<String, String>(((PlanImpl) planDataRun1.getPlan()).getType(), 
					((PlanImpl) planDataRun2.getPlan()).getType());

			DgAnalysisPopulation p = this.classifiedPops.get(modeSwitchTuple);
			if (p == null){
				p = new DgAnalysisPopulation();
				this.classifiedPops.put(modeSwitchTuple, p);
			}
			p.getPersonData().put(d.getPersonId(), d);
		}
	}
	
	public DgAnalysisPopulation getPersonsForModeSwitch(Tuple<PlanImpl.DeprecatedConstants, PlanImpl.DeprecatedConstants> modes) {
		return this.classifiedPops.get(modes);
	}
	
	
}
