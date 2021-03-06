/* *********************************************************************** *
 * project: org.matsim.*
 * DgMatsim2KoehlerStrehler2010ModelConverter
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
package optimize.cten.convert.Matsim2cten.network;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.signals.controller.fixedTime.DefaultPlanbasedSignalSystemController;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.conflicts.Direction;
import org.matsim.contrib.signals.data.conflicts.IntersectionDirections;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalGroupSettingsData;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalPlanData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemControllerData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsData;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.vis.vecmathutils.VectorUtils;

import optimize.cten.convert.Matsim2cten.signals.DgSignalsUtils;
import optimize.cten.data.DgCrossing;
import optimize.cten.data.DgCrossingNode;
import optimize.cten.data.DgGreen;
import optimize.cten.data.DgKSNetwork;
import optimize.cten.data.DgProgram;
import optimize.cten.data.DgStreet;
import optimize.cten.data.TtCrossingType;
import optimize.cten.data.TtRestriction;
import optimize.cten.ids.DgIdConverter;

/**
 * Class to convert a MATSim network into a KS-model network with crossings and streets. BTU Cottbus needs this network format to optimize signal plans with CPLEX.
 * 
 * @author dgrether
 * @author tthunig
 * 
 */
public class M2KS2010NetworkConverter {

	private static final Logger LOG = Logger.getLogger(M2KS2010NetworkConverter.class);

//	private static final int MIN_GREEN_RILSA = 5;
	private static final int MIN_GREEN_RILSA = 6;
	// TODO adapt this if necessary (Nicos Laemmer implemenation also uses a standard clear time of 5 seconds for all group switches)
//	private static final int DEFAULT_CLEAR_TIME = 5;
	private static final int DEFAULT_CLEAR_TIME = 3;
	// TODO adapt this if necessary (90 seconds is the cycle time of fixed-time and laemmer signals in the cottbus scenario)
	private static final int DEFAULT_CYCLE_TIME = 90;
//	private static final int DEFAULT_CYCLE_TIME = 60;

	private Integer cycle = null;

	private DgKSNetwork dgNetwork;
	private double timeInterval;

	private DgIdConverter idConverter;

	private Set<Id<Link>> signalizedLinks;
	private Map<Id<Signal>, List<Id<DgStreet>>> signalToLightsMap = new HashMap<>();
	private Map<Id<SignalSystem>, Id<DgCrossing>> systemToCrossingMap = new HashMap<>();
	private Map<Tuple<Id<Link>, Id<Link>>, Id<DgStreet>> fromToLink2LightRelation = new HashMap<>();

	private Envelope signalsBoundingBox;

	public M2KS2010NetworkConverter(DgIdConverter idConverter) {
		this.idConverter = idConverter;
	}

	public DgKSNetwork convertNetworkLanesAndSignals( Network network, Lanes lanes, SignalsData signals, double startTime, double endTime) {
		return this.convertNetworkLanesAndSignals(network, lanes, signals, null, startTime, endTime);
	}

	/**
	 * converts the given matsim network into a ks-model network with crossings and streets and returns it
	 * 
	 * @param network
	 *            the matsim network to convert
	 * @param lanes
	 * @param signals
	 * @param signalsBoundingBox
	 *            nodes within this envelop will be extended spatially
	 * @param startTime
	 *            of the simulation
	 * @param endTime
	 *            of the simulation
	 * @return the corresponding ks-model network
	 */
	public DgKSNetwork convertNetworkLanesAndSignals(Network network, Lanes lanes, SignalsData signals, Envelope signalsBoundingBox, double startTime,
			double endTime) {
		LOG.info("Checking cycle time...");
		readCycle(signals);
		LOG.info("cycle set to " + this.cycle);
		signalizedLinks = this.getSignalizedLinkIds(signals.getSignalSystemsData());
		LOG.info("Converting network ...");
		this.timeInterval = endTime - startTime;
		this.signalsBoundingBox = signalsBoundingBox;
		this.dgNetwork = this.convertNetwork(network, lanes, signals);
		LOG.info("Network converted.");
		return this.dgNetwork;
	}

	private void readCycle(SignalsData signalsData) {
		for (SignalSystemControllerData ssc : signalsData.getSignalControlData().getSignalSystemControllerDataBySystemId().values()) {
			if (ssc.getControllerIdentifier().equals(DefaultPlanbasedSignalSystemController.IDENTIFIER)) {
				for (SignalPlanData plan : ssc.getSignalPlanData().values()) {
					if (cycle == null) {
						cycle = plan.getCycleTime();
					} else if (cycle != plan.getCycleTime()) {
						throw new IllegalStateException("Signal plans must have a common cycle time!");
					}
				}
			}
			if (cycle == null) {
				// no signal controller is a fixed-time signal. use a default cycle time
				cycle = DEFAULT_CYCLE_TIME;
			}
		}
	}

