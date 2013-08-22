/* *********************************************************************** *
 * project: org.matsim.*
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
package playground.droeder.extendPtTutorial;

import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.SubtourModeChoiceConfigGroup;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleReaderV1;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleWriterV1;
import org.matsim.vehicles.VehiclesFactory;

/**
 * @author droeder
 *
 */
class ExtendPtTutorial {
	
	
	private final static String DIR = "E:/sandbox/org.matsim/examples/pt-tutorial/";

	/**
	 * extends the default pt-tutorial (org.matsim/examples/pt-tutorial/) with a 
	 * busline. THus, agents will switch lines. Furthermore the "TimeAllocationMutator"
	 * and the "TripSubtourModeChoice" are added as additional strategies. For 
	 * "TripSubtourModeChoice" TransportMode.ride is added as further option.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		Scenario sc = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		sc.getConfig().scenario().setUseTransit(true);
		sc.getConfig().scenario().setUseVehicles(true);
		new MatsimNetworkReader(sc).readFile(DIR + "multimodalnetwork.xml");
		new TransitScheduleReader(sc).readFile(DIR + "transitschedule.xml");
		new VehicleReaderV1(((ScenarioImpl) sc).getVehicles()).readFile(DIR + "transitVehicles.xml");
		
		List<Id> links = new ArrayList<Id>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(new IdImpl("1112"));
			add(new IdImpl("1213"));
			add(new IdImpl("1323"));
			add(new IdImpl("2324"));
			add(new IdImpl("2434"));
			add(new IdImpl("3444"));
			add(new IdImpl("4434"));
			add(new IdImpl("3424"));
			add(new IdImpl("2423"));
			add(new IdImpl("2313"));
			add(new IdImpl("1312"));
			add(new IdImpl("1211"));
		}};
		
		
		TransitSchedule schedule = sc.getTransitSchedule();
		TransitScheduleFactory fac = schedule.getFactory();
		Link l;
		TransitStopFacility f;
		List<TransitRouteStop> stops = new ArrayList<TransitRouteStop>();
		NetworkRoute nRoute = new LinkNetworkRouteImpl(links.get(0), links.get(0));
		nRoute.setLinkIds(nRoute.getStartLinkId(), links.subList(1, links.size()), nRoute.getEndLinkId());
		Double delay = 0.;
		for(Id linkId: links){
			l = sc.getNetwork().getLinks().get(linkId);
			f = fac.createTransitStopFacility(linkId, l.getToNode().getCoord(), false);
			f.setLinkId(linkId);
			schedule.addStopFacility(f);
			delay += l.getLength() / (l.getFreespeed() * 0.8);
			stops.add(fac.createTransitRouteStop(f, delay, delay + 10));
		}
		
		TransitLine line = fac.createTransitLine(sc.createId("busline"));
		TransitRoute route = fac.createTransitRoute(sc.createId("busroute"), nRoute, stops, "bus");
		Departure d;
		Vehicle v;
		VehiclesFactory vFac = ((ScenarioImpl) sc).getVehicles().getFactory();
		VehicleType type = vFac.createVehicleType(new IdImpl("bus"));
		((ScenarioImpl) sc).getVehicles().getVehicleTypes().put(type.getId(), type);
		VehicleCapacity vCap = vFac.createVehicleCapacity();
		vCap.setSeats(100);
		type.setCapacity(vCap);
		
		for(int i = 0; i< 86400; i+= 600){
			d = fac.createDeparture(sc.createId(String.valueOf(i)), i);
			v = vFac.createVehicle(sc.createId(String.valueOf(i)), type);
			((ScenarioImpl) sc).getVehicles().getVehicles().put(v.getId(), v);
			d.setVehicleId(v.getId());
			route.addDeparture(d);
		}
		line.addRoute(route);
		schedule.addTransitLine(line);
		new TransitScheduleWriter(schedule).writeFileV1(DIR + "scheduleWithBus.xml.gz");
		new VehicleWriterV1(((ScenarioImpl) sc).getVehicles()).writeFile(DIR + "vehiclesWithBus.xml.gz");
		
		Config c = ConfigUtils.loadConfig(DIR + "config.xml");
		c.transit().setTransitScheduleFile(DIR + "scheduleWithBus.xml.gz");
		c.transit().setVehiclesFile(DIR + "vehiclesWithBus.xml.gz");
		c.network().setInputFile(DIR + "multimodalnetwork.xml");
		c.plans().setInputFile(DIR + "population.xml");
		c.controler().setOutputDirectory("../../org.matsim/output/pt-tutorial/");
		c.controler().setLastIteration(10);
		c.controler().setWriteEventsInterval(10);
		c.controler().setWritePlansInterval(10);

		c.strategy().setFractionOfIterationsToDisableInnovation(0.8);
		c.strategy().addParam("Module_3", "TimeAllocationMutator");
		c.strategy().addParam("Module_4", "TripSubtourModeChoice");
		String[] modes = ((SubtourModeChoiceConfigGroup) c.getModule(SubtourModeChoiceConfigGroup.GROUP_NAME)).getModes();
		String[] modes2 =  new String[modes.length +1];
		for(int i = 0; i < modes.length; i++){
			modes2[i] = modes[i];
		}
		modes2[modes.length] = TransportMode.ride;
		((SubtourModeChoiceConfigGroup) c.getModule(SubtourModeChoiceConfigGroup.GROUP_NAME)).setModes(modes2);
		
		new ConfigWriter(c).write(DIR + "configExtended.xml");
		
	}	
	
}