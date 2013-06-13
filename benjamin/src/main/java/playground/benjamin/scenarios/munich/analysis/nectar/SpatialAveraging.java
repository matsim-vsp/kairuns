/* *********************************************************************** *
 * project: org.matsim.*
 * SpatialAveragingForLinkEmissions.java
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
package playground.benjamin.scenarios.munich.analysis.nectar;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.MatsimConfigReader;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.PointFeatureFactory;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.matsim.core.utils.misc.Time;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import playground.vsp.emissions.events.EmissionEventsReader;
import playground.vsp.emissions.types.ColdPollutant;
import playground.vsp.emissions.types.WarmPollutant;
import playground.vsp.emissions.utils.EmissionUtils;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.util.Assert;

/**
 * @author benjamin
 *
 */
public class SpatialAveraging {
	private static final Logger logger = Logger.getLogger(SpatialAveraging.class);

	private final static String runNumber1 = "baseCase";
	private final static String runNumber2 = "zone30";
	private final static String runDirectory1 = "../../runs-svn/detEval/latsis/output/output_baseCase_ctd_newCode/";
	private final static String runDirectory2 = "../../runs-svn/detEval/latsis/output/output_policyCase_zone30/";
	private final String netFile1 = runDirectory1 + "output_network.xml.gz";
	private final String munichShapeFile = "../../detailedEval/Net/shapeFromVISUM/urbanSuburban/cityArea.shp";

	private static String configFile1 = runDirectory1 + "output_config.xml.gz";
	private final static Integer lastIteration1 = getLastIteration(configFile1);
	private static String configFile2 = runDirectory1 + "output_config.xml.gz";
	private final static Integer lastIteration2 = getLastIteration(configFile2);
	private final String emissionFile1 = runDirectory1 + "ITERS/it." + lastIteration1 + "/" + lastIteration1 + ".emission.events.xml.gz";
	private final String emissionFile2 = runDirectory2 + "ITERS/it." + lastIteration2 + "/" + lastIteration2 + ".emission.events.xml.gz";
	
//	private final static String runNumber1 = "981";
//	private final static String runNumber2 = "983";
//	private final static String runDirectory1 = "../../runs-svn/run" + runNumber1 + "/";
//	private final static String runDirectory2 = "../../runs-svn/run" + runNumber2 + "/";
//	private final String netFile1 = runDirectory1 + runNumber1 + ".output_network.xml.gz";
//	private final String munichShapeFile = "../../detailedEval/Net/shapeFromVISUM/urbanSuburban/cityArea.shp";
//
//	private static String configFile1 = runDirectory1 + runNumber1 + ".output_config.xml.gz";
//	private final static Integer lastIteration1 = getLastIteration(configFile1);
//	private static String configFile2 = runDirectory1 + runNumber1 + ".output_config.xml.gz";
//	private final static Integer lastIteration2 = getLastIteration(configFile2);
//	private final String emissionFile1 = runDirectory1 + "ITERS/it." + lastIteration1 + "/" + runNumber1 + "." + lastIteration1 + ".emission.events.xml.gz";
//	private final String emissionFile2 = runDirectory2 + "ITERS/it." + lastIteration2 + "/" + runNumber2 + "." + lastIteration2 + ".emission.events.xml.gz";

	Network network;
	Collection<SimpleFeature> featuresInMunich;
	EmissionUtils emissionUtils = new EmissionUtils();
	EmissionsPerLinkWarmEventHandler warmHandler;
	EmissionsPerLinkColdEventHandler coldHandler;
	SortedSet<String> listOfPollutants;
	double simulationEndTime;
	String outPathStub;

	final CoordinateReferenceSystem targetCRS = MGC.getCRS("EPSG:20004");
	static double xMin = 4452550.25;
	static double xMax = 4479483.33;
	static double yMin = 5324955.00;
	static double yMax = 5345696.81;

	final double scalingFactor = 100.;
	final int noOfTimeBins = 1;
	final int noOfXbins = 160;
	final int noOfYbins = 120;
	final double smoothingRadius_m = 500.; 
	final String pollutant2analyze = WarmPollutant.NO2.toString();
	final boolean baseCaseOnly = false;
	final boolean calculateRelativeChange = false;

