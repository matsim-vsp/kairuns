/* *********************************************************************** *
 * project: org.matsim.*
 * Counts2QGIS.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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
package playground.yu.utils.qgis;

import java.util.Collection;
import java.util.Set;

import org.geotools.feature.Feature;
import org.geotools.feature.FeatureType;
import org.geotools.feature.IllegalAttributeException;
import org.geotools.feature.SchemaException;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.gbl.Gbl;
import org.matsim.counts.Counts;
import org.matsim.counts.MatsimCountsReader;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

/**
 * write a QGIS-.shp-file, in which only links with counts stations can be
 * written.
 * 
 * @author yu
 * 
 */
public class Counts2QGIS extends MATSimNet2QGIS {
	public Counts2QGIS(String netFilename, String coordRefSys) {
		super(netFilename, coordRefSys);
	}

	public static class Counts2PolygonGraph extends Network2PolygonGraph {
		private Set<Id> linkIds = null;

		public Counts2PolygonGraph(final Network network,
				final CoordinateReferenceSystem crs, final Set<Id> linkIds) {
			super(network, crs);
			this.linkIds = linkIds;
		}

		@Override
		public Collection<Feature> getFeatures() throws SchemaException,
				NumberFormatException, IllegalAttributeException {
			for (int i = 0; i < attrTypes.size(); i++) {
				defaultFeatureTypeFactory.addType(attrTypes.get(i));
			}
			FeatureType ftRoad = defaultFeatureTypeFactory.getFeatureType();
			for (Id linkId : linkIds) {
				Link link = network.getLinks().get(linkId);
				LinearRing lr = getLinearRing(link);
				Polygon p = new Polygon(lr, null, geofac);
				MultiPolygon mp = new MultiPolygon(new Polygon[] { p }, geofac);
				int size = 7 + parameters.size();
				Object[] o = new Object[size];
				o[0] = mp;
				o[1] = link.getId().toString();
				o[2] = link.getFromNode().getId().toString();
				o[3] = link.getToNode().getId().toString();
				o[4] = link.getLength();
				o[5] = link.getCapacity() / network.getCapacityPeriod()
						* 3600.0;
				o[6] = link.getFreespeed();
				for (int i = 0; i < parameters.size(); i++) {
					o[i + 7] = parameters.get(i).get(link.getId());
				}
				// parameters.get(link.getId().toString()) }
				Feature ft = ftRoad.create(o, "network");
				features.add(ft);
			}
			return features;
		}

		@Override
		protected double getLinkWidth(final Link link) {
			return super.getLinkWidth(link) * 2.0;
		}

	}

	protected Counts counts;

	protected Set<Id> readCounts(final String countsFilename) {
		counts = new Counts();
		new MatsimCountsReader(counts).readFile(countsFilename);
		System.out.println("size :\t" + counts.getCounts().keySet().size());
		return counts.getCounts().keySet();
	}

	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		Gbl.startMeasurement();

		String netFilename = "../berlin/network/bb_5_hermannstr.xml.gz";
		String countsFilename = "../berlin/counts/counts4bb_5_hermannstr_counts4Kantstr.xml";

		Counts2QGIS c2q = new Counts2QGIS(netFilename, gk4);
		c2q.setN2g(new Counts2PolygonGraph(c2q.getNetwork(), c2q.crs, c2q
				.readCounts(countsFilename)));
		c2q.writeShapeFile("../matsimTests/berlinQGIS/counts4bb_5_hermannstr_counts4Kantstr.shp");

		Gbl.printElapsedTime();
	}
}
