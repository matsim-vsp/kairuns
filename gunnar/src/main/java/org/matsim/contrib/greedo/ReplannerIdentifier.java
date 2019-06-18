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
package org.matsim.contrib.greedo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.greedo.datastructures.CountIndicatorUtils;
import org.matsim.contrib.greedo.datastructures.SpaceTimeIndicators;

import floetteroed.utilities.DynamicData;
import floetteroed.utilities.DynamicDataUtils;

/**
 * 
 * @author Gunnar Flötteröd
 *
 */
class ReplannerIdentifier {

	// -------------------- MEMBERS --------------------

	private final GreedoConfigGroup greedoConfig;

	private final Map<Id<Person>, SpaceTimeIndicators<Id<?>>> personId2physicalSlotUsage;
	private final Map<Id<Person>, SpaceTimeIndicators<Id<?>>> personId2hypothetialSlotUsage;
	private final DynamicData<Id<?>> currentWeightedCounts;
	private final DynamicData<Id<?>> upcomingWeightedCounts;

	private final Map<Id<Person>, Double> personId2hypotheticalUtilityChange;
	private final double totalUtilityChange;

	private final double lambdaBar;
	private final double beta;

	private SummaryStatistics lastExpectations = null;

	// -------------------- CONSTRUCTION --------------------

	ReplannerIdentifier(final Double unconstrainedBeta, final GreedoConfigGroup greedoConfig, final int iteration,
			final Map<Id<Person>, SpaceTimeIndicators<Id<?>>> personId2physicalSlotUsage,
			final Map<Id<Person>, SpaceTimeIndicators<Id<?>>> personId2hypotheticalSlotUsage,
			final Map<Id<Person>, Double> personId2hypotheticalUtilityChange) {

		this.greedoConfig = greedoConfig;
		this.personId2physicalSlotUsage = personId2physicalSlotUsage;
		this.personId2hypothetialSlotUsage = personId2hypotheticalSlotUsage;

		this.personId2hypotheticalUtilityChange = personId2hypotheticalUtilityChange;
		this.totalUtilityChange = personId2hypotheticalUtilityChange.values().stream()
				.mapToDouble(utlChange -> utlChange).sum();

		this.currentWeightedCounts = CountIndicatorUtils.newWeightedAndUnweightedCounts(
				this.greedoConfig.newTimeDiscretization(), this.personId2physicalSlotUsage.values()).getFirst();
		this.upcomingWeightedCounts = CountIndicatorUtils.newWeightedAndUnweightedCounts(
				this.greedoConfig.newTimeDiscretization(), this.personId2hypothetialSlotUsage.values()).getFirst();
		final double sumOfWeightedCountDifferences2 = DynamicDataUtils.sumOfDifferences2(this.currentWeightedCounts,
				this.upcomingWeightedCounts);

		if ((unconstrainedBeta != null) && (unconstrainedBeta > 0.0)) {
			this.beta = unconstrainedBeta;
			this.lambdaBar = 0.5 * unconstrainedBeta * this.totalUtilityChange
					/ Math.max(sumOfWeightedCountDifferences2, 1e-8);
		} else {
			this.lambdaBar = greedoConfig.getInitialMeanReplanningRate()
					* Math.pow(1.0 + iteration, greedoConfig.getReplanningRateIterationExponent());
			this.beta = 2.0 * this.lambdaBar * sumOfWeightedCountDifferences2 / Math.max(this.totalUtilityChange, 1e-8);
		}
	}

	// -------------------- IMPLEMENTATION --------------------