	/*
	 * conversion of extended nodes: fromLink -> toLink : 2 crossing nodes + 1 light
	 */
	private DgKSNetwork convertNetwork(Network net, Lanes lanes, SignalsData signalsData) {
		DgKSNetwork ksnet = new DgKSNetwork();
		/*
		 * create a crossing for each node (crossing id generated from node id). add the single crossing node for each not extended crossing (crossing node id generated from node id).
		 */
		this.convertNodes2Crossings(ksnet, net);
		/*
		 * convert all links to streets (street id generated from link id). add extended crossing nodes for the already created corresponding extended crossings (extended crossing node id generated
		 * from adjacent link id).
		 */
		this.convertLinks2Streets(ksnet, net);

		// loop over links and create layout (i.e. lights and programs) of
		// target crossing (if it is expanded)
		for (Link link : net.getLinks().values()) {
			// the node id of the matsim network gives the crossing id
			DgCrossing crossing = ksnet.getCrossings().get(this.idConverter.convertNodeId2CrossingId(link.getToNode().getId()));
			// lights are only necessary for expanded crossings
			if (!crossing.getType().equals(TtCrossingType.NOTEXPAND)) {
				// prepare some objects/data
				Link backLink = this.getBackLink(link);
				Id<Link> backLinkId = (backLink == null) ? null : backLink.getId();
				DgCrossingNode inLinkToNode = crossing.getNodes().get(this.idConverter.convertLinkId2ToCrossingNodeId(link.getId()));
				LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(link.getId());
				// create crossing layout
				if (signalizedLinks.contains(link.getId())) {
					LOG.debug("link: " + link.getId() + " is signalized...");
					SignalSystemData system = this.getSignalSystem4SignalizedLinkId(signalsData.getSignalSystemsData(), link.getId());
					// remember system - crossing relation
					systemToCrossingMap.put(system.getId(), crossing.getId());
					this.createCrossing4SignalizedLink(net, crossing, link, inLinkToNode, backLinkId, l2l, system, signalsData);
				} else {
					LOG.debug("link: " + link.getId() + " not signalized...");
					this.createCrossing4NotSignalizedLink(net, crossing, link, inLinkToNode, backLinkId, l2l);
				}
			}
		}

		// create restrictions for "flexible" crossings
		for (SignalSystemData system : signalsData.getSignalSystemsData().getSignalSystemData().values()) {
			DgCrossing crossing = ksnet.getCrossings().get(systemToCrossingMap.get(system.getId()));
			if (!crossing.getType().equals(TtCrossingType.FLEXIBLE)) {
				// only add restrictions for crossings with type flexible
				continue;
			}
			// fill on- and off-light information based on signal groups
			Map<Id<SignalGroup>, SignalGroupData> groupsOfThisSystem = signalsData.getSignalGroupsData()
					.getSignalGroupDataBySystemId(system.getId());
			// preprocessing: look for groups of signals of this system
			Map<Id<Signal>, Id<SignalGroup>> signalToGroupMap = new HashMap<>();
			for (SignalGroupData group : groupsOfThisSystem.values()) {
				for (Id<Signal> signalId : group.getSignalIds()) {
					signalToGroupMap.put(signalId, group.getId());
				}
			}
			for (Id<Signal> signalId : system.getSignalData().keySet()) {
				SignalGroupData groupOfThisSignal = groupsOfThisSystem.get(signalToGroupMap.get(signalId));
				// a signal in MATSim can correspond to more than one light in the ks network (if different turns are allowed)
				for (Id<DgStreet> lightId : signalToLightsMap.get(signalId)) {
					if (!crossing.getRestrictions().containsKey(lightId)) {
						crossing.addRestriction(new TtRestriction(lightId, false));
						/* note: we have to continue, also if the restriction already exists, because signals leading 
						 * to the same light do not necessarily belong to the same group in the Cottbus scenario */
					}
					TtRestriction restriction = crossing.getRestrictions().get(lightId);
					// go through all other lights of the system and check whether they belong together (on/off-light)
					for (Id<Signal> otherSignalId : system.getSignalData().keySet()) {
						for (Id<DgStreet> otherLightId : signalToLightsMap.get(otherSignalId)) {
							if (lightId.equals(otherLightId)) {
								// found itself
								continue;
							}
							if (groupOfThisSignal.getSignalIds().contains(otherSignalId)) {
								// same group - add all corresponding lights to "on" and "off" rlight
								restriction.addOnLight(otherLightId);
								restriction.addOffLight(otherLightId);

							} else if (signalsData.getConflictingDirectionsData() == null) {
								// different group and no conflict data exists - do not allow green at the same
								// time (as for Nicos Laemmer implementation) 
								// i.e. add all corresponding lights as rlight with allowed=false
								restriction.addAllowedLight(otherLightId, DEFAULT_CLEAR_TIME, DEFAULT_CLEAR_TIME);
							}
						}
					}
				}
			}
			// if conflict data exists, fill restriction information based on conflicting directions
			if (signalsData.getConflictingDirectionsData() != null) {
				// create restrictions based on data about conflicting directions
				IntersectionDirections directionsOfThisSystem = signalsData.getConflictingDirectionsData()
						.getConflictsPerSignalSystem().get(system.getId());
				for (Direction dir : directionsOfThisSystem.getDirections().values()) {
					// directions are defined between from and to links. lights in the ks network
					// are also defined between from and to links. i.e. every direction corresponds
					// to exactly one light
					Id<DgStreet> lightId = fromToLink2LightRelation
							.get(new Tuple<Id<Link>, Id<Link>>(dir.getFromLink(), dir.getToLink()));
					if (lightId == null) {
						throw new RuntimeException(
								"No light exists for direction " + dir.getId() + " with from Link " + dir.getFromLink()
										+ " and to link " + dir.getToLink() + ", i.e. the turn is not allowed.");
					}
					TtRestriction r = crossing.getRestrictions().get(lightId);
					// add all conflicting directions as restrictions - not only to the direction itself, but also to all on- and off-lights
					for (Id<Direction> otherDirId : dir.getConflictingDirections()) {
						Direction otherDir = directionsOfThisSystem.getDirections().get(otherDirId);
						Id<DgStreet> otherLightId = fromToLink2LightRelation
								.get(new Tuple<Id<Link>, Id<Link>>(otherDir.getFromLink(), otherDir.getToLink()));
						if (otherLightId == null) {
							throw new RuntimeException("No light exists for direction " + otherDir.getId()
									+ " with from Link " + otherDir.getFromLink() + " and to link "
									+ otherDir.getToLink() + ", i.e. the turn is not allowed.");
						}
						// add the conflicted light itself as restriction
						r.addAllowedLight(otherLightId, DEFAULT_CLEAR_TIME, DEFAULT_CLEAR_TIME);
						// also add all on- and off-lights of the conflicted light as restriction
						TtRestriction otherR = crossing.getRestrictions().get(otherLightId);
						for (Id<DgStreet> onOffLightOfOtherR : otherR.getRlightsOn()) {
							if (otherR.getRlightsOff().contains(onOffLightOfOtherR)) {
								r.addAllowedLight(onOffLightOfOtherR, DEFAULT_CLEAR_TIME, DEFAULT_CLEAR_TIME);
							}
						}
						// add all these restrictions also to all on- and off-lights of the main direction 'dir' from the outer loop above
						for (Id<DgStreet> onOffLightOfR : r.getRlightsOn()) {
							if (r.getRlightsOff().contains(onOffLightOfR)) {
								crossing.getRestrictions().get(onOffLightOfR).addAllowedLight(otherLightId, DEFAULT_CLEAR_TIME, DEFAULT_CLEAR_TIME);
							}
						}
						// with this, on- and off-lights should always have the same restrictions
					}
				}
			}
		}
		return ksnet;
	}