	private void run() throws IOException{
		this.simulationEndTime = getEndTime(configFile1);
		this.listOfPollutants = emissionUtils.getListOfPollutants();
		Scenario scenario = loadScenario(netFile1);
		this.network = scenario.getNetwork();		
		this.featuresInMunich = ShapeFileReader.getAllFeatures(munichShapeFile);

		Map<Double, Map<Id, Map<String, Double>>> time2EmissionMapToAnalyze;
		Map<Double, Map<Id, Double>> time2DemandMapToAnalyze;

		processEmissions(emissionFile1);
		Map<Double, Map<Id, Map<WarmPollutant, Double>>> time2WarmEmissionsTotal1 = this.warmHandler.getWarmEmissionsPerLinkAndTimeInterval();
		Map<Double, Map<Id, Map<ColdPollutant, Double>>> time2ColdEmissionsTotal1 = this.coldHandler.getColdEmissionsPerLinkAndTimeInterval();
		Map<Double, Map<Id, Double>> time2CountsPerLink1 = this.warmHandler.getTime2linkIdLeaveCount();

		Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotal1 = sumUpEmissionsPerTimeInterval(time2WarmEmissionsTotal1, time2ColdEmissionsTotal1);
		Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotalFilled1 = setNonCalculatedEmissions(time2EmissionsTotal1);
		Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotalFilledAndFiltered1 = filterEmissionLinks(time2EmissionsTotalFilled1);
		Map<Double, Map<Id, Double>> time2CountsPerLinkFilledAndFiltered1 = setNonCalculatedCountsAndFilter(time2CountsPerLink1);

		this.warmHandler.reset(0);
		this.coldHandler.reset(0);

		if(baseCaseOnly){
			time2EmissionMapToAnalyze = time2EmissionsTotalFilledAndFiltered1;
			time2DemandMapToAnalyze = time2CountsPerLinkFilledAndFiltered1;
			outPathStub = runDirectory1 + "analysis/spatialAveraging/" + runNumber1 + "." + lastIteration1;
		} else {
			processEmissions(emissionFile2);
			Map<Double, Map<Id, Map<WarmPollutant, Double>>> time2WarmEmissionsTotal2 = this.warmHandler.getWarmEmissionsPerLinkAndTimeInterval();
			Map<Double, Map<Id, Map<ColdPollutant, Double>>> time2ColdEmissionsTotal2 = this.coldHandler.getColdEmissionsPerLinkAndTimeInterval();
			Map<Double, Map<Id, Double>> time2CountsPerLink2 = this.warmHandler.getTime2linkIdLeaveCount();
			
			Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotal2 = sumUpEmissionsPerTimeInterval(time2WarmEmissionsTotal2, time2ColdEmissionsTotal2);
			Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotalFilled2 = setNonCalculatedEmissions(time2EmissionsTotal2);
			Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotalFilledAndFiltered2 = filterEmissionLinks(time2EmissionsTotalFilled2);
			Map<Double, Map<Id, Double>> time2CountsPerLinkFilledAndFiltered2 = setNonCalculatedCountsAndFilter(time2CountsPerLink2);

			if(calculateRelativeChange){
				//TODO: revise the two following methods
//				time2EmissionMapToAnalyze = calcualateRelativeEmissionDifferences(time2EmissionsTotalFilledAndFiltered1, time2EmissionsTotalFilledAndFiltered2);
//				time2DemandMapToAnalyze = calculateRelativeDemandDifferences(time2CountsPerLinkFilledAndFiltered1, time2CountsPerLinkFilledAndFiltered2);
				outPathStub = runDirectory1 + "analysis/spatialAveraging/" + runNumber2 + "." + lastIteration2 + "-" + runNumber1 + "." + lastIteration1 + ".relativeDelta";
			} else {
				time2EmissionMapToAnalyze = calcualateAbsoluteEmissionDifferences(time2EmissionsTotalFilledAndFiltered1, time2EmissionsTotalFilledAndFiltered2, time2CountsPerLinkFilledAndFiltered1, time2CountsPerLinkFilledAndFiltered2);
				time2DemandMapToAnalyze = calculateAbsoluteDemandDifferences(time2CountsPerLinkFilledAndFiltered1, time2CountsPerLinkFilledAndFiltered2);
				outPathStub = runDirectory1 + "analysis/spatialAveraging/" + runNumber2 + "." + lastIteration2 + "-" + runNumber1 + "." + lastIteration1 + ".absoluteDelta";
			}
		}

		Map<Double, double[][]> time2Demand = new HashMap<Double, double[][]>();
		Map<Double, double[][]> time2Emissions = new HashMap<Double, double[][]>();
		for(double endOfTimeInterval : time2DemandMapToAnalyze.keySet()){
			double[][] demand = performSpatialAveragingForDemand(time2DemandMapToAnalyze, endOfTimeInterval);
			writeRoutput(demand, outPathStub + ".Routput.Demand" + "." + endOfTimeInterval + ".txt");
			time2Demand.put(endOfTimeInterval, demand);
			double[][] emissions = performSpatialAveragingForEmissions(time2EmissionMapToAnalyze, endOfTimeInterval);
			writeRoutput(emissions, outPathStub + ".Routput." + pollutant2analyze.toString() + "." + endOfTimeInterval + ".txt");
			time2Emissions.put(endOfTimeInterval, emissions);
		}
//		writeGISoutput(time2Demand, outPathStub + ".GISoutput.Demand" + ".movie.shp");
//		writeGISoutput(time2Emissions, outPathStub +  ".GISoutput." + pollutant2analyze.toString() + ".movie.shp");
	}