	Set<Id<Person>> drawReplanners() {

		// Initialize score residuals.

		final DynamicData<Id<?>> interactionResiduals = DynamicDataUtils.newDifference(this.upcomingWeightedCounts,
				this.currentWeightedCounts, this.lambdaBar);

		// Go through all persons and decide which driver gets to re-plan.

		final List<Id<Person>> allPersonIdsShuffled = new ArrayList<>(this.personId2hypotheticalUtilityChange.keySet());
		Collections.shuffle(allPersonIdsShuffled);

		DynamicData<Id<?>> weightedReplannerCountDifferences = null;
		DynamicData<Id<?>> unweightedReplannerCountDifferences = null;
		DynamicData<Id<?>> weightedNonReplannerCountDifferences = null;
		DynamicData<Id<?>> unweightedNonReplannerCountDifferences = null;
		if (this.greedoConfig.getDetailedLogging()) { // TODO This is awkward.
			weightedReplannerCountDifferences = new DynamicData<>(this.greedoConfig.newTimeDiscretization());
			unweightedReplannerCountDifferences = new DynamicData<>(this.greedoConfig.newTimeDiscretization());
			weightedNonReplannerCountDifferences = new DynamicData<>(this.greedoConfig.newTimeDiscretization());
			unweightedNonReplannerCountDifferences = new DynamicData<>(this.greedoConfig.newTimeDiscretization());
		}

		double replannerUtilityChangeSum = 0.0;
		double nonReplannerUtilityChangeSum = 0.0;
		double replannerSizeSum = 0.0;
		double nonReplannerSizeSum = 0.0;

		final Set<Id<Person>> replanners = new LinkedHashSet<>();

		for (Id<Person> personId : allPersonIdsShuffled) {

			final ScoreUpdater<Id<?>> scoreUpdater = new ScoreUpdater<>(this.personId2physicalSlotUsage.get(personId),
					this.personId2hypothetialSlotUsage.get(personId), this.lambdaBar, this.beta, interactionResiduals,
					this.personId2hypotheticalUtilityChange.get(personId));

			final boolean replanner = this.greedoConfig.getReplannerIdentifierRecipe().isReplanner(personId,
					scoreUpdater.getScoreChangeIfOne(), scoreUpdater.getScoreChangeIfZero());

			if (replanner) {
				replanners.add(personId);
				if (this.greedoConfig.getDetailedLogging()) {
					CountIndicatorUtils.addIndicatorsToTotalsTreatingNullAsZero(weightedReplannerCountDifferences,
							unweightedReplannerCountDifferences, this.personId2hypothetialSlotUsage.get(personId),
							+1.0);
					CountIndicatorUtils.addIndicatorsToTotalsTreatingNullAsZero(weightedReplannerCountDifferences,
							unweightedReplannerCountDifferences, this.personId2physicalSlotUsage.get(personId), -1.0);
				}
				replannerUtilityChangeSum += this.personId2hypotheticalUtilityChange.get(personId);
				if (this.personId2physicalSlotUsage.containsKey(personId)) {
					replannerSizeSum += this.personId2physicalSlotUsage.get(personId).size();
				}
			} else {
				if (this.greedoConfig.getDetailedLogging()) {
					CountIndicatorUtils.addIndicatorsToTotalsTreatingNullAsZero(weightedNonReplannerCountDifferences,
							unweightedNonReplannerCountDifferences, this.personId2hypothetialSlotUsage.get(personId),
							+1.0);
					CountIndicatorUtils.addIndicatorsToTotalsTreatingNullAsZero(weightedNonReplannerCountDifferences,
							unweightedNonReplannerCountDifferences, this.personId2physicalSlotUsage.get(personId),
							-1.0);
				}
				nonReplannerUtilityChangeSum += this.personId2hypotheticalUtilityChange.get(personId);
				if (this.personId2physicalSlotUsage.containsKey(personId)) {
					nonReplannerSizeSum += this.personId2physicalSlotUsage.get(personId).size();
				}
			}

			scoreUpdater.updateResiduals(replanner ? 1.0 : 0.0);
		}

		Double sumOfUnweightedReplannerCountDifferences2 = null;
		Double sumOfWeightedReplannerCountDifferences2 = null;
		Double sumOfUnweightedNonReplannerCountDifferences2 = null;
		Double sumOfWeightedNonReplannerCountDifferences2 = null;
		if (this.greedoConfig.getDetailedLogging()) {
			sumOfUnweightedReplannerCountDifferences2 = DynamicDataUtils
					.sumOfEntries2(unweightedReplannerCountDifferences);
			sumOfWeightedReplannerCountDifferences2 = DynamicDataUtils.sumOfEntries2(weightedReplannerCountDifferences);
			sumOfUnweightedNonReplannerCountDifferences2 = DynamicDataUtils
					.sumOfEntries2(unweightedNonReplannerCountDifferences);
			sumOfWeightedNonReplannerCountDifferences2 = DynamicDataUtils
					.sumOfEntries2(weightedNonReplannerCountDifferences);
		}

		// TODO This may access fields that are "detailed logging"-only.
		final Map<Id<Person>, Double> personId2similarity = new LinkedHashMap<>();
		for (Id<Person> personId : this.personId2hypotheticalUtilityChange.keySet()) {
			final SpaceTimeIndicators<Id<?>> hypotheticalSlotUsage = this.personId2hypothetialSlotUsage.get(personId);
			final SpaceTimeIndicators<Id<?>> physicalSlotUsage = this.personId2physicalSlotUsage.get(personId);
			double similarityNumerator = 0.0;
			for (int timeBin = 0; timeBin < this.greedoConfig.getBinCnt(); timeBin++) {
				if (hypotheticalSlotUsage != null) {
					for (SpaceTimeIndicators<Id<?>>.Visit hypotheticalVisit : hypotheticalSlotUsage
							.getVisits(timeBin)) {
						similarityNumerator += weightedReplannerCountDifferences
								.getBinValue(hypotheticalVisit.spaceObject, timeBin);
					}
				}
				if (physicalSlotUsage != null) {
					for (SpaceTimeIndicators<Id<?>>.Visit physicalVisit : physicalSlotUsage.getVisits(timeBin)) {
						similarityNumerator -= weightedReplannerCountDifferences.getBinValue(physicalVisit.spaceObject,
								timeBin);
					}
				}
			}
			personId2similarity.put(personId, similarityNumerator / this.personId2hypotheticalUtilityChange.size());
		}

		this.lastExpectations = new SummaryStatistics(this.lambdaBar, replannerUtilityChangeSum,
				nonReplannerUtilityChangeSum, sumOfUnweightedReplannerCountDifferences2,
				sumOfWeightedReplannerCountDifferences2, sumOfUnweightedNonReplannerCountDifferences2,
				sumOfWeightedNonReplannerCountDifferences2, replannerSizeSum, nonReplannerSizeSum, replanners.size(),
				this.personId2hypotheticalUtilityChange.size() - replanners.size(), personId2similarity,
				this.greedoConfig.getReplannerIdentifierRecipe().getDeployedRecipeName());

		return replanners;
	}