	/**
	 * creates crossings in ksNet for all nodes in the matsim network net. if a node lies within the signals bounding box (i.e. should be expanded) this method creates a corresponding crossing with
	 * the type "fixed" (if the node is signalized) or "equalRank" (else) respectively. this crossing has no crossing nodes so far. if a node lies outside the signals bounding box this method creates
	 * the complete crossing, which gets the type "notExpand" and a single crossing node.
	 * 
	 * @param ksNet
	 * @param net
	 */
	private void convertNodes2Crossings(DgKSNetwork ksNet, Network net) {
		for (Node node : net.getNodes().values()) {
			DgCrossing crossing = new DgCrossing(this.idConverter.convertNodeId2CrossingId(node.getId()));

			// create crossing type
			Coordinate nodeCoordinate = MGC.coord2Coordinate(node.getCoord());
			if (// there is no signals bounding box to stop expansion -> all nodes will be expanded
			this.signalsBoundingBox == null ||
			// OR: node is within the signals bounding box
					this.signalsBoundingBox.contains(nodeCoordinate)) {

				// create (default) crossing type "fixed" if node is signalized, "equalRank" else
				for (Link link : node.getInLinks().values()) {
					// node is signalized. set fixed as default type - may be overwritten later in createCrossing4SignalizedLink()
					if (signalizedLinks.contains(link.getId())) {
						crossing.setType(TtCrossingType.FIXED);
					}
				}
				// node isn't signalized, but within the signals bounding box
				if (crossing.getType() == null) {
					crossing.setType(TtCrossingType.EQUALRANK);
				}
			} else { // node is outside the signals bounding box
				crossing.setType(TtCrossingType.NOTEXPAND);
				// create and add the single crossing node of the not expanded crossing
				DgCrossingNode crossingNode = new DgCrossingNode(this.idConverter.convertNodeId2NotExpandedCrossingNodeId(node.getId()));
				crossingNode.setCoordinate(node.getCoord());
				crossing.addNode(crossingNode);
			}

			ksNet.addCrossing(crossing);
		}
	}

