/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
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

package playground.agarwalamit.corineLandcover;

import java.util.*;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import org.apache.log4j.Logger;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;
import playground.agarwalamit.utils.GeometryUtils;

/**
 * Created by amit on 31.07.17.
 */

public class LandCoverInformer {

    public static final Logger LOGGER = Logger.getLogger(LandCoverInformer.class);
    private final Map<ActivityTypeFromLandCover, Geometry> activityType2LandcoverZone = new HashMap<>();

    public LandCoverInformer(final String corineLandCoverShapeFile) {
        LOGGER.info("Reading CORINE landcover shape file . . .");
        Collection<SimpleFeature> landCoverFeatures = ShapeFileReader.getAllFeatures(corineLandCoverShapeFile);

        LOGGER.info("Merging the geometries of the same activity types ...");
        Map<ActivityTypeFromLandCover, List<Geometry>> activityTypes2ListOfGeometries = new HashMap<>();

        for (SimpleFeature lancoverZone : landCoverFeatures) {
            int landCoverId = Integer.valueOf( (String) lancoverZone.getAttribute(LandCoverUtils.CORINE_LANDCOVER_TAG_ID));
            List<ActivityTypeFromLandCover> acts = LandCoverUtils.getActivitiesTypeFromZone(landCoverId);

            for (ActivityTypeFromLandCover activityTypeFromLandCover : acts ) {
                List<Geometry> geoms = activityTypes2ListOfGeometries.get(activityTypeFromLandCover);
                if (geoms==null) geoms = new ArrayList<>();

                geoms.add(  (Geometry)lancoverZone.getDefaultGeometry() );
                activityTypes2ListOfGeometries.put(activityTypeFromLandCover, geoms);
            }
        }

        // combined geoms of the same activity types
        for (ActivityTypeFromLandCover activityTypeFromLandCover : activityTypes2ListOfGeometries.keySet()) {
            activityType2LandcoverZone.put(activityTypeFromLandCover, GeometryUtils.combine(activityTypes2ListOfGeometries.get(activityTypeFromLandCover)));
        }
    }

    public static void main(String[] args) {
        String landcoverFile = "../../repos/shared-svn/projects/nemo_mercator/30_Scenario/cemdap_input/shapeFiles/CORINE_landcover_nrw/corine_nrw_src_clc12.shp";
        String zoneFile = "../../repos/shared-svn/projects/nemo_mercator/30_Scenario/cemdap_input/shapeFiles/sourceShape_NRW/dvg2gem_nw.shp";
        new LandCoverInformer(landcoverFile);
    }


    public Point getRandomPoint (final SimpleFeature feature, final  ActivityTypeFromLandCover activityTypeFromLandCover) {
        List<Geometry> geoms = new ArrayList<>();
        geoms.add(  (Geometry) feature.getDefaultGeometry() );
        geoms.add( this.activityType2LandcoverZone.get(activityTypeFromLandCover) );
        return GeometryUtils.getRandomPointCommonToAllGeometries( geoms  );
    }
}