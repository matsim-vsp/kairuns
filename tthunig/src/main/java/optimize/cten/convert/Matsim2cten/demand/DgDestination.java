/* *********************************************************************** *
 * project: org.matsim.*
 * DgDestination
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


/**
 * @author dgrether
 *
 */
public interface DgDestination {
	
	public String getId();
	
	public Coordinate getCoordinate();
	
	public Double getNumberOfTrips();
}