	/**
	 * creates streets in ksnet for all links in the matsim network net. if a from or to node of a link lies within the signals bounding box (i.e. should be expanded) this method creates a new
	 * crossing node for this street in the expanded network ksnet. if a node lies outside the signals bounding box the single crossing node for the not expanded crossing already exists (see
	 * convertNodes2Crossings(...)) and is used by this method to create the street.
	 * 
	 * @param ksnet
	 * @param net
	 */
	private void convertLinks2Streets(DgKSNetwork ksnet, Network net) {

		for (Link link : net.getLinks().values()) {
			Node mFromNode = link.getFromNode();
			Node mToNode = link.getToNode();
			Tuple<Coord, Coord> startEnd = this.scaleLinkCoordinates(link.getLength(), mFromNode.getCoord(), mToNode.getCoord());

			// get from node
			DgCrossing fromNodeCrossing = ksnet.getCrossings().get(this.idConverter.convertNodeId2CrossingId(mFromNode.getId()));
			DgCrossingNode fromNode;
			// create from node for expanded crossings
			if (!fromNodeCrossing.getType().equals(TtCrossingType.NOTEXPAND)) {
				fromNode = new DgCrossingNode(this.idConverter.convertLinkId2FromCrossingNodeId(link.getId()));
				fromNode.setCoordinate(startEnd.getFirst());
				fromNodeCrossing.addNode(fromNode);
			} else { // node for not expanded crossing already exists
				fromNode = fromNodeCrossing.getNodes().get(this.idConverter.convertNodeId2NotExpandedCrossingNodeId(mFromNode.getId()));
			}

			// get to node
			DgCrossing toNodeCrossing = ksnet.getCrossings().get(this.idConverter.convertNodeId2CrossingId(mToNode.getId()));
			DgCrossingNode toNode;
			// create to node for expanded crossings
			if (!toNodeCrossing.getType().equals(TtCrossingType.NOTEXPAND)) {
				toNode = new DgCrossingNode(this.idConverter.convertLinkId2ToCrossingNodeId(link.getId()));
				toNode.setCoordinate(startEnd.getSecond());
				toNodeCrossing.addNode(toNode);
			} else { // node for not expanded crossing already exists
				toNode = toNodeCrossing.getNodes().get(this.idConverter.convertNodeId2NotExpandedCrossingNodeId(mToNode.getId()));
			}

			DgStreet street = new DgStreet(this.idConverter.convertLinkId2StreetId(link.getId()), fromNode, toNode);
			double fsd = link.getLength() / link.getFreespeed();
			// this is the earliest time where matsim sets the agent to the next link
			long matsimFsd = (long) Math.floor(fsd + 1);
			if (matsimFsd != 0) {
				street.setCost(matsimFsd);
			} else {
				LOG.warn("Street id " + street.getId() + " has a freespeed tt of " + fsd + " that is rounded to " + matsimFsd + " replacing by 1");
				street.setCost(1);
			}
//			// old travel time conversion:
//			long fs = Math.round(fsd);
//			if (fs != 0) {
//				street.setCost(fs);
//			} else {
//				LOG.warn("Street id " + street.getId() + " has a freespeed tt of " + fsd + " that is rounded to " + fs + " replacing by 1");
//				street.setCost(0);
//			}
			double capacity = link.getCapacity() / net.getCapacityPeriod() * this.timeInterval;
			street.setCapacity(capacity);
			ksnet.addStreet(street);
		}
	}

