package ch.ethz.matsim.ier.emulator;

import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Time;
import org.matsim.vehicles.Vehicle;

import com.google.inject.Inject;

/**
 * This is an example implementation of SimulationEmulator. See the interface
 * for more information and comments inline for an explanation what is done
 * here.
 * 
 * @author shoerl
 */
public class FirstSimpleSimulationEmulator implements SimulationEmulator {

	private Network network;
	private TravelTime travelTime;

	@Inject
	public FirstSimpleSimulationEmulator(MatsimServices services) {
		this.network = services.getScenario().getNetwork();
		this.travelTime = services.getLinkTravelTimes();
	}

	@Override // TODO synchronized!
	public synchronized void emulate(Person person, Plan plan, EventsManager eventsManager) {
		List<? extends PlanElement> elements = plan.getPlanElements();

		double time = 0.0;

		for (int i = 0; i < elements.size(); i++) {
			PlanElement element = elements.get(i);

			boolean isFirstElement = i == 0;
			boolean isLastElement = i == elements.size() - 1;

			if (element instanceof Activity) {
				/*
				 * Simulating activities is quite straight-forward. Either they have an end time
				 * set or they have a maximum duration. It only gets a bit more complicated for
				 * public transit. In that case we would need to check if the next leg is a
				 * public transit trip and adjust the end time here according to the schedule.
				 */

				Activity activity = (Activity) element;

				if (!isFirstElement) {
					eventsManager.processEvent(new ActivityStartEvent(time, person.getId(), activity.getLinkId(),
							activity.getFacilityId(), activity.getType()));
				}

				if (!Time.isUndefinedTime(activity.getEndTime())) {
					time = Math.max(time, activity.getEndTime());
				} else {
					time += activity.getMaximumDuration();
				}

				if (!isLastElement) {
					eventsManager.processEvent(new ActivityEndEvent(time, person.getId(), activity.getLinkId(),
							activity.getFacilityId(), activity.getType()));
				}
			} else if (element instanceof Leg) {
				/*
				 * Same is true for legs. We need to generate departure and arrival events and
				 * everything in between.
				 */

				Leg leg = (Leg) element;

				Activity previousActivity = (Activity) elements.get(i - 1);
				Activity followingActivity = (Activity) elements.get(i + 1);

				eventsManager.processEvent(
						new PersonDepartureEvent(time, person.getId(), previousActivity.getLinkId(), leg.getMode()));

				// ============================================================

				Route route = leg.getRoute();

				if (route instanceof NetworkRoute) {
					
					
					NetworkRoute networkRoute = (NetworkRoute) route;
					if (!networkRoute.getStartLinkId().equals(networkRoute.getEndLinkId())) {

						Id<Vehicle> vehicleId = Id.createVehicleId(person.getId());
						
						double routeTime_s = time;
						Link link = this.network.getLinks().get(networkRoute.getStartLinkId());
						eventsManager.processEvent(new VehicleEntersTrafficEvent(routeTime_s, person.getId(),
								link.getId(), vehicleId, leg.getMode(), 0.0));
						routeTime_s += this.travelTime.getLinkTravelTime(link, routeTime_s, person, null);
						eventsManager.processEvent(new LinkLeaveEvent(routeTime_s, vehicleId, link.getId()));

						for (Id<Link> linkId : networkRoute.getLinkIds()) {
							link = this.network.getLinks().get(linkId);
							eventsManager.processEvent(new LinkEnterEvent(routeTime_s, vehicleId, link.getId()));
							routeTime_s += this.travelTime.getLinkTravelTime(link, routeTime_s, person, null);
							eventsManager.processEvent(new LinkLeaveEvent(routeTime_s, vehicleId, link.getId()));
						}

						// TODO first a LinkEnterEvent?
						eventsManager.processEvent(new VehicleLeavesTrafficEvent(routeTime_s, person.getId(),
								networkRoute.getEndLinkId(), vehicleId, leg.getMode(), 0.0));
					}
					
				} else {

					// Here we currently only add a teleportation event. For vehicular modes like
					// car
					// or pt we would probably like to add more events here. (Like
					// PersonEntersVehicle). The important question is which events do we actually
					// need to create to make the scoring consistent.

					double travelTime = leg.getTravelTime();
					if (Time.isUndefinedTime(travelTime)) {
						// For some reason some legs don't have a proper travel time in equil scenario.
						// Not sure why, but I don't think it has to do with this whole setup here.
						travelTime = 3600.0;
					}
					eventsManager.processEvent(
							new TeleportationArrivalEvent(time, person.getId(), leg.getRoute().getDistance()));
				}

				
				time += leg.getTravelTime();
				eventsManager.processEvent(
						new PersonArrivalEvent(time, person.getId(), followingActivity.getLinkId(), leg.getMode()));
			}
		}
	}
}
