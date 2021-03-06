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
 * File: org.bgp4j.netty.protocol.update.UpdatePacketDecoder.java 
 */
package org.bgp4j.netty.protocol.update;

import io.netty.buffer.ByteBuf;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import org.bgp4j.net.ASType;
import org.bgp4j.net.AddressFamily;
import org.bgp4j.net.BGPv4Constants;
import org.bgp4j.net.NetworkLayerReachabilityInformation;
import org.bgp4j.net.PathSegment;
import org.bgp4j.net.SubsequentAddressFamily;
import org.bgp4j.net.attributes.ASPathAttribute;
import org.bgp4j.net.attributes.AggregatorPathAttribute;
import org.bgp4j.net.attributes.AtomicAggregatePathAttribute;
import org.bgp4j.net.attributes.ClusterListPathAttribute;
import org.bgp4j.net.attributes.CommunityMember;
import org.bgp4j.net.attributes.CommunityPathAttribute;
import org.bgp4j.net.attributes.LocalPrefPathAttribute;
import org.bgp4j.net.attributes.MultiExitDiscPathAttribute;
import org.bgp4j.net.attributes.MultiProtocolReachableNLRI;
import org.bgp4j.net.attributes.MultiProtocolUnreachableNLRI;
import org.bgp4j.net.attributes.NextHopPathAttribute;
import org.bgp4j.net.attributes.OriginPathAttribute;
import org.bgp4j.net.attributes.OriginatorIDPathAttribute;
import org.bgp4j.net.attributes.PathAttribute;
import org.bgp4j.net.attributes.UnknownPathAttribute;
import org.bgp4j.net.packets.BGPv4Packet;
import org.bgp4j.net.packets.NotificationPacket;
import org.bgp4j.net.packets.update.AttributeFlagsNotificationPacket;
import org.bgp4j.net.packets.update.AttributeLengthNotificationPacket;
import org.bgp4j.net.packets.update.InvalidNetworkFieldNotificationPacket;
import org.bgp4j.net.packets.update.InvalidNextHopNotificationPacket;
import org.bgp4j.net.packets.update.InvalidOriginNotificationPacket;
import org.bgp4j.net.packets.update.MalformedASPathAttributeNotificationPacket;
import org.bgp4j.net.packets.update.MalformedAttributeListNotificationPacket;
import org.bgp4j.net.packets.update.MissingWellKnownAttributeNotificationPacket;
import org.bgp4j.net.packets.update.OptionalAttributeErrorNotificationPacket;
import org.bgp4j.net.packets.update.UnrecognizedWellKnownAttributeNotificationPacket;
import org.bgp4j.net.packets.update.UpdateNotificationPacket;
import org.bgp4j.net.packets.update.UpdatePacket;
import org.bgp4j.netty.protocol.ProtocolPacketUtils;
import org.bgp4j.netty.util.NLRICodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 */
public class UpdatePacketDecoder {
	private Logger log = LoggerFactory.getLogger(UpdatePacketDecoder.class);