	/**
	 * scales the link start and end coordinates based on a node offset to create extended crossing nodes, i.e. the link will be shortened at the beginning and the end. the scaled start coordinate
	 * gives the coordinate for the extended crossing node corresponding to the from crossing of the link. the scaled end coordinate gives this information for the to crossing of the link.
	 * 
	 * @param linkLength
	 *            currently not used. we use the euclidean distance between start and end coordinate as link length.
	 * @param linkStartCoord
	 *            the start coordinate of the link
	 * @param linkEndCoord
	 *            the end coordinate of the link
	 * @return a tuple of the scaled start and end coordinates, so the coordinates for the extended crossing nodes
	 */
	private Tuple<Coord, Coord> scaleLinkCoordinates(double linkLength, Coord linkStartCoord, Coord linkEndCoord) {
		double nodeOffsetMeter = 20.0;
		Point2D.Double linkStart = new Point2D.Double(linkStartCoord.getX(), linkStartCoord.getY());
		Point2D.Double linkEnd = new Point2D.Double(linkEndCoord.getX(), linkEndCoord.getY());

		// calculate length and normal
		Point2D.Double deltaLink = new Point2D.Double(linkEnd.x - linkStart.x, linkEnd.y - linkStart.y);
		double euclideanLinkLength = this.calculateEuclideanLinkLength(deltaLink);
		// //calculate the correction factor if real link length is different
		// than euclidean distance
		// double linkLengthCorrectionFactor = euclideanLinkLength / linkLength;
		// Point2D.Double deltaLinkNorm = new Point2D.Double(deltaLink.x /
		// euclideanLinkLength, deltaLink.y / euclideanLinkLength);
		// Point2D.Double normalizedOrthogonal = new
		// Point2D.Double(deltaLinkNorm.y, - deltaLinkNorm.x);

		// first calculate the scale of the link based on the node offset, i.e.
		// the link will be shortened at the beginning and the end
		double linkScale = 1.0;
		if ((euclideanLinkLength * 0.2) > (2.0 * nodeOffsetMeter)) { // 2*
																		// nodeoffset
																		// is
																		// less
																		// than
																		// 20%
			linkScale = (euclideanLinkLength - (2.0 * nodeOffsetMeter)) / euclideanLinkLength;
		} else { // use 80 % as euclidean length (because nodeoffset is to big)
			linkScale = euclideanLinkLength * 0.8 / euclideanLinkLength;
		}

		// scale link
		Tuple<Double, Double> scaledLink = VectorUtils.scaleVector(linkStart, linkEnd, linkScale);
		Point2D.Double scaledLinkEnd = scaledLink.getSecond();
		Point2D.Double scaledLinkStart = scaledLink.getFirst();
		Coord start = new Coord(scaledLinkStart.x, scaledLinkStart.y);
		Coord end = new Coord(scaledLinkEnd.x, scaledLinkEnd.y);
		return new Tuple<Coord, Coord>(start, end);
	}

	private double calculateEuclideanLinkLength(Point2D.Double deltaLink) {
		return Math.sqrt(Math.pow(deltaLink.x, 2) + Math.pow(deltaLink.y, 2));
	}

	private static Tuple<SignalPlanData, SignalGroupSettingsData> getPlanAndSignalGroupSettings4Signal(Id<SignalSystem> signalSystemId,
			Id<Signal> signalId, SignalsData signalsData) {
		SignalSystemControllerData controllData = signalsData.getSignalControlData().getSignalSystemControllerDataBySystemId().get(signalSystemId);
		SignalPlanData signalPlan = controllData.getSignalPlanData().values().iterator().next();
		SignalGroupData signalGroup = DgSignalsUtils.getSignalGroup4SignalId(signalSystemId, signalId, signalsData.getSignalGroupsData());
		return new Tuple<SignalPlanData, SignalGroupSettingsData>(signalPlan,
				signalPlan.getSignalGroupSettingsDataByGroupId().get(signalGroup.getId()));
	}