	private void writeGISoutput(
			Map<Double, double[][]> time2results,
			String outputPathForGIS) throws IOException {
		
		PointFeatureFactory factory = new PointFeatureFactory.Builder()
		.setCrs(this.targetCRS)
		.setName("EmissionPoint")
		.addAttribute("Time", String.class)
		.addAttribute("Emissions", Double.class)
		.create();
		Collection<SimpleFeature> features = new ArrayList<SimpleFeature>();
		
		for(Double endOfTimeInterval : time2results.keySet()){
//			if(endOfTimeInterval < Time.MIDNIGHT){ // time manager in QGIS does not accept time beyond midnight...
				for(int xIndex = 0; xIndex < noOfXbins; xIndex++){
					for(int yIndex = 0; yIndex < noOfYbins; yIndex++){
						Coord cellCentroid = findCellCentroid(xIndex, yIndex);
						if(isInMunich(cellCentroid)){
							String dateTimeString = convertSeconds2dateTimeFormat(endOfTimeInterval);
							double result = time2results.get(endOfTimeInterval)[xIndex][yIndex];
							SimpleFeature feature = factory.createPoint(new Coordinate(cellCentroid.getX(), cellCentroid.getY()), new Object[] {dateTimeString, result}, null);
							features.add(feature);
						}
					}
//				}
			}
		}
		ShapeFileWriter.writeGeometries(features, outputPathForGIS);
		logger.info("Finished writing output for GIS to " + outputPathForGIS);
	}

	private void writeRoutput(double[][] results, String outputPathForR) {
		try {
			BufferedWriter buffW = new BufferedWriter(new FileWriter(outputPathForR));
			String valueString = new String();
			valueString = "\t";
			
			//x-coordinates as first row
			for(int xIndex = 0; xIndex < results.length; xIndex++){
				valueString += findBinCenterX(xIndex) + "\t";
			}
			buffW.write(valueString);
			buffW.newLine();
			valueString = new String();
			
			for(int yIndex = 0; yIndex < results[0].length; yIndex++){
				//y-coordinates as first column
				valueString += findBinCenterY(yIndex) + "\t";
				//table contents
				for(int xIndex = 0; xIndex < results.length; xIndex++){ 
						Coord cellCentroid = findCellCentroid(xIndex, yIndex);
						if(isInMunich(cellCentroid)){
							valueString += Double.toString(results[xIndex][yIndex]) + "\t"; 
						} else {
							valueString += "NA" + "\t";
						}
				}
				buffW.write(valueString);
				buffW.newLine();
				valueString = new String();
			}
		buffW.close();	
		} catch (IOException e) {
			throw new RuntimeException("Failed writing output for R.");
		}	
		logger.info("Finished writing output for R to " + outputPathForR);
	}

	private double[][] getResults(
				double[][] sumOfweightedValuesForCell,
				double[][] sumOfweightsForCell,
				AveragingMethod avm) {
			
			double[][] results = new double[noOfXbins][noOfYbins];
			final double area_in_smoothing_circle_sqkm = (Math.PI * this.smoothingRadius_m * this.smoothingRadius_m) / (1000. * 1000.);
	//		final double area_in_cell_sqkm = (xMax-xMin)/noOfXbins * (yMax-yMin)/noOfYbins / 1000. / 1000. ;
			for(int xIndex = 0; xIndex < noOfXbins; xIndex++){
				for(int yIndex = 0; yIndex < noOfYbins; yIndex++){
					
					if(avm.equals(AveragingMethod.AVERAGE)){
						/* Average value for cell
						 * Values NEED to be non-additive (e.g. g/vkm, ...)  */
						results[xIndex][yIndex] = sumOfweightedValuesForCell[xIndex][yIndex] / sumOfweightsForCell[xIndex][yIndex] ;
					} else if(avm.equals(AveragingMethod.PERSQKM)){
						/* Sum over values for cell normalized to "per sqkm" (dependent on calcluateWeightOfLinkForCell)
						 * Values NEED to be additive (e.g. vkm, g, counts, ...)
						 * Make sure coordinate system is metric */
						
	//					results[xIndex][yIndex] = sumOfweightedValuesForCell[xIndex][yIndex] / area_in_cell_sqkm  ;
						results[xIndex][yIndex] = sumOfweightedValuesForCell[xIndex][yIndex] / area_in_smoothing_circle_sqkm;
					}
				}
			}
			return results;
		}

