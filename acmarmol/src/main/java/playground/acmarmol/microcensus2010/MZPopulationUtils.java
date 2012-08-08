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

package playground.acmarmol.microcensus2010;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.core.utils.collections.Tuple;

/**
* 
* Helper class to filter the population
* 
*
* @author acmarmol
* 
*/

public class MZPopulationUtils {
	
//////////////////////////////////////////////////////////////////////
//member variables
//////////////////////////////////////////////////////////////////////

private static final String HOME = "home";	
private static final String WORK = "work";	
	

//////////////////////////////////////////////////////////////////////
//public methods
//////////////////////////////////////////////////////////////////////	

	public static void removePlans(final Population population, final Set<Id> ids) {
		for (Id id : ids) {
			Person p = population.getPersons().remove(id);
			if (p == null) { Gbl.errorMsg("pid="+id+": id not found in the plans DB!"); }
		}
	}



//////////////////////////////////////////////////////////////////////

	public static Set<Id> identifyPlansWithoutActivities(final Population population) {
		Set<Id> ids = new HashSet<Id>();
		for (Person person : population.getPersons().values()) {
			if(person.getSelectedPlan()==null){
			ids.add(person.getId());}
		}
		return ids;
	}


//////////////////////////////////////////////////////////////////////
	
	public static Set<Id> identifyNonHomeBasedPlans(final Population population) {
		Set<Id> ids = new HashSet<Id>();
		for (Person p : population.getPersons().values()) {
			Plan plan = p.getSelectedPlan();
			ActivityImpl last = (ActivityImpl)plan.getPlanElements().get(plan.getPlanElements().size()-1);
			if (!last.getType().equals(HOME)) { ids.add(p.getId()); }
		}
		return ids;
	}

//////////////////////////////////////////////////////////////////////

	public static Set<Id> identifyPlansWithNegCoords(final Population population) {
		Set<Id> ids = new HashSet<Id>();
		for (Person person : population.getPersons().values()) {
			Plan plan = person.getSelectedPlan();
			if(plan!=null){ //avoid persons without activities
				for (PlanElement pe : plan.getPlanElements()) {
					if (pe instanceof ActivityImpl) {
					ActivityImpl act = (ActivityImpl) pe;
					if ((act.getCoord().getX()<0) || (act.getCoord().getY()<0)) { ids.add(person.getId()); }
					}
				}
			}
		}
		return ids;
	}	

//////////////////////////////////////////////////////////////////////

	public static Set<Id> identifyPlansWithTooLongWalkTrips(final Population population) {
	Set<Id> ids = new HashSet<Id>();
	for (Person person : population.getPersons().values()) {
		Plan plan = person.getSelectedPlan();
		if(plan!=null){ //avoid persons without activities
			for (PlanElement pe : plan.getPlanElements()) {
				if (pe instanceof Leg) {
					Leg leg = (Leg) pe;
					if ((leg.getMode().equals(TransportMode.walk))&&(leg.getRoute().getDistance()>10000.0)) {ids.add(person.getId()); }
				}
			}
		}
	}
	return ids;
}	
	
//////////////////////////////////////////////////////////////////////
	
	public static void setHomeLocations(final Population population, final ObjectAttributes householdAttributes, final ObjectAttributes populationAttributes) {
		int counter = 0;
		for (Person person : population.getPersons().values()) {
			Plan plan = person.getSelectedPlan();
			String hhnr = (String) populationAttributes.getAttribute(person.getId().toString(), "household number");
			CoordImpl homeCoord = (CoordImpl)householdAttributes.getAttribute(hhnr, "coord");

			if(plan!=null){ //avoid persons without activities
				for (int i=0; i<plan.getPlanElements().size(); i=i+2) {
					Activity act = (ActivityImpl)plan.getPlanElements().get(i);
					if ((act.getCoord().getX() == homeCoord.getX()) && (act.getCoord().getY() == homeCoord.getY())) {
						if (!act.getType().equals(HOME)) {
							act.setType(HOME);
							counter++;
	//						System.out.println("        pid=" + p.getId() + "; act_nr=" + (i/2) + ": set type to '"+HOME+"'");
						}
					}
				}
			}
		}
		System.out.println("      Number of activities set to home: " + counter);
	}

//////////////////////////////////////////////////////////////////////	
	