	/**
	 * creates a light (i.e. a street representing the connection of crossing nodes of one crossing) between the inLink crossing node and the outLink crossing node. lights are only used for extended
	 * crossings. so the outLink gives the outLink crossing node.
	 * 
	 * @param fromLink
	 *            the inLink in the matsim network
	 * @param fromLane
	 * @param outLinkId
	 *            the matsim id of the outLink
	 * @param backLinkId
	 *            the back link id of the fromLink
	 * @param inLinkToNode
	 *            the corresponding target crossing node of the fromLink
	 * @param crossing
	 *            the target crossing of the fromLink
	 * @return the id of the created light
	 */
	private Id<DgStreet> createLights( Network net, Link fromLink, Lane fromLane, Id<Link> outLinkId, Id<Link> backLinkId, DgCrossingNode inLinkToNode,
						     DgCrossing crossing, Id<Signal> signalId) {
		if (backLinkId != null && backLinkId.equals(outLinkId)) {
			return null; // do nothing if it is the backlink
		}
		Id<DgStreet> lightId = this.idConverter.convertFromLinkIdToLinkId2LightId(fromLink.getId(), (fromLane != null)? fromLane.getId() : null, outLinkId);
		LOG.debug("    light id: " + lightId);
		Id<Link> convertedOutLinkId = Id.create(this.idConverter.convertLinkId2FromCrossingNodeId(outLinkId), Link.class);
		LOG.debug("    outLinkId : " + outLinkId + " converted id: " + convertedOutLinkId);
		DgCrossingNode outLinkFromNode = crossing.getNodes().get(convertedOutLinkId);
		if (outLinkFromNode == null) {
			LOG.error("Crossing " + crossing.getId() + " has no node with id " + convertedOutLinkId);
			throw new IllegalStateException("outLinkFromNode not found.");
			// return null;
		}
		for (DgStreet crossingLight : crossing.getLights().values()) {
			if (crossingLight.getFromNode().equals(inLinkToNode) && crossingLight.getToNode().equals(outLinkFromNode)) {
				if (signalId != null)
					rememberLightSignalRelation(crossingLight.getId(), signalId);
				return null; // same light exists already
			}
		}
		DgStreet street = new DgStreet(lightId, inLinkToNode, outLinkFromNode);
		street.setCost(0);
		if (fromLane != null) {
			street.setCapacity(fromLane.getCapacityVehiclesPerHour() / net.getCapacityPeriod() * this.timeInterval);
		} else {
			street.setCapacity(fromLink.getCapacity() / net.getCapacityPeriod() * this.timeInterval);
		}
		if (crossing.getType().equals(TtCrossingType.FLEXIBLE)) {
			// TODO adapt this when no min green time should be used
			street.setMinGreen(MIN_GREEN_RILSA);
		}
		crossing.addLight(street);
		if (signalId != null)
			rememberLightSignalRelation(lightId, signalId);
		fromToLink2LightRelation.put(new Tuple<Id<Link>, Id<Link>>(fromLink.getId(), outLinkId), lightId);
		
		return lightId;
	}

