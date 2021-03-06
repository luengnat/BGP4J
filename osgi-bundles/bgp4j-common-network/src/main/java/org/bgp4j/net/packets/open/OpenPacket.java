/**
 *  Copyright 2012 Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 */
package org.bgp4j.net.packets.open;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.bgp4j.net.BGPv4Constants;
import org.bgp4j.net.capabilities.Capability;
import org.bgp4j.net.packets.BGPv4Packet;

/**
 * @author Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 */
public class OpenPacket extends BGPv4Packet {
	private int protocolVersion;
	private int autonomousSystem;
	private int holdTime;
	private long bgpIdentifier;
	private List<Capability> capabilities = new LinkedList<Capability>();
	
	public OpenPacket() {}
	
	public OpenPacket(int protocolVersion) {
		this.protocolVersion = protocolVersion;
	}
	
	public OpenPacket(int protocolVersion, int autonomousSysten, long bgpIdentifier) {
		this(protocolVersion);
		
		this.autonomousSystem = autonomousSysten;
		this.bgpIdentifier = bgpIdentifier;
	}
	
	public OpenPacket(int protocolVersion, int autonomousSysten, long bgpIdentifier, int holdTime) {
		this(protocolVersion, autonomousSysten, bgpIdentifier);
		
		this.holdTime = holdTime;
	}
	
	public OpenPacket(int protocolVersion, int autonomousSysten, long bgpIdentifier, int holdTime, Collection<Capability> capabilities) {
		this(protocolVersion, autonomousSysten, bgpIdentifier, holdTime);

		this.capabilities.addAll(capabilities);
	}
	
	
	public OpenPacket(int protocolVersion, int autonomousSysten, long bgpIdentifier, int holdTime, Capability[] capabilities) {
		this(protocolVersion, autonomousSysten, bgpIdentifier, holdTime);

		for(Capability cap : capabilities)
			this.capabilities.add(cap);
	}
	
	/**
	 * @return the protocolVersion
	 */
	public int getProtocolVersion() {
		return protocolVersion;
	}
	
	/**
	 * @param protocolVersion the protocolVersion to set
	 */
	public void setProtocolVersion(int protocolVersion) {
		this.protocolVersion = protocolVersion;
	}
	
	/**
	 * @return the autonomuosSystem
	 */
	public int getAutonomousSystem() {
		return autonomousSystem;
	}
	
	/**
	 * @param autonomuosSystem the autonomuosSystem to set
	 */
	public void setAutonomousSystem(int autonomuosSystem) {
		this.autonomousSystem = autonomuosSystem;
	}
	
	/**
	 * @return the holdTime
	 */
	public int getHoldTime() {
		return holdTime;
	}
	
	/**
	 * @param holdTime the holdTime to set
	 */
	public void setHoldTime(int holdTime) {
		this.holdTime = holdTime;
	}
	
	/**
	 * @return the bgpIdentifier
	 */
	public long getBgpIdentifier() {
		return bgpIdentifier;
	}
	
	/**
	 * @param bgpIdentifier the bgpIdentifier to set
	 */
	public void setBgpIdentifier(long bgpIdentifier) {
		this.bgpIdentifier = bgpIdentifier;
	}
	
	/**
	 * @return the capabilities
	 */
	public List<Capability> getCapabilities() {
		return capabilities;
	}
	
	/**
	 * @param capabilities the capabilities to set
	 */
	public void setCapabilities(List<Capability> capabilities) {
		this.capabilities = capabilities;
	}

	@Override
	public int getType() {
		return BGPv4Constants.BGP_PACKET_TYPE_OPEN;
	}

	/**
	 * look up a specific capability in the list of provided capabilities
	 * 
	 * @param clazzToFind the class of the capability to find
	 * @return the capability or null if the capability is not passed along in the OPEN packet
	 */
	@SuppressWarnings("unchecked")
	public <T extends Capability> T findCapability(Class<T> clazzToFind) {
		T cap = null;
		
		if(this.capabilities != null) {
			for(Capability c : this.capabilities) {
				if(c.getClass().equals(clazzToFind)) {
					cap = (T)c;
					break;
				}
			}
		}
		return cap;
	}
	
	@Override
	public String toString() {
		ToStringBuilder builder = (new ToStringBuilder(this))
				.append("type", getType())
				.append("autonomousSystem", autonomousSystem)
				.append("bgpIdentifier", bgpIdentifier)
				.append("holdTime", holdTime)
				.append("protocolVersion", protocolVersion);
				
		for(Capability cap : capabilities)
			builder.append("capability", cap);
		
		return builder.toString();
	}
}