	public static void setWorkLocations(final Population population, final ObjectAttributes populationAttributes) {
		int counter = 0;
		for (Person person : population.getPersons().values()) {
		
			if(((PersonImpl) person).isEmployed()){
				
				Plan plan = person.getSelectedPlan();
				CoordImpl workCoord = (CoordImpl)populationAttributes.getAttribute(person.getId().toString(), "work: location coord");
				
				if(plan!=null){ //avoid persons without activities
					for (int i=0; i<plan.getPlanElements().size(); i=i+2) {
						Activity act = (ActivityImpl)plan.getPlanElements().get(i);
						if ((act.getCoord().getX() == workCoord.getX()) && (act.getCoord().getY() == workCoord.getY())) {
							if (!act.getType().equals(WORK)) {
							act.setType(WORK);
							counter++;
							//						System.out.println("        pid=" + p.getId() + "; act_nr=" + (i/2) + ": set type to '"+HOME+"'");
							}
						}
					}
				}
			}
		}	
		System.out.println("      Number of activities set to work: " + counter);
	}

//////////////////////////////////////////////////////////////////////
	

public static Set<Id> identifyPlansWithUndefinedNegCoords(final Population population) {
	Set<Id> ids = new HashSet<Id>();
		for (Person person : population.getPersons().values()) {
			Plan plan = person.getSelectedPlan();
			if(plan!=null){ //avoid persons without activities
					for (PlanElement pe : plan.getPlanElements()) {
					if (pe instanceof ActivityImpl) {
					ActivityImpl act = (ActivityImpl) pe;
					System.out.println(person.getId());
						if (((act.getCoord().getX() == -97) || (act.getCoord().getY() == -97))) {
							ids.add(person.getId());
							}
					}
				}
			}
		}
	return ids;
}	
		
//////////////////////////////////////////////////////////////////////

	public static Set<Id> identifyPlansWithoutBestPrecision(final Population population) {
	Set<Id> ids = new HashSet<Id>();
	for (Person person : population.getPersons().values()) {	
		Plan plan = person.getSelectedPlan();
		if(plan!=null){ //avoid persons without activities
			for (PlanElement pe : plan.getPlanElements()) {
				if (pe instanceof Leg) {
					Leg leg = (Leg) pe;
					if (leg.getRoute().getDistance() == -99000) { ids.add(person.getId()); }
				}
			}
		}
	}
	return ids;
}	
	
	
	
//////////////////////////////////////////////////////////////////////

