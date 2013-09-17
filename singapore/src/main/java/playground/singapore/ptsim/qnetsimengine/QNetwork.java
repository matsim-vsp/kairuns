/* *********************************************************************** *
 * project: org.matsim.*
 * QueueNetwork.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007, 2009 by the members listed in the COPYING,  *
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

package playground.singapore.ptsim.qnetsimengine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.mobsim.qsim.qnetsimengine.NetsimNetwork;
import org.matsim.core.mobsim.qsim.qnetsimengine.NetsimNode;
import org.matsim.vis.snapshotwriters.AgentSnapshotInfoFactory;
import org.matsim.vis.snapshotwriters.SnapshotLinkWidthCalculator;
import org.matsim.vis.snapshotwriters.VisLink;

import playground.singapore.transitRouterEventsBased.stopStopTimes.StopStopTime;

/**
 *
 * @author david
 * @author mrieser
 * @author dgrether
 * @author sergioo
 */

public class QNetwork implements NetsimNetwork {

	private final Map<Id, PTQLink> links;

	private final Map<Id, QNode> nodes;

	private final Network network;

	private final NetsimNetworkFactory<QNode, ? extends PTQLink> queueNetworkFactory;
	private final SnapshotLinkWidthCalculator linkWidthCalculator = new SnapshotLinkWidthCalculator();
	private final 	AgentSnapshotInfoFactory snapshotInfoFactory = new AgentSnapshotInfoFactory(linkWidthCalculator);
	
	private StopStopTime stopStopTime;
	final boolean ptSim;
	
	PTQNetsimEngine simEngine;

	QNetwork(final Network network, final NetsimNetworkFactory<QNode, ? extends PTQLink> netsimNetworkFactory) {
		this.network = network;
		this.queueNetworkFactory = netsimNetworkFactory;
		this.links = new LinkedHashMap<Id, PTQLink>((int)(network.getLinks().size()*1.1), 0.95f);
		this.nodes = new LinkedHashMap<Id, QNode>((int)(network.getLinks().size()*1.1), 0.95f);
		if (! Double.isNaN(network.getEffectiveLaneWidth())){
			this.linkWidthCalculator.setLaneWidth(network.getEffectiveLaneWidth());
		}
		String oneNodeId = network.getNodes().keySet().iterator().next().toString();
		ptSim = oneNodeId.contains("e-") || oneNodeId.contains("s-");
	}

	public void setStopStopTime(StopStopTime stopStopTime) {
		this.stopStopTime = stopStopTime;
	}

	public StopStopTime getStopStopTime() {
		return stopStopTime;
	}

	public void initialize(PTQNetsimEngine simEngine) {
		this.simEngine = simEngine;
		for (Node n : network.getNodes().values()) {
			this.nodes.put(n.getId(), this.queueNetworkFactory.createNetsimNode(n, this));
		}
		for (Link l : network.getLinks().values()) {
			this.links.put(l.getId(), this.queueNetworkFactory.createNetsimLink(l, this, this.nodes.get(l.getToNode().getId())));
		}
		for (QNode n : this.nodes.values()) {
			n.init();
		}
	}
	
	/*package*/ SnapshotLinkWidthCalculator getLinkWidthCalculator(){
		return this.linkWidthCalculator;
	}

	@Override
	public Network getNetwork() {
		return this.network;
	}

	@Override
	public Map<Id, PTQLink> getNetsimLinks() {
		return Collections.unmodifiableMap(this.links);
	}

	@Override
	public Map<Id, ? extends VisLink> getVisLinks() {
		return Collections.unmodifiableMap(this.links);
	}

	@Override
	public Map<Id, QNode> getNetsimNodes() {
		return Collections.unmodifiableMap(this.nodes);
	}

	@Override
	public PTQLink getNetsimLink(final Id id) {
		return this.links.get(id);
	}

	@Override
	public NetsimNode getNetsimNode(final Id id) {
		return this.nodes.get(id);
	}

	@Override
	public  AgentSnapshotInfoFactory getAgentSnapshotInfoFactory(){
		return this.snapshotInfoFactory;
	}


}