	/**
	 * creates the crossing layout (lights and programs) for the target crossing of signalized links. Maps a signalized MATSim Link's turning moves and signalization to lights and greens, i.e. 1
	 * allowed turning move => 1 light + 1 green Turning moves are given by: a) the outLinks of the toNode of the Link, if no lanes are given and there are no turning move restrictions set for the
	 * Signal b) the turning move restrictions of multiple signals attached to the link d) the turing move restrictions of the signal, if it is attached to a lane c) the toLinks of the lanes attached
	 * to the link, if there are no turning move restrictions for the signal If there are several signals without turning move restrictions on a link or a lane nothing can be created because this is
	 * an inconsistent state of the input data: thus the programs/plans for the signal might be ambiguous, an exception is thrown.
	 * 
	 * @param crossing
	 *            the target crossing of the link
	 * @param link
	 * @param inLinkToNode
	 *            the corresponding target crossing node of the link
	 * @param backLinkId
	 * @param l2l
	 * @param system
	 * @param signalsData
	 */
	private void createCrossing4SignalizedLink(Network net, DgCrossing crossing, Link link, DgCrossingNode inLinkToNode, Id<Link> backLinkId,
			LanesToLinkAssignment l2l, SignalSystemData system, SignalsData signalsData) {

		DgProgram program = null;
		if (signalsData.getSignalControlData().getSignalSystemControllerDataBySystemId().get(system.getId()).getControllerIdentifier()
				.equals(DefaultPlanbasedSignalSystemController.IDENTIFIER)) {
			// it is a fixed-time signal
			crossing.setType(TtCrossingType.FIXED);
			// create program for fixed crossings
			Id<DgProgram> programId = idConverter.convertSignalSystemId2ProgramId(system.getId());
			if (!crossing.getPrograms().containsKey(programId)) {
				program = new DgProgram(programId);
				program.setCycle(this.cycle);
				crossing.addProgram(program);
			} else {
				program = crossing.getPrograms().get(programId);
			}
		} else {
			// it is a flexible signal
			crossing.setType(TtCrossingType.FLEXIBLE);
			crossing.setClearTime(DEFAULT_CLEAR_TIME);
			crossing.setCycle(this.cycle);
		}

		List<SignalData> signals4Link = this.getSignals4LinkId(system, link.getId());
		// first get the outlinks that are controlled by the signal
		for (SignalData signal : signals4Link) {
			LOG.debug("    signal: " + signal.getId() + " system: " + system.getId());
			Id<DgStreet> lightId = null;
			if (l2l == null) {
				Set<Id<Link>> outLinkIds = new HashSet<>();
				if (signals4Link.size() > 1 && (signal.getTurningMoveRestrictions() == null || signal.getTurningMoveRestrictions().isEmpty())) {
					throw new IllegalStateException("more than one signal on one link but no lanes and no turning move restrictions is not allowed");
				} else if (signal.getTurningMoveRestrictions() == null || signal.getTurningMoveRestrictions().isEmpty()) {
					outLinkIds.addAll(this.getTurningMoves4LinkWoLanes(link));
				} else { // we have turning move restrictions
					outLinkIds = signal.getTurningMoveRestrictions();
				}
				// create lights and green settings
				for (Id<Link> outLinkId : outLinkIds) {
					LOG.debug("    outLinkId: " + outLinkId);
					lightId = this.createLights(net, link, null, outLinkId, backLinkId, inLinkToNode, crossing, signal.getId());
					if (lightId != null) {
						LOG.debug("    created Light " + lightId);
						fillProgramForFixedCrossings(system, signalsData, program, signal, lightId);
					}
				}
			} else { // link with lanes
				for (Id<Lane> laneId : signal.getLaneIds()) {
					Lane lane = l2l.getLanes().get(laneId);
					if (signal.getTurningMoveRestrictions() == null || signal.getTurningMoveRestrictions().isEmpty()) {
						// no turning move restrictions for signal -> outlinks come from lane
						for (Id<Link> outLinkId : lane.getToLinkIds()) {
							LOG.debug("    outLinkId: " + outLinkId);
							lightId = this.createLights(net, link, lane, outLinkId, backLinkId, inLinkToNode, crossing, signal.getId());
							if (lightId != null) {
								LOG.debug("    created Light " + lightId);
								fillProgramForFixedCrossings(system, signalsData, program, signal, lightId);
							}
						}
					} else { // turning move restrictions on signal -> outlinks taken from signal
						for (Id<Link> outLinkId : signal.getTurningMoveRestrictions()) {
							LOG.debug("    outLinkId: " + outLinkId);
							lightId = this.createLights(net, link, lane, outLinkId, backLinkId, inLinkToNode, crossing, signal.getId());
							if (lightId != null) {
								LOG.debug("    created Light " + lightId);
								fillProgramForFixedCrossings(system, signalsData, program, signal, lightId);
							}
						}
					}
				}
			}
		}
	}

	private void rememberLightSignalRelation(Id<DgStreet> lightId, Id<Signal> signalId) {
		if (!signalToLightsMap.containsKey(signalId)) {
			signalToLightsMap.put(signalId, new LinkedList<>());
		}
		signalToLightsMap.get(signalId).add(lightId);
	}

	private static void fillProgramForFixedCrossings(SignalSystemData system, SignalsData signalsData, DgProgram program, SignalData signal,
			Id<DgStreet> lightId) {
		if (program != null) {
			Tuple<SignalPlanData, SignalGroupSettingsData> planGroupSettings = getPlanAndSignalGroupSettings4Signal(system.getId(), signal.getId(),
					signalsData);
			SignalPlanData signalPlan = planGroupSettings.getFirst();
			SignalGroupSettingsData groupSettings = planGroupSettings.getSecond();
			createAndAddGreen4Settings(lightId, program, groupSettings, signalPlan);
		}
	}

	/**
	 * creates the crossing layout (lights without programs) for the target crossing of non signalized links.
	 * 
	 * @param crossing
	 *            the target crossing of the link
	 * @param link
	 * @param inLinkToNode
	 *            the corresponding target crossing node of the link
	 * @param backLinkId
	 * @param l2l
	 */
	private void createCrossing4NotSignalizedLink(Network net, DgCrossing crossing, Link link, DgCrossingNode inLinkToNode, Id<Link> backLinkId,
			LanesToLinkAssignment l2l) {

		if (l2l == null) { // create lights for link without lanes
			List<Id<Link>> toLinks = this.getTurningMoves4LinkWoLanes(link);
			for (Id<Link> outLinkId : toLinks) {
				this.createLights(net, link, null, outLinkId, backLinkId, inLinkToNode, crossing, null);
			}
		} else {
			for (Lane lane : l2l.getLanes().values()) {
				// check for outlanes (create only lights for lanes without
				// outlanes, i.e. "last lanes" of a link)
				if (lane.getToLaneIds() == null || lane.getToLaneIds().isEmpty()) {
					for (Id<Link> outLinkId : lane.getToLinkIds()) {
						this.createLights(net, link, lane, outLinkId, backLinkId, inLinkToNode, crossing, null);
					}
				}
			}
		}
	}