	@SuppressWarnings("deprecation")
	public static void HandleBorderCrossingTrips(final Population population, final ObjectAttributes wegeAttributes, Set<Id> border_crossing_wids) {	
		
	
	HashMap<Id, Tuple<Integer, PlanElement>> toAdd =  new HashMap<Id, Tuple<Integer, PlanElement>>();
	HashMap<Id,ArrayList<PlanElement>> toRemove = 	new HashMap<Id,ArrayList<PlanElement>>();
	Set<Id> overnigth_pids = new HashSet<Id>();
		
	for(Id wid: border_crossing_wids){
		
		ArrayList<PlanElement> elementsToRemove = null;
		Id pid = new IdImpl(wid.toString().substring(0, wid.toString().indexOf('-')).trim());
		overnigth_pids.add(pid);

		int legNumber = Integer.parseInt(wid.toString().substring(wid.toString().indexOf('-')+1));
		//maybe legNumber is not the best way to index, because some planElements are deleted (the ones outside Switzerland)
		// to overcome this issue, all plan elements are stored first in toRemove, and only in the end are deleted.

		List<PlanElement> planElements = population.getPersons().get(pid).getSelectedPlan().getPlanElements();
		LegImpl leg = (LegImpl) planElements.get(2*legNumber-1);
		ActivityImpl nextActivity = (ActivityImpl) planElements.get(2*legNumber);
		ActivityImpl previousActivity = (ActivityImpl) planElements.get(2*legNumber-2);
		
	
		//HANDLING OF TRIPS GOING OUT OF SWITZERLAND
		if(wegeAttributes.getAttribute(wid.toString(), "start land").equals("8100") &&  !wegeAttributes.getAttribute(wid.toString(), "end land").equals("8100")){
		
			boolean cont = true;
			int etappen = 1;
			int curr_mode = Integer.MAX_VALUE;
			String type = nextActivity.getType();
			
			//if goes out via plane, specify next activity as airport, otherwise as border
			if(leg.getMode().equals("plane")){
				nextActivity.setType("airport: ".concat(type));
			}else {
				nextActivity.setType("border: ".concat(type));
			}
									
			//modify leg and replace with information from before the border crossing (in MZ2010, a new etappe always start at border crossing!)
						
			while(cont){
				if(etappen > (Integer) wegeAttributes.getAttribute(wid.toString(), "number of etappen")){
					Gbl.errorMsg("This should never happen!  Wege id ("+wid+") doesn't cross border!");
					}
			
				Etappe etappe = (Etappe) wegeAttributes.getAttribute(wid.toString(), "etappe".concat(String.valueOf(etappen)));
				
				if(etappe.getStartCountry().equals("8100") && etappe.getEndCountry().equals("8100")){
					if(etappe.getModeInteger()<curr_mode){// && (leg.getMode().equals("plane")? !etappe.getMode().equals("plane"):true)){
						curr_mode = etappe.getModeInteger();
						leg.setMode(etappe.getMode());
					}
						leg.setArrivalTime(etappe.getArrivalTime());
						leg.setTravelTime(etappe.getArrivalTime()-leg.getDepartureTime());
						nextActivity.setCoord(etappe.getEndCoord());
						nextActivity.setStartTime(leg.getArrivalTime());
														
					
				}else{ cont = false;}
				
				
				etappen++;
			}
			
		
		//HANDLING OF TRIPS ENTERING SWITZERLAND
		}else if(!wegeAttributes.getAttribute(wid.toString(), "start land").equals("8100") &&  wegeAttributes.getAttribute(wid.toString(), "end land").equals("8100")){
			
			boolean immediate_return = false;
			if(!overnigth_pids.contains(pid)){overnigth_pids.remove(pid);}
			String type = previousActivity.getType();
			//if goes in via plane, specify previous activity as airport, otherwise as border
			if(leg.getMode().equals("plane")){
				if(!previousActivity.getType().contains("airport") && !previousActivity.getType().contains("border")){
					previousActivity.setType("airport: ".concat(type));
				}else immediate_return = true;
			}else{
				if(!previousActivity.getType().contains("airport") && !previousActivity.getType().contains("border")){
					previousActivity.setType("border: ".concat(type));
				}else immediate_return = true;
			}
			
			
			int curr_mode = Integer.MAX_VALUE;
			Coord curr_start_coord = null;
			boolean start = false;
			
			for(int i=1; i<= (Integer) wegeAttributes.getAttribute(wid.toString(), "number of etappen"); i++){
							
				Etappe etappe = (Etappe) wegeAttributes.getAttribute(wid.toString(), "etappe".concat(String.valueOf(i)));
				
				if(!etappe.getStartCountry().equals("8100") && etappe.getEndCountry().equals("8100")){
					leg.setDepartureTime(etappe.getDepartureTime());
					leg.setTravelTime(etappe.getArrivalTime()-leg.getDepartureTime());
					previousActivity.setEndTime(leg.getDepartureTime());
					curr_start_coord = (etappe.getStartCoord());
					start = true;
				}
				else if(start){
					if(etappe.getModeInteger()<curr_mode){
					curr_mode = etappe.getModeInteger();
					leg.setMode(etappe.getMode());
					}
					leg.setArrivalTime(etappe.getArrivalTime());
					leg.setTravelTime(etappe.getArrivalTime()-leg.getDepartureTime());
					curr_start_coord = (etappe.getStartCoord());
				}
			}	
			
			
			//identify if out and in - border crossings are the same, otherwise it necessary to create new virtual activity. 
			boolean different_border_crossing = false;
			if(immediate_return && !curr_start_coord.equals(previousActivity.getCoord())){
				different_border_crossing = true;				
			}else{
				previousActivity.setCoord(curr_start_coord);
			}
			
		
			
				int index = planElements.indexOf(previousActivity);
				String crossing_type = previousActivity.getType().substring(0, previousActivity.getType().indexOf(':')).trim();
								
				if(toRemove.containsKey(pid)){
					elementsToRemove = toRemove.get(pid);
				}
				else{
					elementsToRemove = new ArrayList<PlanElement>(10);
				}
								
							
				if(index>0){
					boolean cont = true;
					if(immediate_return){
					//immediate return, only do something if different border crossing is used
					// if different border crossing is used, a new virtual activity is created with a connecting teleport leg
						if(different_border_crossing){
							LegImpl teleportLeg = new LegImpl(leg);
							teleportLeg.setDepartureTime(previousActivity.getEndTime());
							teleportLeg.setArrivalTime(previousActivity.getEndTime());
							teleportLeg.setTravelTime(0);
							teleportLeg.setMode("abroad: teleport");
							toAdd.put(pid, new Tuple<Integer, PlanElement>(index+1, teleportLeg));      
							
							ActivityImpl virtualActivity = new ActivityImpl(previousActivity);
							virtualActivity.setStartTime(previousActivity.getEndTime());
							virtualActivity.setCoord(curr_start_coord);
							toAdd.put(pid, new Tuple<Integer, PlanElement>(index+2, teleportLeg));  
							
							
						}
						
						
					}else{
					//not immediate return, therefore there are activities and legs outside switzerland that need to be eliminated
					//remove previous plan elements (executed outside switzerland) until "airport" or "border" activity is found 
					// Possibilities:	
					//1) all previous activities and legs are outside switzerland -> remove all
					//2) there's a previous "airport" or "border" activity, thus the person left and entered switzerland on the same plan. 
					//	2.1) if the coords of these two match (same airport or border pass) merge both activities and fix start and end times accordingly
					//  2.2) if the coords don't match, create intermediate leg with mode "teleport" two keep consistency on plan.
						
							for (int j=index-1; cont && j>=0 ;j--) {
								
								boolean delete = true;
								PlanElement pe = planElements.get(j);
								if(pe instanceof Activity){
									Activity activity = (Activity) pe;
									if(activity.getType().contains("airport") || activity.getType().contains("border")){
										
										//going in to switzerland via the same path that went out -> merge activites  (2.1)
										if(activity.getType().contains(crossing_type) && activity.getCoord().equals(previousActivity.getCoord())){ 
										previousActivity.setStartTime(activity.getStartTime());
										cont = false;
										
										}else{
										//going in to switzerland via other path -> create "teleport" leg  (2.2)
										LegImpl teleportLeg = new LegImpl(leg);
										teleportLeg.setDepartureTime(activity.getEndTime());
										teleportLeg.setArrivalTime(previousActivity.getStartTime());
										teleportLeg.setMode("abroad: teleport");
										toAdd.put(pid, new Tuple<Integer, PlanElement>(index, teleportLeg));
										delete = false; 
										cont = false;
											
										}
									}
								}
								if(delete){elementsToRemove.add(pe);}
							
							}
						}
					
				}
			
				toRemove.put(pid, elementsToRemove);
		}// end handling out border
		
		
	 }//end loop for all wids
	
	addPlanElements(population,toAdd);
	removePlanElements(population, toRemove);
	
	
	
	}//end method

//////////////////////////////////////////////////////////////////////
	
