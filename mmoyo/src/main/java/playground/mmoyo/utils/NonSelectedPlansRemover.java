/* *********************************************************************** *
 * project: org.matsim.*
 * NonSelectedPlansRemover.java
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

package playground.mmoyo.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.network.NetworkImpl;
 /**
  *filters all plans leaving only the selected ones
  * @author manuel
  */
public class NonSelectedPlansRemover {

	public void run(Population population){
		for (Person person : population.getPersons().values()){
			Collection <Plan> selectedPlanList = new ArrayList<Plan>();
			selectedPlanList.add(person.getSelectedPlan());
			person.getPlans().retainAll(selectedPlanList);
		}
	}

	public static void main(String[] args) {
		String PopFilePath;
		String NetFilePath;
		
		if (args.length==2){
			PopFilePath = args[0];
			NetFilePath = args[1];
		}else{
			PopFilePath = "../path";
			NetFilePath = "../path";
		}

		DataLoader dLoader = new DataLoader();
		
		Population population = dLoader.readPopulation(PopFilePath);
		new NonSelectedPlansRemover().run(population);
		NetworkImpl net = dLoader.readNetwork(NetFilePath);
		
		String outputFile = new File(PopFilePath).getParent() + File.separatorChar + "PopulationOnlySelectedPlans.xml"; 
		System.out.println("writing output plan file..." + outputFile);
		new PopulationWriter(population, net).write(outputFile);
	}
}
