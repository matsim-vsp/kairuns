/* *********************************************************************** *
 * project: org.matsim.*
 * CharyparNagelScoringFunctionFactory.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package playground.mfeil;

import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;

/**
 * A factory to create {@link JohScoringFunction}s.
 *
 * @author mfeil
 */
public class JohScoringFunctionEstimationFactory implements ScoringFunctionFactory {
	
	Network network;
	
	public JohScoringFunctionEstimationFactory (Network network){
		this.network = network;
	}
		
	public ScoringFunction getNewScoringFunction(final Plan plan) {
		return new JohScoringFunctionEstimation(plan, network);
	}

}