	private static void addPlanElements(Population population,  HashMap<Id, Tuple<Integer, PlanElement>> toAdd){

		for(Id id: toAdd.keySet()){

			Person person = population.getPersons().get(id);
			person.getSelectedPlan().getPlanElements().add(toAdd.get(id).getFirst(), toAdd.get(id).getSecond());
		
		}
	
	}
	
//////////////////////////////////////////////////////////////////////
	
	private static void removePlanElements(Population population,  HashMap<Id,ArrayList<PlanElement>> toRemove){
	
		for(Id id: toRemove.keySet()){
			
			Person person = population.getPersons().get(id);
			person.getSelectedPlan().getPlanElements().removeAll(toRemove.get(id));
			
		}
		
	}
	
	
//////////////////////////////////////////////////////////////////////

	public static Set<Id> identifyPlansOutOfSwizerland(final Population population, final ObjectAttributes wegeAttributes) {
		Set<Id> ids = new HashSet<Id>();
		for (Person person : population.getPersons().values()) {	
			Plan plan = person.getSelectedPlan();
			if(plan!=null){ //avoid persons without activities
				boolean out = true;
				int legCounter = 0;
				for (PlanElement pe : plan.getPlanElements()) {
					if (pe instanceof Leg) {
						legCounter++;
						String sland = (String) wegeAttributes.getAttribute(person.getId().toString().concat("-").concat(String.valueOf(legCounter)),"start land");
						String zland = (String) wegeAttributes.getAttribute(person.getId().toString().concat("-").concat(String.valueOf(legCounter)),"end land");
						if(sland.equals("8100") || zland.equals("8100")){
							out = false;
							break;
						}
					}
				}
				if(out){ids.add(person.getId());}
				
			}
		}
		return ids;
	}	


//////////////////////////////////////////////////////////////////////