	// TODO check this again which offset is needed for green
	private static void createAndAddGreen4Settings(Id<DgStreet> lightId, DgProgram program, SignalGroupSettingsData groupSettings,
			SignalPlanData signalPlan) {
		DgGreen green = new DgGreen(Id.create(lightId, DgGreen.class));
		green.setOffset(groupSettings.getOnset());
		green.setLength(calculateGreenTimeSeconds(groupSettings, signalPlan.getCycleTime()));
		LOG.debug("    green time " + green.getLength() + " offset: " + green.getOffset());
		program.addGreen(green);
	}

	private static int calculateGreenTimeSeconds(SignalGroupSettingsData settings, Integer cycle) {
		if (settings.getOnset() <= settings.getDropping()) {
			return settings.getDropping() - settings.getOnset();
		} else {
			return settings.getDropping() + (cycle - settings.getOnset());
		}
	}

	@Deprecated
	private void createAndAddAllTimeGreen(Id<DgGreen> lightId, DgProgram program) {
		DgGreen green = new DgGreen(lightId);
		green.setLength(this.cycle);
		green.setOffset(0);
		program.addGreen(green);
	}

	private SignalSystemData getSignalSystem4SignalizedLinkId(SignalSystemsData signalSystems, Id<Link> linkId) {
		for (SignalSystemData system : signalSystems.getSignalSystemData().values()) {
			for (SignalData signal : system.getSignalData().values()) {
				if (signal.getLinkId().equals(linkId)) {
					return system;
				}
			}
		}
		return null;
	}

	private List<SignalData> getSignals4LinkId(SignalSystemData system, Id<Link> linkId) {
		List<SignalData> signals4Link = new ArrayList<SignalData>();
		for (SignalData signal : system.getSignalData().values()) {
			if (signal.getLinkId().equals(linkId)) {
				signals4Link.add(signal);
			}
		}
		return signals4Link;
	}

	private Link getBackLink(Link link) {
		for (Link outLink : link.getToNode().getOutLinks().values()) {
			if (link.getFromNode().equals(outLink.getToNode())) {
				return outLink;
			}
		}
		// no back link has been found - check whether there exists one with similar angle
		for (Link outLink : link.getToNode().getOutLinks().values()) {
			if (angle(link, outLink) < Math.PI/9) {
//				Log.warn("in-back link pair: " + link.getId() + ", " + outLink.getId());
				// the angle between the links is less than 20 degrees. consider it as the back link
				return outLink;
			}
		}
		return null;
	}

	private double angle(Link inLink, Link outLink) {
		double inVector_x = inLink.getFromNode().getCoord().getX() - inLink.getToNode().getCoord().getX();
		double inVector_y = inLink.getFromNode().getCoord().getY() - inLink.getToNode().getCoord().getY();
		double outVector_x = outLink.getToNode().getCoord().getX() - outLink.getFromNode().getCoord().getX();
		double outVector_y = outLink.getToNode().getCoord().getY() - outLink.getFromNode().getCoord().getY();
		
		double lengthIn = Math.sqrt(Math.pow(inVector_x, 2) + Math.pow(inVector_y, 2));
		double lengthOut = Math.sqrt(Math.pow(outVector_x, 2) + Math.pow(outVector_y, 2));
		
		double skalarInOut = inVector_x * outVector_x + inVector_y * outVector_y;
		
		double cosAngle = skalarInOut / (lengthIn * lengthOut);
		
		return Math.acos(cosAngle);
	}

	private List<Id<Link>> getTurningMoves4LinkWoLanes(Link link) {
		List<Id<Link>> outLinks = new ArrayList<>();
		for (Link outLink : link.getToNode().getOutLinks().values()) {
			if (!link.getFromNode().equals(outLink.getToNode())) {
				outLinks.add(outLink.getId());
			}
		}
		return outLinks;
	}

	private Set<Id<Link>> getSignalizedLinkIds(SignalSystemsData signals) {
		Map<Id<SignalSystem>, Set<Id<Link>>> signalizedLinksPerSystem = DgSignalsUtils.calculateSignalizedLinksPerSystem(signals);
		Set<Id<Link>> signalizedLinks = new HashSet<>();
		for (Set<Id<Link>> signalizedLinksOfSystem : signalizedLinksPerSystem.values()) {
			signalizedLinks.addAll(signalizedLinksOfSystem);
		}
		return signalizedLinks;
	}

}
