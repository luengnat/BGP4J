/**
 * 
 */
package org.bgp4j.netty.protocol;

import org.bgp4j.netty.BGPv4Constants;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * Superclass for all BGPv4 path attributes
 * 
 * @author rainer
 *
 */
public abstract class PathAttribute {

	private boolean optional;
	private boolean transitive;
	private boolean partial;
	
	/**
	 * encode the path attribute for network transmission
	 * 
	 * @return an encoded formatted path attribute
	 */
	ChannelBuffer encodePathAttribute()  {
		ChannelBuffer buffer = ChannelBuffers.buffer(BGPv4Constants.BGP_PACKET_MAX_LENGTH);
		int valueLength = getValueLength();
		int attrFlagsCode = 0;
				
		if(isOptional())
			attrFlagsCode |= BGPv4Constants.BGP_PATH_ATTRIBUTE_OPTIONAL_BIT;
		
		if(isTransitive())
			attrFlagsCode |= BGPv4Constants.BGP_PATH_ATTRIBUTE_TRANSITIVE_BIT;

		if(isPartial())
			attrFlagsCode |= BGPv4Constants.BGP_PATH_ATTRIBUTE_PARTIAL_BIT;
		
		if(valueLength > 255)
			attrFlagsCode |= BGPv4Constants.BGP_PATH_ATTRIBUTE_EXTENDED_LENGTH_BIT;
		
		attrFlagsCode |= (getTypeCode() & BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_MASK);

		buffer.writeShort(attrFlagsCode);
		
		if(valueLength > 255)
			buffer.writeShort(valueLength);
		else
			buffer.writeByte(valueLength);
		
		buffer.writeBytes(encodeValue());
		
		return buffer;
	}
	
	int calculatePacketSize() {
		int size = 2; // attribute flags + type field;
		int valueLength = getValueLength();
		
		size += (valueLength > 255) ? 2 : 1; // length field;
		size += valueLength;
		
		return size;
	}
	
	/**
	 * get the specific type code (see RFC 4271)
	 * @return
	 */
	protected abstract int getTypeCode();

	/**
	 * get the attribute value length
	 * @return
	 */
	protected abstract int getValueLength();

	/**
	 * get the encoded attribute value
	 */
	protected abstract ChannelBuffer encodeValue();
	
	/**
	 * @return the partial
	 */
	public boolean isPartial() {
		return partial;
	}

	/**
	 * @param partial the partial to set
	 */
	public void setPartial(boolean partial) {
		this.partial = partial;
	}

	/**
	 * @return the optional
	 */
	public boolean isOptional() {
		return optional;
	}

	/**
	 * @return the optional
	 */
	public boolean isWellKnown() {
		return !isOptional();
	}

	/**
	 * @param optional the optional to set
	 */
	protected void setOptional(boolean optional) {
		this.optional = optional;
	}

	/**
	 * @param wellKnown the well known to set
	 */
	protected void setWellKnown(boolean wellKnown) {
		setOptional(!wellKnown);
	}
	
	/**
	 * @return the transitive
	 */
	public boolean isTransitive() {
		return transitive;
	}

	/**
	 * @param transitive the transitive to set
	 */
	public void setTransitive(boolean transitive) {
		this.transitive = transitive;
	}
	
	
}