	public static ArrayList<Set<Id>> identifyCrossBorderWeges(final Population population, final ObjectAttributes wegeAttributes) {
		Set<Id> wids = new LinkedHashSet<Id>();
		Set<Id> pids = new LinkedHashSet<Id>();
		
		for (Person person : population.getPersons().values()) {	
			
			Plan plan = person.getSelectedPlan();
			if(plan!=null){ //avoid persons without activities
				int legCounter = 0;
				for (PlanElement pe : plan.getPlanElements()) {
					if (pe instanceof Leg) {
						Leg leg = (Leg) pe;
						legCounter++;
						String wid = person.getId().toString().concat("-").concat(String.valueOf(legCounter));
						String sland = (String) wegeAttributes.getAttribute(wid,"start land");
						String zland = (String) wegeAttributes.getAttribute(wid,"end land");
						if((!sland.equals("8100") ^ !zland.equals("8100"))){
						 wids.add(new IdImpl(wid));
						 pids.add(person.getId());
							
					}
				}
			}
			
			
			}
		}
		ArrayList<Set<Id>> ids = new ArrayList<Set<Id>>();
		ids.add(pids);
		ids.add(wids);
		return ids;
	}	

//////////////////////////////////////////////////////////////////////

	public static Set<Id> removeWegesOutsideSwitzerland(final Population population, final Set<Id> ids, ObjectAttributes wegeAttributes) {
		
		Set<PlanElement> elements = new LinkedHashSet<PlanElement>();
		
		for (Id id : ids) {
			int legCounter = 0;
			Person person = population.getPersons().get(id);
			Plan plan = person.getSelectedPlan();
			for (int i=0; i<plan.getPlanElements().size();i++) {
				
					PlanElement pe = plan.getPlanElements().get(i);
					if (pe instanceof Leg) {
						legCounter++;
						String wid = person.getId().toString().concat("-").concat(String.valueOf(legCounter));
						
						Leg leg = (Leg) pe;
						Activity act = (Activity) plan.getPlanElements().get(i+1);
						
						System.out.println(wid);
						
						String sland = (String) wegeAttributes.getAttribute(wid,"start land");
						String zland = (String) wegeAttributes.getAttribute(wid,"end land");
						if((!sland.equals("8100") && !zland.equals("8100"))){
						elements.add((PlanElement) leg);
						if(act.getType().contains("airport")){
							for(int j=i-1;j==0;j-=2){
								PlanElement pel = plan.getPlanElements().get(j);
							if(pel instanceof Activity){
								Activity activity = (Activity) pel;
								if(activity.getType().contains("aiport")){activity.setEndTime(act.getEndTime());}
							}
							}
						}
						elements.add((PlanElement) act);
						}
						
					}
			}
			
			plan.getPlanElements().removeAll(elements);
		}
		return ids;
	}	
	
	
	
}