	private double[][] performSpatialAveragingForEmissions(
			Map<Double, Map<Id, Map<String, Double>>> time2EmissionMapToAnalyze,
			double endOfTimeInterval) {
		
		Map<Id, Map<String, Double>> emissionMapToAnalyze = time2EmissionMapToAnalyze.get(endOfTimeInterval);
		double[][] sumOfweightsForCell = new double[noOfXbins][noOfYbins];
		double[][] sumOfweightedValuesForCell = new double[noOfXbins][noOfYbins];

		for(Id linkId : emissionMapToAnalyze.keySet()){
			Coord linkCoord = this.network.getLinks().get(linkId).getCoord();
			double xLink = linkCoord.getX();
			double yLink = linkCoord.getY();

			for(int xIndex = 0; xIndex < noOfXbins; xIndex++){
				for(int yIndex = 0; yIndex < noOfYbins; yIndex++){
					Coord cellCentroid = findCellCentroid(xIndex, yIndex);

					double value = emissionMapToAnalyze.get(linkId).get(pollutant2analyze);
					double weightOfLinkForCell = calculateWeightOfLinkForCell(xLink, yLink, cellCentroid.getX(), cellCentroid.getY());
					sumOfweightsForCell[xIndex][yIndex] += weightOfLinkForCell;
					sumOfweightedValuesForCell[xIndex][yIndex] += weightOfLinkForCell * value;
				}
			}
		}
//		double[][] results = getResults(sumOfweightedValuesForCell, sumOfweightsForCell, AveragingMethod.AVERAGE);
		double[][] results = getResults(sumOfweightedValuesForCell, sumOfweightsForCell, AveragingMethod.PERSQKM);
		return results;
	}

	private double[][] performSpatialAveragingForDemand(
			Map<Double, Map<Id, Double>> time2DemandMapToAnalyze,
			double endOfTimeInterval) {
		
		Map<Id, Double> demandMapToAnalyze = time2DemandMapToAnalyze.get(endOfTimeInterval);
		double[][] sumOfweightsForCell = new double[noOfXbins][noOfYbins];
		double[][] sumOfweightedValuesForCell = new double[noOfXbins][noOfYbins];

		for(Id linkId : demandMapToAnalyze.keySet()){
			Coord linkCoord = this.network.getLinks().get(linkId).getCoord();
			double xLink = linkCoord.getX();
			double yLink = linkCoord.getY();

			for(int xIndex = 0; xIndex < noOfXbins; xIndex++){
				for(int yIndex = 0; yIndex < noOfYbins; yIndex++){
					Coord cellCentroid = findCellCentroid(xIndex, yIndex);
					double value = demandMapToAnalyze.get(linkId);
					double weightOfLinkForCell = calculateWeightOfLinkForCell(xLink, yLink, cellCentroid.getX(), cellCentroid.getY());
					sumOfweightsForCell[xIndex][yIndex] += weightOfLinkForCell;
					sumOfweightedValuesForCell[xIndex][yIndex] += weightOfLinkForCell * value;
				}
			}
		}
		double[][] results = getResults(sumOfweightedValuesForCell, sumOfweightsForCell, AveragingMethod.PERSQKM);
		return results;
	}

	private boolean isInMunich(Coord cellCentroid) {
		boolean isInMunichShape = false;
		GeometryFactory factory = new GeometryFactory();
		Geometry geo = factory.createPoint(new Coordinate(cellCentroid.getX(), cellCentroid.getY()));
		for(SimpleFeature feature : this.featuresInMunich){
			if(((Geometry) feature.getDefaultGeometry()).contains(geo)){
				isInMunichShape = true;
				break;
			}
		}
		return isInMunichShape;
	}

	private boolean isInResearchArea(Coord linkCoord) {
		Double xLink = linkCoord.getX();
		Double yLink = linkCoord.getY();
		
		if(xLink > xMin && xLink < xMax){
			if(yLink > yMin && yLink < yMax){
				return true;
			}
		}
		return false;
	}

