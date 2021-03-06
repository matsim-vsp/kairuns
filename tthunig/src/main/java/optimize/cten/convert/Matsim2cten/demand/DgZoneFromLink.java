/* *********************************************************************** *
 * project: org.matsim.*
 * DgZoneFromLink
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
package optimize.cten.convert.Matsim2cten.demand;

import org.locationtech.jts.geom.Coordinate;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.geometry.geotools.MGC;

public class DgZoneFromLink extends DgOriginImpl implements DgOrigin {

	private Link link;
	
	public DgZoneFromLink(Link startLink) {
		this.link = startLink;
	}
	
	public Link getLink(){
		return this.link;
	}

	@Override
	public Coordinate getCoordinate() {
		return MGC.coord2Coordinate(this.link.getCoord());
	}
	
}
