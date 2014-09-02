/* *********************************************************************** *
 * project: org.matsim.*
 * BKickIncomeControler2
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
package playground.benjamin.scoring.income.old;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.households.PersonHouseholdMapping;
import org.matsim.roadpricing.RoadPricingConfigGroup;
import org.matsim.roadpricing.RoadPricingScheme;

import playground.benjamin.BkControler;
import playground.benjamin.BkPaths;

/**
 * @author bkick
 * @author michaz
 *
 */
@Deprecated // it is no longer necessary to use inheritance here.  kai, oct'11
            // for a better use case of personalizable travel costs and personalizable scoring, please refer to {@link BkRouterTest} and {@link BkScoringTest}. benjamin, oct'11
public class BkControlerIncome extends BkControler {

	private PersonHouseholdMapping personHouseholdMapping;

	public BkControlerIncome(String arg) {
		super(arg);
	}
	
	public BkControlerIncome(String[] args) {
		super(args);
	}

	public BkControlerIncome(Config config) {
		super(config);
	}

	@Override
	@Deprecated // it is no longer necessary to use inheritance here.  kai, oct'11
	            // for a better use case of personalizable travel costs and personalizable scoring, please refer to {@link BkRouterTest} and {@link BkScoringTest}. benjamin, oct'11
	protected void setUp() {

		this.addInstallTravelCostCalculatorFactoryControlerListener();

		this.scenarioData.getConfig().global().setNumberOfThreads(1);
		this.personHouseholdMapping = new PersonHouseholdMapping(((ScenarioImpl) this.getScenario()).getHouseholds());
		
		/*		Setting the needed scoring function.
		Remark: parameters must be set in several classes and independently for scoring and router!*/
		ScoringFunctionFactory scoringFactory = new IncomeScoringFunctionFactory(this.getScenario().getConfig(), personHouseholdMapping, this.getNetwork());
		
		this.setScoringFunctionFactory(scoringFactory);
		super.setUp();
	}

	private void installTravelCostCalculatorFactory() {
		//returns null, if there is no road pricing
        if (ConfigUtils.addOrGetModule(config, RoadPricingConfigGroup.GROUP_NAME, RoadPricingConfigGroup.class).isUsingRoadpricing()){
			RoadPricingScheme roadPricingScheme = (RoadPricingScheme) this.scenarioData.getScenarioElement(RoadPricingScheme.ELEMENT_NAME);
			
			/*		Setting travel cost calculator for the router.
			Remark: parameters must be set in several classes and independently for scoring and router!*/
			TravelDisutilityFactory travelCostCalculatorFactory = new IncomeTollTravelCostCalculatorFactory(personHouseholdMapping, roadPricingScheme, config);
			setTravelDisutilityFactory(travelCostCalculatorFactory);
		}
		else{
			/*		Setting travel cost calculator for the router.
			Remark: parameters must be set in several classes and independently for scoring and router!*/
			TravelDisutilityFactory travelCostCalculatorFactory = new IncomeTravelCostCalculatorFactory(personHouseholdMapping);
			setTravelDisutilityFactory(travelCostCalculatorFactory);
		}
	}
	
	private void addInstallTravelCostCalculatorFactoryControlerListener() {
		addControlerListener(new StartupListener() {

			@Override
			public void notifyStartup(StartupEvent event) {
				installTravelCostCalculatorFactory();
			}
			
		});
	}
	
	public static void main(String[] args) {
		
			//these lines can also be included in runConfigurations/arguments/programArguments
			String config = BkPaths.SHAREDSVN + "studies/bkick/oneRouteTwoModeIncomeTest/config.xml"; 
			String[] args2 = {config};
			args = args2;
			
		if ((args == null) || (args.length == 0)) {
			System.out.println("No argument given!");
			System.out.println("Usage: Controler config-file [dtd-file]");
			System.out.println();
		} else {
			final BkControlerIncome controler = new BkControlerIncome(args);
			
			controler.setOverwriteFiles(true);
			controler.run();
		}
	}


}