	private String convertSeconds2dateTimeFormat(double endOfTimeInterval) {
		String date = "2012-04-13 ";
		String time = Time.writeTime(endOfTimeInterval, Time.TIMEFORMAT_HHMM);
		String dateTimeString = date + time;
		return dateTimeString;
	}

	private double calculateWeightOfLinkForCell(double x1, double y1, double x2, double y2) {
		double distance = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
		return Math.exp((-distance * distance) / (smoothingRadius_m * smoothingRadius_m));
	}
	
//	private double calculateWeightOfLinkForCell(double x1, double y1, double x2, double y2) {
//		// check if x and y values are in the same cell:
//		if ( mapXCoordToBin(x1) == mapXCoordToBin(x2) ) {
//			if ( mapYCoordToBin(y1) == mapYCoordToBin(y2) ) {
//				return 1. ;
//			}
//		}
//		return 0. ;
//	}

	private double findBinCenterY(int yIndex) {
		double yBinCenter = yMin + ((yIndex + .5) / noOfYbins) * (yMax - yMin);
		Assert.equals(mapYCoordToBin(yBinCenter), yIndex);
		return yBinCenter ;
	}

	private double findBinCenterX(int xIndex) {
		double xBinCenter = xMin + ((xIndex + .5) / noOfXbins) * (xMax - xMin);
		Assert.equals(mapXCoordToBin(xBinCenter), xIndex);
		return xBinCenter ;
	}

	private Coord findCellCentroid(int xIndex, int yIndex) {
		double xCentroid = findBinCenterX(xIndex);
		double yCentroid = findBinCenterY(yIndex);
		Coord cellCentroid = new CoordImpl(xCentroid, yCentroid);
		return cellCentroid;
	}

	private Integer mapYCoordToBin(double yCoord) {
		if (yCoord <= yMin || yCoord >= yMax) return null; // yCoord is not in area of interest
		double relativePositionY = ((yCoord - yMin) / (yMax - yMin) * noOfYbins); // gives the relative position along the y-range
		return (int) relativePositionY; // returns the number of the bin [0..n-1]
	}

	private Integer mapXCoordToBin(double xCoord) {
		if (xCoord <= xMin || xCoord >= xMax) return null; // xCorrd is not in area of interest
		double relativePositionX = ((xCoord - xMin) / (xMax - xMin) * noOfXbins); // gives the relative position along the x-range
		return (int) relativePositionX; // returns the number of the bin [0..n-1]
	}

	private Map<Double, Map<Id, Double>> calculateAbsoluteDemandDifferences(
			Map<Double, Map<Id, Double>> time2CountsPerLinkFilledAndFiltered1,
			Map<Double, Map<Id, Double>> time2CountsPerLinkFilledAndFiltered2) {
		
		Map<Double, Map<Id, Double>> time2AbsoluteDemandDifference = new HashMap<Double, Map<Id,Double>>();
		for(Double endOfTimeInterval : time2CountsPerLinkFilledAndFiltered1.keySet()){
			Map<Id, Double> absoluteDemandDifference = new HashMap<Id, Double>();
			Map<Id, Double> linkId2Counts = time2CountsPerLinkFilledAndFiltered1.get(endOfTimeInterval);
			for(Id linkId : linkId2Counts.keySet()){
				double countBefore = this.scalingFactor * linkId2Counts.get(linkId);
				double countAfter = this.scalingFactor * time2CountsPerLinkFilledAndFiltered2.get(endOfTimeInterval).get(linkId);
				
				double vkmBefore = countBefore * this.network.getLinks().get(linkId).getLength() / 1000.;
				double vkmAfter = countAfter * this.network.getLinks().get(linkId).getLength() / 1000.;
				
				double vkmDifference = vkmAfter - vkmBefore;
				absoluteDemandDifference.put(linkId, vkmDifference);
				
//				double countDifference = countAfter - countBefore;
//				absoluteDemandDifference.put(linkId, countDifference);
			}
			time2AbsoluteDemandDifference.put(endOfTimeInterval, absoluteDemandDifference);
		}
		return time2AbsoluteDemandDifference;
	}

