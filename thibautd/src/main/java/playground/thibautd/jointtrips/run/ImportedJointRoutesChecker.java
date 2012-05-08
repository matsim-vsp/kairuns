/* *********************************************************************** *
 * project: org.matsim.*
 * ImportedJointRoutesChecker.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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
package playground.thibautd.jointtrips.run;

import java.util.Iterator;
import java.util.List;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.utils.misc.Time;
import org.matsim.population.algorithms.AbstractPersonAlgorithm;

import playground.thibautd.jointtrips.population.DriverRoute;
import playground.thibautd.router.ActivityWrapperFacility;
import playground.thibautd.router.TripRouter;

/**
 * Checks driver routes, in case only passengers were provided in the plan file
 * @author thibautd
 */
public class ImportedJointRoutesChecker extends AbstractPersonAlgorithm {
	private final TripRouter router;

	public ImportedJointRoutesChecker(final TripRouter router) {
		this.router = router;
	}

	@Override
	public void run(final Person person) {
		for (Plan plan : person.getPlans()) {
			Iterator<PlanElement> pes = plan.getPlanElements().iterator();

			Activity origin = (Activity) pes.next();
			double now = 0;
			while (pes.hasNext()) {
				// FIXME: relies on the assumption of strict alternance leg/act
				Leg l = (Leg) pes.next();
				Activity dest = (Activity) pes.next();

				now = updateTime( now , origin );
				if (l.getRoute() != null && l.getRoute() instanceof DriverRoute) {
					List<? extends PlanElement> trip =
						router.calcRoute(
								l.getMode(),
								new ActivityWrapperFacility( origin ),
								new ActivityWrapperFacility( dest ),
								now,
								person);

					if (trip.size() != 1) {
						throw new RuntimeException( "unexpected trip length "+trip.size()+" for "+trip+" for mode "+l.getMode());
					}

					DriverRoute newRoute = (DriverRoute) ((Leg) trip.get( 0 )).getRoute();
					newRoute.setPassengerIds( ((DriverRoute) l.getRoute()).getPassengersIds() );
					l.setRoute( newRoute );
				}

				now = updateTime( now , l );
				origin = dest;
			}
		}
	}

	private static double updateTime(
			final double currTime,
			final Activity act) {
		double e = act.getEndTime();
		double d = act.getMaximumDuration();
		return e != Time.UNDEFINED_TIME ? e :
			currTime + ( d != Time.UNDEFINED_TIME ? d : 0 );
	}

	private static double updateTime(
			final double currTime,
			final Leg leg) {
		double tt = leg.getTravelTime();
		return tt != Time.UNDEFINED_TIME ? currTime + tt : currTime;
	}
}