	/**
	 * decode the UPDATE network packet. The passed channel buffer MUST point to the first packet octet AFTER the type octet.
	 * 
	 * @param buffer the buffer containing the data. 
	 * @return
	 */
	public BGPv4Packet decodeUpdatePacket(ByteBuf buffer) {
		UpdatePacket packet = new UpdatePacket();
		
		ProtocolPacketUtils.verifyPacketSize(buffer, BGPv4Constants.BGP_PACKET_MIN_SIZE_UPDATE, -1);
		
		if(buffer.readableBytes() < 2)
			throw new MalformedAttributeListException();
		
		// handle withdrawn routes
		int withdrawnOctets = buffer.readUnsignedShort();
		
		// sanity checking
		if(withdrawnOctets > buffer.readableBytes())
			throw new MalformedAttributeListException();
		
		ByteBuf withdrawnBuffer = null;		
		
		if(withdrawnOctets > 0) {
			withdrawnBuffer = buffer.readSlice(withdrawnOctets);
		}

		// sanity checking
		if(buffer.readableBytes() < 2)
			throw new MalformedAttributeListException();

		// handle path attributes
		int pathAttributeOctets =  buffer.readUnsignedShort();
		
		// sanity checking
		if(pathAttributeOctets > buffer.readableBytes())
			throw new MalformedAttributeListException();
			
		ByteBuf pathAttributesBuffer = null;
		
		if(pathAttributeOctets > 0) {
			pathAttributesBuffer = buffer.readSlice(pathAttributeOctets);
		}
		
		if(withdrawnBuffer != null) {
			try {
				packet.getWithdrawnRoutes().addAll(decodeWithdrawnRoutes(withdrawnBuffer));
			} catch(IndexOutOfBoundsException e) {
				throw new MalformedAttributeListException();
			}
		}

		if(pathAttributesBuffer != null) {
			try {
				packet.getPathAttributes().addAll(decodePathAttributes(pathAttributesBuffer));
			} catch (IndexOutOfBoundsException ex) {
				throw new MalformedAttributeListException();
			}
		}
		
		// handle network layer reachability information
		if(buffer.readableBytes() > 0) {
			try {
				while (buffer.isReadable()) {
					packet.getNlris().add(NLRICodec.decodeNLRI(buffer));
				}
			} catch (IndexOutOfBoundsException e) {
				throw new InvalidNetworkFieldException();
			} catch(IllegalArgumentException e) {
				throw new InvalidNetworkFieldException();				
			}
		}
		
		return packet;
	}

	/**
	 * decode a NOTIFICATION packet that corresponds to UPDATE apckets. The passed channel buffer MUST point to the first packet octet AFTER the terror sub code.
	 * 
	 * @param buffer the buffer containing the data. 
	 * @return
	 */
	public NotificationPacket decodeUpdateNotification(ByteBuf buffer, int errorSubcode) {
		UpdateNotificationPacket packet = null;
		byte[] offendingAttribute = null;

		if(buffer.isReadable()) {
			offendingAttribute = new byte[buffer.readableBytes()];

			buffer.readBytes(offendingAttribute);
		}

		switch(errorSubcode) {
		case UpdateNotificationPacket.SUBCODE_MALFORMED_ATTRIBUTE_LIST:
			packet = new MalformedAttributeListNotificationPacket();
			break;
		case UpdateNotificationPacket.SUBCODE_UNRECOGNIZED_WELL_KNOWN_ATTRIBUTE:
			packet = new UnrecognizedWellKnownAttributeNotificationPacket(offendingAttribute);
			break;
		case UpdateNotificationPacket.SUBCODE_MISSING_WELL_KNOWN_ATTRIBUTE:
			packet = new MissingWellKnownAttributeNotificationPacket(0);
			break;
		case UpdateNotificationPacket.SUBCODE_ATTRIBUTE_FLAGS_ERROR:
			packet = new AttributeFlagsNotificationPacket(offendingAttribute);
			break;
		case UpdateNotificationPacket.SUBCODE_ATTRIBUTE_LENGTH_ERROR:
			packet = new AttributeLengthNotificationPacket(offendingAttribute);
			break;
		case UpdateNotificationPacket.SUBCODE_INVALID_ORIGIN_ATTRIBUTE:
			packet = new InvalidOriginNotificationPacket(offendingAttribute);
			break;
		case UpdateNotificationPacket.SUBCODE_INVALID_NEXT_HOP_ATTRIBUTE:
			packet = new InvalidNextHopNotificationPacket(offendingAttribute);
			break;
		case UpdateNotificationPacket.SUBCODE_OPTIONAL_ATTRIBUTE_ERROR:
			packet = new OptionalAttributeErrorNotificationPacket(offendingAttribute);
			break;
		case UpdateNotificationPacket.SUBCODE_INVALID_NETWORK_FIELD:
			packet = new InvalidNetworkFieldNotificationPacket();
			break;
		case UpdateNotificationPacket.SUBCODE_MALFORMED_AS_PATH:
			packet = new MalformedASPathAttributeNotificationPacket(offendingAttribute);
			break;
		}
		
		return packet;
	}