	private Map<Double, Map<Id, Map<String, Double>>> calcualateAbsoluteEmissionDifferences(
			Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotalFilledAndFiltered1,
			Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotalFilledAndFiltered2,
			Map<Double, Map<Id, Double>> time2CountsPerLinkFilledAndFiltered1,
			Map<Double, Map<Id, Double>> time2CountsPerLinkFilledAndFiltered2) {

		Map<Double, Map<Id, Map<String, Double>>> time2AbsoluteEmissionDifference = new HashMap<Double, Map<Id, Map<String, Double>>>();
		for(Entry<Double, Map<Id, Map<String, Double>>> entry0 : time2EmissionsTotalFilledAndFiltered1.entrySet()){
			double endOfTimeInterval = entry0.getKey();
			Map<Id, Map<String, Double>> absoluteEmissionDifference = new HashMap<Id, Map<String, Double>>();
			Map<Id, Map<String, Double>> linkId2Emissions = entry0.getValue();

			for(Entry<Id, Map<String, Double>> entry1 : linkId2Emissions.entrySet()){
				Id linkId = entry1.getKey();
				Map<String, Double> absoluteEmissionDifferencePerLink = new HashMap<String, Double>();
				for(String pollutant : entry1.getValue().keySet()){
					Double emissionsBefore = this.scalingFactor * entry1.getValue().get(pollutant);
					Double emissionsAfter = this.scalingFactor * time2EmissionsTotalFilledAndFiltered2.get(endOfTimeInterval).get(linkId).get(pollutant);
					
					double linkLength_km = this.network.getLinks().get(linkId).getLength() / 1000.;
					
					double emissionsPerVehicleKmBefore; 
					double countBefore = this.scalingFactor * time2CountsPerLinkFilledAndFiltered1.get(endOfTimeInterval).get(linkId);
					if(countBefore != 0.0){
						emissionsPerVehicleKmBefore = emissionsBefore / (countBefore * linkLength_km);
					} else {
						emissionsPerVehicleKmBefore = 0.0;
					}
					
					double emissionsPerVehicleKmAfter;
					double countAfter = this.scalingFactor * time2CountsPerLinkFilledAndFiltered2.get(endOfTimeInterval).get(linkId);
					if(countAfter != 0.0){
						emissionsPerVehicleKmAfter = emissionsAfter / (countAfter * linkLength_km);
					} else {
						emissionsPerVehicleKmAfter = 0.0;
					}

//					double emissionsPerVehicleKmDifference = emissionsPerVehicleKmAfter - emissionsPerVehicleKmBefore;
//					absoluteEmissionDifferencePerLink.put(pollutant, emissionsPerVehicleKmDifference);
					
					double absoluteEmissionDifferenceOfLink = emissionsAfter - emissionsBefore;
					absoluteEmissionDifferencePerLink.put(pollutant, absoluteEmissionDifferenceOfLink);
				}
				absoluteEmissionDifference.put(linkId, absoluteEmissionDifferencePerLink);
			}
			time2AbsoluteEmissionDifference.put(endOfTimeInterval, absoluteEmissionDifference);
		}
		return time2AbsoluteEmissionDifference;
	}

	private Map<Double, Map<Id, Double>> calculateRelativeDemandDifferences(
			Map<Double, Map<Id, Double>> time2CountsPerLinkFilledAndFiltered1,
			Map<Double, Map<Id, Double>> time2CountsPerLinkFilledAndFiltered2) {

		Map<Double, Map<Id, Double>> time2RelativeDelta = new HashMap<Double, Map<Id, Double>>();
		for(Entry<Double, Map<Id, Double>> entry0 : time2CountsPerLinkFilledAndFiltered1.entrySet()){
			double endOfTimeInterval = entry0.getKey();
			Map<Id, Double> linkId2Demand = entry0.getValue();
			Map<Id, Double> delta = new HashMap<Id, Double>();

			for(Entry<Id, Double> entry1 : linkId2Demand.entrySet()){
				Id linkId = entry1.getKey();
				double demandBefore = entry1.getValue();
				double demandAfter = time2CountsPerLinkFilledAndFiltered2.get(endOfTimeInterval).get(linkId);
				if (demandBefore == 0.0){ // cannot calculate relative change if "before" value is 0 ...
					logger.warn("Setting demand in baseCase for link " + linkId + " from " + demandBefore + " to 1.0 ...");
					demandBefore = 1;
				} else {
					// do nothing
				}
				double demandDifferenceRatio = (demandAfter - demandBefore) / demandBefore;
				delta.put(linkId, demandDifferenceRatio);
				
				//===
//				double linkLength_km = this.network.getLinks().get(linkId).getLength() / 1000.;
//				
//				double congestionTimePerVehKmBefore; 
//				double countBefore = time2CountsPerLinkFilledAndFiltered1.get(endOfTimeInterval).get(linkId);
//				if(countBefore == 0.){
//					countBefore = 1.;
//					logger.warn("setting count in baseCase for link " + linkId + " from " + countBefore + " to 1.0 ...");
//				}
//				congestionTimePerVehKmBefore = demandBefore / (countBefore * linkLength_km);
//				
//				double congestionTimePerVehKmAfter;
//				double countAfter = time2CountsPerLinkFilledAndFiltered2.get(endOfTimeInterval).get(linkId);
//				if(countAfter == 0.){
//					countAfter = 1.;
//					logger.warn("setting count in policyCase for link " + linkId + " from " + countAfter + " to 1.0 ...");
//				}
//				congestionTimePerVehKmAfter = demandAfter / (countAfter * linkLength_km);
//
//				double demandDifferenceRatio = (congestionTimePerVehKmAfter - congestionTimePerVehKmBefore) / congestionTimePerVehKmBefore;
//				delta.put(linkId, demandDifferenceRatio);
				//===
			}
			time2RelativeDelta.put(endOfTimeInterval, delta);
		}
		return time2RelativeDelta;
	}
	
