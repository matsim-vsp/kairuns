/* *********************************************************************** *
 * project: org.matsim.*
 * JoinedHouseholdsIdentifier.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

package playground.christoph.evacuation.withinday.replanning.identifiers;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.facilities.ActivityFacilities;
import org.matsim.core.api.experimental.facilities.ActivityFacility;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.events.SimulationAfterSimStepEvent;
import org.matsim.core.mobsim.framework.events.SimulationInitializedEvent;
import org.matsim.core.mobsim.framework.listeners.SimulationAfterSimStepListener;
import org.matsim.core.mobsim.framework.listeners.SimulationInitializedListener;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.households.Household;
import org.matsim.households.Households;
import org.matsim.ptproject.qsim.QSim;
import org.matsim.ptproject.qsim.agents.PlanBasedWithinDayAgent;
import org.matsim.ptproject.qsim.comparators.PersonAgentComparator;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringActivityIdentifier;

import playground.christoph.evacuation.analysis.CoordAnalyzer;
import playground.christoph.evacuation.config.EvacuationConfig;
import playground.christoph.evacuation.mobsim.HouseholdPosition;
import playground.christoph.evacuation.mobsim.HouseholdsTracker;
import playground.christoph.evacuation.mobsim.Tracker.Position;
import playground.christoph.evacuation.mobsim.VehiclesTracker;
import playground.christoph.evacuation.withinday.replanning.utils.HouseholdModeAssignment;
import playground.christoph.evacuation.withinday.replanning.utils.ModeAvailabilityChecker;
import playground.christoph.evacuation.withinday.replanning.utils.SelectHouseholdMeetingPoint;

/**
 *  Define which households will relocate to another (secure!) location
 *  at which time.
 *  
 *  Moreover it is decided which transport mode will be used for the evacuation.
 *  If a car is available, it is used. Otherwise the people will walk.
 *  
 *  @author cdobler
 */