	private ASPathAttribute decodeASPathAttribute(ByteBuf buffer, ASType asType) {
		ASPathAttribute attr = new ASPathAttribute(asType);

		while(buffer.isReadable()) {
			if(buffer.readableBytes() < 2)
				throw new MalformedASPathAttributeException();
			
			int segmentType = buffer.readUnsignedByte();
			int pathLength = buffer.readUnsignedByte();
			int pathOctetLength = (pathLength * (asType == ASType.AS_NUMBER_4OCTETS ? 4 : 2));
			
			if(buffer.readableBytes() < pathOctetLength)
				throw new MalformedASPathAttributeException();
			
			PathSegment segment =  new PathSegment(asType);

			try {
				segment.setPathSegmentType(PathSegmentTypeCodec.fromCode(segmentType));
			} catch (IllegalArgumentException e) {
				log.error("cannot convert AS_PATH type", e);

				throw new MalformedASPathAttributeException();
			}
			
			for(int i=0; i<pathLength; i++) {
				if(asType == ASType.AS_NUMBER_4OCTETS)
					segment.getAses().add((int)buffer.readUnsignedInt());
				else
					segment.getAses().add(buffer.readUnsignedShort());
			}
			
			attr.getPathSegments().add(segment);
		}
		
		return attr;
	}

	private OriginPathAttribute decodeOriginPathAttribute(ByteBuf buffer) {
		OriginPathAttribute attr = new OriginPathAttribute();
		
		if(buffer.readableBytes() != 1)
			throw new AttributeLengthException();
		
		try {
			attr.setOrigin(OriginCodec.fromCode(buffer.readUnsignedByte()));
		} catch(IllegalArgumentException e) {
			log.error("cannot convert ORIGIN code", e);
			
			throw new InvalidOriginException();
		}
		
		return attr;
	}

	private MultiExitDiscPathAttribute decodeMultiExitDiscPathAttribute(ByteBuf buffer) {
		MultiExitDiscPathAttribute attr = new MultiExitDiscPathAttribute();
		
		if(buffer.readableBytes() != 4)
			throw new AttributeLengthException();
		
		attr.setDiscriminator((int)buffer.readUnsignedInt());
		
		return attr;
	}

	private LocalPrefPathAttribute decodeLocalPrefPathAttribute(ByteBuf buffer) {
		LocalPrefPathAttribute attr = new LocalPrefPathAttribute();
		
		if(buffer.readableBytes() != 4)
			throw new AttributeLengthException();
		
		attr.setLocalPreference((int)buffer.readUnsignedInt());
		
		return attr;
	}

	private NextHopPathAttribute decodeNextHopPathAttribute(ByteBuf buffer) {
		NextHopPathAttribute attr = new NextHopPathAttribute();
		
		if(buffer.readableBytes() != 4)
			throw new AttributeLengthException();
		
		try {
			byte[] addr = new byte[4];
			
			buffer.readBytes(addr);
			attr.setNextHop((Inet4Address)Inet4Address.getByAddress(addr));
		} catch(IllegalArgumentException e) {
			throw new InvalidNextHopException();
		} catch (UnknownHostException e) {
			throw new InvalidNextHopException();
		}
		
		return attr;
	}

	private AtomicAggregatePathAttribute decodeAtomicAggregatePathAttribute(ByteBuf buffer) {
		AtomicAggregatePathAttribute attr = new AtomicAggregatePathAttribute();
		
		if(buffer.readableBytes() != 0)
			throw new AttributeLengthException();
		
		return attr;
	}

