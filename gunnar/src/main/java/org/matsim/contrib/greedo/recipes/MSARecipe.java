/*
 * Copyright 2018 Gunnar Flötteröd
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * contact: gunnar.flotterod@gmail.com
 *
 */
package org.matsim.contrib.greedo.recipes;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.greedo.LogDataWrapper;
import org.matsim.core.gbl.MatsimRandom;

/**
 *
 * @author Gunnar Flötteröd
 *
 */
public class MSARecipe implements ReplannerIdentifierRecipe {

	// -------------------- CONSTANTS --------------------

	private final double initialStepSize;

	private final double iterationExponent;

	// -------------------- MEMBERS --------------------

	private int iteration = 0;

	// -------------------- CONSTRUCTION --------------------

	public MSARecipe(final double initialStepSize, final double iterationExponent) {
		this.initialStepSize = initialStepSize;
		this.iterationExponent = iterationExponent;
	}

	// --------------- IMPLEMENATION OF ReplannerIdentifierRecipe ---------------

	@Override
	public boolean isReplanner(Id<Person> personId, double deltaScoreIfYes, double deltaScoreIfNo) {
		final double proba = this.initialStepSize * Math.pow(1.0 + this.iteration, this.iterationExponent);
		return (MatsimRandom.getRandom().nextDouble() < proba);
	}

	@Override
	public void update(LogDataWrapper logDataWrapper) {
		this.iteration = logDataWrapper.getIteration();
	}

	@Override
	public String getDeployedRecipeName() {
		return this.getClass().getSimpleName();
	}

}