public class JoinedHouseholdsIdentifier extends DuringActivityIdentifier implements 
		SimulationInitializedListener, SimulationAfterSimStepListener {

	private static final Logger log = Logger.getLogger(JoinedHouseholdsIdentifier.class);
	
	private final Households households;
	private final ActivityFacilities facilities;
	private final CoordAnalyzer coordAnalyzer;
	private final SelectHouseholdMeetingPoint selectHouseholdMeetingPoint;
	private final ModeAvailabilityChecker modeAvailabilityChecker;
	private final VehiclesTracker vehiclesTracker;
	private final HouseholdsTracker householdsTracker;
	
	private final Map<Id, HouseholdDeparture> householdDepartures;
	private final Map<Id, PlanBasedWithinDayAgent> agentMapping;
	
	/*
	 * Maps to store information for the replanner.
	 * Where does the household meet? Which transport mode does
	 * an agent use? Which agents are drivers?
	 */
	private final Map<Id, Id> householdMeetingPointMapping;
	private final Map<Id, String> transportModeMapping;
	private final Map<Id, Id> driverVehicleMapping;
	
	public JoinedHouseholdsIdentifier(Scenario scenario, SelectHouseholdMeetingPoint selectHouseholdMeetingPoint,
			CoordAnalyzer coordAnalyzer, VehiclesTracker vehiclesTracker, HouseholdsTracker householdsTracker,
			ModeAvailabilityChecker modeAvailabilityChecker) {
		this.households = ((ScenarioImpl) scenario).getHouseholds();
		this.facilities = ((ScenarioImpl) scenario).getActivityFacilities();
		this.selectHouseholdMeetingPoint = selectHouseholdMeetingPoint;
		this.coordAnalyzer = coordAnalyzer;
		this.vehiclesTracker = vehiclesTracker;
		this.householdsTracker = householdsTracker;
		this.modeAvailabilityChecker = modeAvailabilityChecker;
		
		this.agentMapping = new HashMap<Id, PlanBasedWithinDayAgent>();
		this.householdMeetingPointMapping = new ConcurrentHashMap<Id, Id>();
		this.transportModeMapping = new ConcurrentHashMap<Id, String>();
		this.driverVehicleMapping = new ConcurrentHashMap<Id, Id>();
		this.householdDepartures = new HashMap<Id, HouseholdDeparture>();
	}

	@Override
	public Set<PlanBasedWithinDayAgent> getAgentsToReplan(double time) {
		
		/*
		 * Clear maps for every time step.
		 */
		this.householdMeetingPointMapping.clear();
		this.transportModeMapping.clear();
		this.driverVehicleMapping.clear();
		
		Set<PlanBasedWithinDayAgent> set = new TreeSet<PlanBasedWithinDayAgent>(new PersonAgentComparator());
	
		Iterator<Entry<Id, HouseholdDeparture>> iter = this.householdDepartures.entrySet().iterator();
		while(iter.hasNext()) {
			Entry<Id, HouseholdDeparture> entry = iter.next();
			Id householdId = entry.getKey();
			HouseholdDeparture householdDeparture = entry.getValue();
			
			if (householdDeparture.getDepartureTime() == time) {
				
				Id facilityId = householdDeparture.getFacilityId();
				Id meetingPointId = selectHouseholdMeetingPoint.selectNextMeetingPoint(householdId);
				householdMeetingPointMapping.put(householdId, meetingPointId);
				Household household = households.getHouseholds().get(householdId);

				/*
				 * 
				 */
				HouseholdModeAssignment assignment = modeAvailabilityChecker.getHouseholdModeAssignment(household, facilityId);	
				driverVehicleMapping.putAll(assignment.getDriverVehicleMap());
				transportModeMapping.putAll(assignment.getTransportModeMap());
				
				for (Entry<Id, Id> e : assignment.getPassengerVehicleMap().entrySet()) {
					vehiclesTracker.addPassengerToVehicle(e.getKey(), e.getValue());
				}
				
				// finally add agents to replanning set
				for (Id agentId : household.getMemberIds()) {
					set.add(agentMapping.get(agentId));
				}
			}
		}
		
		return set;
	}
	
	/**
	 * @return The mapping between a household and the meeting point that should be used.
	 */
	public Map<Id, Id> getHouseholdMeetingPointMapping() {
		return this.householdMeetingPointMapping;
	}
	
	/**
	 * @return The mapping between an agent and the transportMode that should be used.
	 */
	public Map<Id, String> getTransportModeMapping() {
		return this.transportModeMapping;
	}
	
	/**
	 * @return The mapping between an agent and the vehicle that should be used.
	 */
	public Map<Id, Id> getDriverVehicleMapping() {
		return this.driverVehicleMapping;
	}
	
	/*
	 * Create a mapping between personIds and the agents in the mobsim.
	 * 
	 * Moreover ensure that the joinedHouseholds and householdDeparture
	 * data structures are filled properly. When the simulation starts,
	 * all households are joined at their home facility.
	 */
	@Override
	public void notifySimulationInitialized(SimulationInitializedEvent e) {
		QSim sim = (QSim) e.getQueueSimulation();

		this.agentMapping.clear();
		for (MobsimAgent mobsimAgent : sim.getAgents()) {
			PlanBasedWithinDayAgent withinDayAgent = (PlanBasedWithinDayAgent) mobsimAgent;
			agentMapping.put(withinDayAgent.getId(), withinDayAgent);				
		}
	}
	
	@Override
	public void notifySimulationAfterSimStep(SimulationAfterSimStepEvent e) {
		
		if (e.getSimulationTime() == EvacuationConfig.evacuationTime) {
			this.initiallyCollectHouseholds(e.getSimulationTime());
		} else if (e.getSimulationTime() > EvacuationConfig.evacuationTime) {
			/*
			 * Get a Set of Ids of households which might have changed their state
			 * in the current time step.
			 */
			Set<Id> householdsToUpdate = this.householdsTracker.getHouseholdsToUpdate();
			this.updateHouseholds(householdsToUpdate, e.getSimulationTime());
			
			/*
			 * Check whether a household has missed its departure.
			 */
			Iterator<Entry<Id, HouseholdDeparture>> iter = this.householdDepartures.entrySet().iterator();
			while (iter.hasNext()) {
				Entry<Id, HouseholdDeparture> entry = iter.next();
				Id householdId = entry.getKey();
				HouseholdDeparture householdDeparture = entry.getValue();
				if (householdDeparture.departureTime < e.getSimulationTime()) {
					log.warn("Household missed its departure time! Id " + householdId + ". Time: " + e.getSimulationTime());
					iter.remove();
				}
			}
		}
	}
	
	/*
	 * Start collecting households when the evacuation has started.
	 */
	private void initiallyCollectHouseholds(double time) {
		
		/*
		 * Get a Set of Ids of all households to initally define their departure time.
		 */
		this.householdDepartures.clear();
		
		Map<Id, HouseholdPosition> householdPositions = this.householdsTracker.getHouseholdPositions();
		
		for (Entry<Id, HouseholdPosition> entry : householdPositions.entrySet()) {
			Id householdId = entry.getKey();
			HouseholdPosition householdPosition = entry.getValue();
			
			// if the household is joined
			if (householdPosition.isHouseholdJoined()) {
				
				// if the household is at a facility
				if (householdPosition.getPositionType() == Position.FACILITY) {
					
					//if the household is at its meeting point facility
					if (householdPosition.getPositionId().equals(householdPosition.getMeetingPointFacilityId())) {
						
						/*
						 * If the meeting point is not secure, schedule a departure.
						 * Otherwise ignore the household since it current location
						 * is already secure.
						 */
						Id facilityId = householdPosition.getPositionId();
						ActivityFacility facility = this.facilities.getFacilities().get(facilityId);
						boolean facilityIsSecure = !this.coordAnalyzer.isFacilityAffected(facility);
						if (!facilityIsSecure) {
							HouseholdDeparture householdDeparture = createHouseholdDeparture(time, householdId, householdPosition.getPositionId());
							this.householdDepartures.put(householdId, householdDeparture);
							
							/*
							 * The initial meeting points have been selected by the SelectHouseholdMeetingPoint class.
							 */
							this.selectHouseholdMeetingPoint.getMeetingPoint(householdId);
						}
					}
				}
				
			}
		}
	}
	
	private void updateHouseholds(Set<Id> householdsToUpdate, double time) {
		
		for (Id id : householdsToUpdate) {
			HouseholdPosition householdPosition = householdsTracker.getHouseholdPosition(id);
			HouseholdDeparture householdDeparture = this.householdDepartures.get(id);
			
			/*
			 * Check whether the household is joined.
			 */
			boolean isJoined = householdPosition.isHouseholdJoined();
			boolean wasJoined = (householdDeparture != null);
			if (isJoined) {
				/*
				 * Check whether the household is in a facility.
				 */
				Position positionType = householdPosition.getPositionType();
				if (positionType == Position.FACILITY) {
					Id facilityId = householdPosition.getPositionId();
					Id meetingPointId = householdPosition.getMeetingPointFacilityId();
					
					/*
					 * Check whether the household is at its meeting facility.
					 */
					if (meetingPointId.equals(facilityId)) {
						
						/*
						 * The household is at its meeting point. If no departure has
						 * been scheduled so far and the facility is not secure, schedule one.
						 */
						if (householdDeparture == null) {
							
							ActivityFacility facility = this.facilities.getFacilities().get(facilityId);
							boolean facilityIsSecure = !this.coordAnalyzer.isFacilityAffected(facility);
							if (!facilityIsSecure) {
								// ... and schedule the household's departure.
								householdDeparture = createHouseholdDeparture(time, id, meetingPointId);
								this.householdDepartures.put(id, householdDeparture);
							}
						}
					} 
					
					/*
					 * The household is joined at a facility which is not its
					 * meeting facility. Ensure that no departure is scheduled
					 * and create a warn message if the evacuation has already
					 * started. 
					 * TODO: check whether this could be a valid state.
					 */
					else {
						this.householdDepartures.remove(id);
						if (time > EvacuationConfig.evacuationTime && !facilityId.toString().contains("pickup")) {
							log.warn("Household is joined at a facility which is not its meeting facility. Id: " + id);							
						}
					}
				}
				
				/*
				 * The household is joined but not at a facility. Therefore ensure
				 * that there is no departure scheduled.
				 */
				else {				
					this.householdDepartures.remove(id);
				}
			}
			
			/*
			 * The household is not joined. Therefore ensure that there is no departure
			 * scheduled for for that household.
			 */
			else {
				this.householdDepartures.remove(id);
				
				/*
				 * If the household was joined and the evacuation has already started.
				 * We do not expect to find a departure before it was scheduled.
				 */
				if (wasJoined && time < householdDeparture.departureTime) {
					log.warn("Household has left its meeting point before scheduled departure. Id " + id);
				}
			}
		}
	}
	
	private HouseholdDeparture createHouseholdDeparture(double time, Id householdId, Id facilityId) {
		
		// TODO: use a function to estimate the departure time
		HouseholdDeparture householdDeparture = new HouseholdDeparture(householdId, facilityId, time + 600);
		
		return householdDeparture;
	}
	
	/*
	 * A datastructure to store households and their planned departures.
	 */
	private static class HouseholdDeparture {
		
		private final Id householdId;
		private final Id facilityId;
		private final double departureTime;
		
		public HouseholdDeparture(Id householdId, Id facilityId, double departureTime) {
			this.householdId = householdId;
			this.facilityId = facilityId;
			this.departureTime = departureTime;
		}
		
		public Id getHouseholdId() {
			return this.householdId;
		}
		
		public Id getFacilityId() {
			return this.facilityId;
		}
		
		public double getDepartureTime() {
			return this.departureTime;
		}
		
		@Override
		public boolean equals(Object o) {
			if (o instanceof HouseholdDeparture) {
				return ((HouseholdDeparture) o).getHouseholdId().equals(householdId);
			}
			return false;
		}
	}
	

}