	private AggregatorPathAttribute decodeAggregatorPathAttribute(ByteBuf buffer, ASType asType) {
		AggregatorPathAttribute attr = new AggregatorPathAttribute(asType);
		int readableBytes = buffer.readableBytes();
		
		if(asType == ASType.AS_NUMBER_4OCTETS) {
			if(readableBytes != 8)
				throw new AttributeLengthException();		
				
			attr.setAsNumber((int)buffer.readUnsignedInt());
		} else {
			if(readableBytes != 6)
				throw new AttributeLengthException();		
			
			attr.setAsNumber(buffer.readUnsignedShort());
		}
		
		try {
			byte[] addr = new byte[4];
			
			buffer.readBytes(addr);
			attr.setAggregator((Inet4Address)Inet4Address.getByAddress(addr));
		} catch (UnknownHostException e) {
			throw new OptionalAttributeErrorException();
		}

		return attr;
	}

	private CommunityPathAttribute decodeCommunityPathAttribute(ByteBuf buffer) {
		CommunityPathAttribute attr = new CommunityPathAttribute();
		
		if(buffer.readableBytes() < 4 || (buffer.readableBytes() % 4 != 0))
			throw new OptionalAttributeErrorException();
		
		attr.setCommunity((int)buffer.readUnsignedInt());
		while(buffer.isReadable()) {
			CommunityMember member = new CommunityMember();
			
			member.setAsNumber(buffer.readUnsignedShort());
			member.setMemberFlags(buffer.readUnsignedShort());
			
			attr.getMembers().add(member);
		}
		
		return attr;
	}

	private MultiProtocolReachableNLRI decodeMpReachNlriPathAttribute(ByteBuf buffer) {
		MultiProtocolReachableNLRI attr = new MultiProtocolReachableNLRI();
		
		try {
			attr.setAddressFamily(AddressFamily.fromCode(buffer.readUnsignedShort()));
			attr.setSubsequentAddressFamily(SubsequentAddressFamily.fromCode(buffer.readUnsignedByte()));
			
			int nextHopLength = buffer.readUnsignedByte();
			
			if(nextHopLength > 0) {
				byte[] nextHop = new byte[nextHopLength];
				
				buffer.readBytes(nextHop);
				attr.setNextHopAddress(nextHop);
			}
			
			buffer.readByte(); // reserved
			
			while(buffer.isReadable()) {
				attr.getNlris().add(NLRICodec.decodeNLRI(buffer));
			}
		} catch(RuntimeException e) {
			log.error("failed to decode MP_REACH_NLRI path attribute", e);
			
			throw new OptionalAttributeErrorException();
		}
		
		return attr;
	}
	
	private MultiProtocolUnreachableNLRI decodeMpUnreachNlriPathAttribute(ByteBuf buffer) {
		MultiProtocolUnreachableNLRI attr = new MultiProtocolUnreachableNLRI();
		
		try {
			attr.setAddressFamily(AddressFamily.fromCode(buffer.readUnsignedShort()));
			attr.setSubsequentAddressFamily(SubsequentAddressFamily.fromCode(buffer.readUnsignedByte()));
			
			while(buffer.isReadable()) {
				attr.getNlris().add(NLRICodec.decodeNLRI(buffer));
			}
		} catch(RuntimeException e) {
			log.error("failed to decode MP_UNREACH_NLRI path attribute", e);
			
			throw new OptionalAttributeErrorException();
		}
		
		return attr;
	}
	
	private OriginatorIDPathAttribute decodeOriginatorIDPathAttribute(ByteBuf buffer) {
		OriginatorIDPathAttribute attr = new OriginatorIDPathAttribute();
		
		try {
			attr.setOriginatorID((int)buffer.readUnsignedInt());
		} catch(RuntimeException e) {
			log.error("failed to decode ORIGINATOR_ID attribute", e);
			
			throw new OptionalAttributeErrorException();
		}
		
		return attr;
	}

	private ClusterListPathAttribute decodeClusterListPathAttribute(ByteBuf buffer) {
		ClusterListPathAttribute attr = new ClusterListPathAttribute();
		
		try {
			while(buffer.isReadable()) {
				attr.getClusterIds().add((int)buffer.readUnsignedInt());
			}
		} catch(RuntimeException e) {
			log.error("failed to decode ORIGINATOR_ID attribute", e);
			
			throw new OptionalAttributeErrorException();
		}
		return attr;
	}
	 