	private Map<Double, Map<Id, Map<String, Double>>> calcualateRelativeEmissionDifferences(
			Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotalFilledAndFiltered1,
			Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotalFilledAndFiltered2) {

		Map<Double, Map<Id, Map<String, Double>>> time2RelativeDelta = new HashMap<Double, Map<Id, Map<String, Double>>>();
		for(Entry<Double, Map<Id, Map<String, Double>>> entry0 : time2EmissionsTotalFilledAndFiltered1.entrySet()){
			double endOfTimeInterval = entry0.getKey();
			Map<Id, Map<String, Double>> linkId2emissions = entry0.getValue();
			Map<Id, Map<String, Double>> relativeDelta = new HashMap<Id, Map<String, Double>>();

			for(Entry<Id, Map<String, Double>> entry1 : linkId2emissions.entrySet()){
				Id linkId = entry1.getKey();
				Map<String, Double> emissionDifferenceMap = new HashMap<String, Double>();
				for(String pollutant : entry1.getValue().keySet()){
					double emissionsBefore = entry1.getValue().get(pollutant);
					double emissionsAfter = time2EmissionsTotalFilledAndFiltered2.get(endOfTimeInterval).get(linkId).get(pollutant);
					if (emissionsBefore == 0.0){ // cannot calculate relative change if "before" value is 0.0 ...
						logger.warn("Setting emissions in baseCase on link " + linkId + " and pollutant " + pollutant + " from " + emissionsBefore + " to 1.0 ...");
						emissionsBefore = 1.0;
					} else {
						// do nothing
					}
					double emissionDifferenceRatio = (emissionsAfter - emissionsBefore) / emissionsBefore;
					emissionDifferenceMap.put(pollutant, emissionDifferenceRatio);
				}
				relativeDelta.put(linkId, emissionDifferenceMap);
			}
			time2RelativeDelta.put(endOfTimeInterval, relativeDelta);
		}
		return time2RelativeDelta;
	}

	private Map<Double, Map<Id, Double>> setNonCalculatedCountsAndFilter(Map<Double, Map<Id, Double>> time2CountsPerLink) {
		Map<Double, Map<Id, Double>> time2CountsTotalFiltered = new HashMap<Double, Map<Id,Double>>();
	
		for(Double endOfTimeInterval : time2CountsPerLink.keySet()){
			Map<Id, Double> linkId2Count = time2CountsPerLink.get(endOfTimeInterval);
			Map<Id, Double> linkId2CountFiltered = new HashMap<Id, Double>();
		
			for(Link link : network.getLinks().values()){
				Coord linkCoord = link.getCoord();
				if(isInResearchArea(linkCoord)){
					Id linkId = link.getId();
	
					if(linkId2Count.get(linkId) == null){
						linkId2CountFiltered.put(linkId, 0.);
					} else {
						linkId2CountFiltered.put(linkId, linkId2Count.get(linkId));
					}
				}
			}
			time2CountsTotalFiltered.put(endOfTimeInterval, linkId2CountFiltered);
		}
		return time2CountsTotalFiltered;
	}

	private Map<Double, Map<Id, SortedMap<String, Double>>> setNonCalculatedEmissions(Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotal) {
		Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotalFilled = new HashMap<Double, Map<Id, SortedMap<String, Double>>>();
		
		for(double endOfTimeInterval : time2EmissionsTotal.keySet()){
			Map<Id, SortedMap<String, Double>> emissionsTotalFilled = this.emissionUtils.setNonCalculatedEmissionsForNetwork(this.network, time2EmissionsTotal.get(endOfTimeInterval));
			time2EmissionsTotalFilled.put(endOfTimeInterval, emissionsTotalFilled);
		}
		return time2EmissionsTotalFilled;
	}