	// -------------------- INNER CLASS --------------------

	SummaryStatistics getSummaryStatistics(final Set<Id<Person>> replanners,
			final Map<Id<Person>, Integer> personId2age) {
		this.lastExpectations.setReplannerId2ageAtReplanning(
				personId2age.entrySet().stream().filter(entry -> replanners.contains(entry.getKey()))
						.collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue())));
		return this.lastExpectations;
	}

	public static class SummaryStatistics {

		public final Double lambdaBar;
		public final Double sumOfReplannerUtilityChanges;
		public final Double sumOfNonReplannerUtilityChanges;
		public final Double sumOfUnweightedReplannerCountDifferences2;
		public final Double sumOfWeightedReplannerCountDifferences2;
		public final Double sumOfUnweightedNonReplannerCountDifferences2;
		public final Double sumOfWeightedNonReplannerCountDifferences2;
		public final Double replannerSizeSum;
		public final Double nonReplannerSizeSum;
		public final Integer numberOfReplanners;
		public final Integer numberOfNonReplanners;
		public final Map<Id<Person>, Double> personId2similarity;
		public final String replannerIdentifierRecipeName;
		private Map<Id<Person>, Integer> replannerId2ageAtReplanning;

		SummaryStatistics() {
			this(null, null, null, null, null, null, null, null, null, null, null, new LinkedHashMap<>(), null);
		}

		private SummaryStatistics(final Double lambdaBar, final Double sumOfReplannerUtilityChanges,
				final Double sumOfNonReplannerUtilityChanges, final Double sumOfUnweightedReplannerCountDifferences2,
				final Double sumOfWeightedReplannerCountDifferences2,
				final Double sumOfUnweightedNonReplannerCountDifferences2,
				final Double sumOfWeightedNonReplannerCountDifferences2, final Double replannerSizeSum,
				final Double nonReplannerSizeSum, final Integer numberOfReplanners, final Integer numberOfNonReplanners,
				final Map<Id<Person>, Double> personId2similarity, final String replannerIdentifierRecipeName) {
			this.lambdaBar = lambdaBar;
			this.sumOfReplannerUtilityChanges = sumOfReplannerUtilityChanges;
			this.sumOfNonReplannerUtilityChanges = sumOfNonReplannerUtilityChanges;
			this.sumOfUnweightedReplannerCountDifferences2 = sumOfUnweightedReplannerCountDifferences2;
			this.sumOfWeightedReplannerCountDifferences2 = sumOfWeightedReplannerCountDifferences2;
			this.sumOfUnweightedNonReplannerCountDifferences2 = sumOfUnweightedNonReplannerCountDifferences2;
			this.sumOfWeightedNonReplannerCountDifferences2 = sumOfWeightedNonReplannerCountDifferences2;
			this.replannerSizeSum = replannerSizeSum;
			this.nonReplannerSizeSum = nonReplannerSizeSum;
			this.numberOfReplanners = numberOfReplanners;
			this.numberOfNonReplanners = numberOfNonReplanners;
			this.personId2similarity = Collections.unmodifiableMap(personId2similarity);
			this.replannerIdentifierRecipeName = replannerIdentifierRecipeName;
			this.replannerId2ageAtReplanning = new LinkedHashMap<>();
		}

		private void setReplannerId2ageAtReplanning(final Map<Id<Person>, Integer> replannerId2ageAtReplanning) {
			this.replannerId2ageAtReplanning = Collections.unmodifiableMap(replannerId2ageAtReplanning);
		}

		public Map<Id<Person>, Integer> getReplannerId2ageAtReplanning() {
			return this.replannerId2ageAtReplanning;
		}

		public Double getSumOfUtilityChanges() {
			if ((this.sumOfReplannerUtilityChanges != null) && (this.sumOfNonReplannerUtilityChanges != null)) {
				return (this.sumOfReplannerUtilityChanges + this.sumOfNonReplannerUtilityChanges);
			} else {
				return null;
			}
		}

		public Double getSumOfUtilityChangesGivenUniformReplanning() {
			final Double sumOfUtilityChanges = this.getSumOfUtilityChanges();
			if ((sumOfUtilityChanges != null) && (this.lambdaBar != null)) {
				return this.lambdaBar * sumOfUtilityChanges;
			} else {
				return null;
			}
		}

		public Double getSumOfWeightedCountDifferences2() {
			if ((this.sumOfWeightedReplannerCountDifferences2 != null)
					&& (this.sumOfWeightedNonReplannerCountDifferences2 != null)) {
				return (this.sumOfWeightedReplannerCountDifferences2 + this.sumOfWeightedNonReplannerCountDifferences2);
			} else {
				return null;
			}
		}

		public Double getSumOfUnweightedCountDifferences2() {
			if ((this.sumOfUnweightedReplannerCountDifferences2 != null)
					&& (this.sumOfUnweightedNonReplannerCountDifferences2 != null)) {
				return (this.sumOfUnweightedReplannerCountDifferences2
						+ this.sumOfUnweightedNonReplannerCountDifferences2);
			} else {
				return null;
			}
		}

		public Integer getNumberOfReplanningCandidates() {
			if ((this.numberOfReplanners != null) && (this.numberOfNonReplanners != null)) {
				return (this.numberOfReplanners + this.numberOfNonReplanners);
			} else {
				return null;
			}
		}
	}
}
