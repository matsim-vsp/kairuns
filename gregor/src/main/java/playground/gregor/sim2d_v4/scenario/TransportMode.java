/* *********************************************************************** *
 * project: org.matsim.*
 * TransportMode.java
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

package playground.gregor.sim2d_v4.scenario;

import java.util.HashSet;
import java.util.Set;

public abstract class TransportMode {

	public static String walk = org.matsim.api.core.v01.TransportMode.walk;
	public static String walk2d = "walk2d";
	public static Set<String> transportModes;
	static {
		transportModes = new HashSet<String>();
		transportModes.add(walk);
		transportModes.add(walk2d);
		
	}
	
}