	private Map<Double, Map<Id, Map<String, Double>>> filterEmissionLinks(Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotal) {
		Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotalFiltered = new HashMap<Double, Map<Id, Map<String, Double>>>();
		
		for(Double endOfTimeInterval : time2EmissionsTotal.keySet()){
			Map<Id, SortedMap<String, Double>> emissionsTotal = time2EmissionsTotal.get(endOfTimeInterval);
			Map<Id, Map<String, Double>> emissionsTotalFiltered = new HashMap<Id, Map<String, Double>>();

			for(Link link : network.getLinks().values()){
				Coord linkCoord = link.getCoord();
				if(isInResearchArea(linkCoord)){
					Id linkId = link.getId();
					emissionsTotalFiltered.put(linkId, emissionsTotal.get(linkId));
				}
			}
			time2EmissionsTotalFiltered.put(endOfTimeInterval, emissionsTotalFiltered);
		}
		return time2EmissionsTotalFiltered;
	}

	private Map<Double, Map<Id, SortedMap<String, Double>>> sumUpEmissionsPerTimeInterval(
			Map<Double, Map<Id, Map<WarmPollutant, Double>>> time2warmEmissionsTotal,
			Map<Double, Map<Id, Map<ColdPollutant, Double>>> time2coldEmissionsTotal) {
	
		Map<Double, Map<Id, SortedMap<String, Double>>> time2totalEmissions = new HashMap<Double, Map<Id, SortedMap<String, Double>>>();
	
		for(double endOfTimeInterval: time2warmEmissionsTotal.keySet()){
			Map<Id, Map<WarmPollutant, Double>> warmEmissions = time2warmEmissionsTotal.get(endOfTimeInterval);
			
			Map<Id, SortedMap<String, Double>> totalEmissions = new HashMap<Id, SortedMap<String, Double>>();
			if(time2coldEmissionsTotal.get(endOfTimeInterval) == null){
				for(Id id : warmEmissions.keySet()){
					SortedMap<String, Double> warmEmissionsOfLink = this.emissionUtils.convertWarmPollutantMap2String(warmEmissions.get(id));
					totalEmissions.put(id, warmEmissionsOfLink);
				}
			} else {
				Map<Id, Map<ColdPollutant, Double>> coldEmissions = time2coldEmissionsTotal.get(endOfTimeInterval);
				totalEmissions = this.emissionUtils.sumUpEmissionsPerId(warmEmissions, coldEmissions);
			}
			time2totalEmissions.put(endOfTimeInterval, totalEmissions);
		}
		return time2totalEmissions;
	}

	private void processEmissions(String emissionFile) {
		EventsManager eventsManager = EventsUtils.createEventsManager();
		EmissionEventsReader emissionReader = new EmissionEventsReader(eventsManager);
		this.warmHandler = new EmissionsPerLinkWarmEventHandler(this.simulationEndTime, noOfTimeBins);
		this.coldHandler = new EmissionsPerLinkColdEventHandler(this.simulationEndTime, noOfTimeBins);
		eventsManager.addHandler(this.warmHandler);
		eventsManager.addHandler(this.coldHandler);
		emissionReader.parse(emissionFile);
	}

	private Scenario loadScenario(String netFile) {
		Config config = ConfigUtils.createConfig();
		config.network().setInputFile(netFile);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		return scenario;
	}

	private Double getEndTime(String configfile) {
		Config config = ConfigUtils.createConfig();
		MatsimConfigReader configReader = new MatsimConfigReader(config);
		configReader.readFile(configfile);
		Double endTime = config.getQSimConfigGroup().getEndTime();
		logger.info("Simulation end time is: " + endTime / 3600 + " hours.");
		logger.info("Aggregating emissions for " + (int) (endTime / 3600 / noOfTimeBins) + " hour time bins.");
		return endTime;
	}

	private static Integer getLastIteration(String configFile) {
		Config config = ConfigUtils.createConfig();
		MatsimConfigReader configReader = new MatsimConfigReader(config);
		configReader.readFile(configFile);
		Integer lastIteration = config.controler().getLastIteration();
		return lastIteration;
	}

	public static void main(String[] args) throws IOException{
		new SpatialAveraging().run();
	}
	
	protected enum AveragingMethod{
		AVERAGE, PERSQKM
	}
}