	private List<PathAttribute> decodePathAttributes(ByteBuf buffer) {
		List<PathAttribute> attributes = new LinkedList<PathAttribute>();
		
		while(buffer.isReadable()) {
			buffer.markReaderIndex();
	
			try {
				int flagsType = buffer.readUnsignedShort();
				boolean optional = ((flagsType & BGPv4Constants.BGP_PATH_ATTRIBUTE_OPTIONAL_BIT) != 0);
				boolean transitive = ((flagsType & BGPv4Constants.BGP_PATH_ATTRIBUTE_TRANSITIVE_BIT) != 0);
				boolean partial = ((flagsType & BGPv4Constants.BGP_PATH_ATTRIBUTE_PARTIAL_BIT) != 0);
				int typeCode = (flagsType & BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_MASK);
				int valueLength = 0;
	
				if ((flagsType & BGPv4Constants.BGP_PATH_ATTRIBUTE_EXTENDED_LENGTH_BIT) != 0)
					valueLength = buffer.readUnsignedShort();
				else
					valueLength = buffer.readUnsignedByte();
	
				ByteBuf valueBuffer = buffer.readSlice(valueLength);
	
				PathAttribute attr = null;
			
				switch (typeCode) {
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_AGGREGATOR:
					attr = decodeAggregatorPathAttribute(valueBuffer, ASType.AS_NUMBER_2OCTETS);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_AS4_AGGREGATOR:
					attr = decodeAggregatorPathAttribute(valueBuffer, ASType.AS_NUMBER_4OCTETS);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_AS4_PATH:
					attr = decodeASPathAttribute(valueBuffer, ASType.AS_NUMBER_4OCTETS);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_AS_PATH:
					attr = decodeASPathAttribute(valueBuffer, ASType.AS_NUMBER_2OCTETS);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_ATOMIC_AGGREGATE:
					attr = decodeAtomicAggregatePathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_COMMUNITIES:
					attr = decodeCommunityPathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_LOCAL_PREF:
					attr = decodeLocalPrefPathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_MULTI_EXIT_DISC:
					attr = decodeMultiExitDiscPathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_NEXT_HOP:
					attr = decodeNextHopPathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_ORIGIN:
					attr = decodeOriginPathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_MP_REACH_NLRI:
					attr = decodeMpReachNlriPathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_MP_UNREACH_NLRI:
					attr = decodeMpUnreachNlriPathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_ORIGINATOR_ID:
					attr = decodeOriginatorIDPathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_CLUSTER_LIST:
					attr = decodeClusterListPathAttribute(valueBuffer);
					break;
				default: 
				    {
						byte[] value = new byte[valueBuffer.readableBytes()];
					
						valueBuffer.readBytes(value);
						attr = new UnknownPathAttribute(typeCode, value);
				    }
					break;
				}
				attr.setOptional(optional);
				attr.setTransitive(transitive);
				attr.setPartial(partial);
				
				attributes.add(attr);
			} catch(AttributeException ex) {
				int endReadIndex = buffer.readerIndex();
				
				buffer.resetReaderIndex();
				
				int attributeLength = endReadIndex - buffer.readerIndex();
				byte[] packet = new byte[attributeLength];
				
				buffer.readBytes(packet);
				ex.setRawOffendingAttributes(packet);
				
				throw ex;
			} catch(IndexOutOfBoundsException ex) {
				int endReadIndex = buffer.readerIndex();
				
				buffer.resetReaderIndex();
				
				int attributeLength = endReadIndex - buffer.readerIndex();
				byte[] packet = new byte[attributeLength];
				
				buffer.readBytes(packet);
	
				throw new AttributeLengthException(packet);
			}
			
		}
		
		return attributes;
	}

	private List<NetworkLayerReachabilityInformation> decodeWithdrawnRoutes(ByteBuf buffer)  {
		List<NetworkLayerReachabilityInformation> routes = new LinkedList<NetworkLayerReachabilityInformation>();
		
		while(buffer.isReadable()) {
			routes.add(NLRICodec.decodeNLRI(buffer));			
		}
		return routes;
	}

}
