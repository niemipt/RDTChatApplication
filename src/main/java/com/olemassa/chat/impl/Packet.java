package com.olemassa.chat.impl;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.olemassa.chat.impl.util.VerySimpleTimer;

import jonelo.jacksum.JacksumAPI;
import jonelo.jacksum.algorithm.AbstractChecksum;

class Packet {

	static final Logger logger = LoggerFactory.getLogger(Packet.class);
	
	static AbstractChecksum jacksum;
	static {
		try {
			jacksum = JacksumAPI.getChecksumInstance("crc8");
		} catch (NoSuchAlgorithmException e) {
			logger.error("Ei saada jacksummia toimiin..", e);
		}
		jacksum.setEncoding(AbstractChecksum.BASE16);
	}

	public static byte getChecksumValue(byte sequence, byte[] payload) {
		logger.debug("getChecksumValue({}, {})", sequence, payload);
		jacksum.reset();
		jacksum.update(ArrayUtils.addAll(new byte[] {sequence}, payload));
		return (byte) jacksum.getValue();
	}
	
	public static byte[] trim(byte[] bytes) {
	    int nonEmptyIndex = bytes.length - 1;
	    while (nonEmptyIndex >= 0 && bytes[nonEmptyIndex] == 0) nonEmptyIndex--;
	    return ArrayUtils.subarray(bytes, 0, nonEmptyIndex + 1);
	}

	public static Packet readPacket(byte[] packetBytes) {
		logger.debug("readPacket({})", packetBytes);
		byte sequence = packetBytes[0];
		byte[] payload = ArrayUtils.subarray(packetBytes, 1, packetBytes.length - 1);
		byte checksum = getChecksumValue(sequence, payload);
		logger.debug("Sequence: {} Payload: {} Checksum from packet: {} Counted checksum: {}",
				sequence,
				payload,
				packetBytes[packetBytes.length - 1],
				checksum);
		if (checksum != packetBytes[packetBytes.length - 1])
			throw new IllegalArgumentException("Paketin tarkaste ei ole oikein");
		return new Packet(sequence, payload);
	}

	private byte sequence;
	private byte[] payload;
	long timeout;
	
	private VerySimpleTimer timer = null;

	public Packet(byte sequence, byte[] payload) {
		logger.debug("Packet({}, {})", sequence, payload);
		this.sequence = sequence;
		this.payload = payload;
	}
	
	public byte getSequence() {
		return sequence;
	}

	public void setSequence(byte sequence) {
		this.sequence = sequence;
	}

	public byte[] getPayload() {
		return payload;
	}

	public void setPayload(byte[] payload) {
		this.payload = payload;
	}

	public byte[] getPacketBytes() {
		logger.debug("getPacketBytes()");
		return ArrayUtils.addAll(ArrayUtils.addAll(new byte[] {sequence} ,payload), new byte[] {getChecksumValue(sequence, payload)});
	}

	public boolean isAck() {
		return "ACK".equalsIgnoreCase(new String(payload));
	}
	
	public void initializeTimer(long timeout) {
		timer = new VerySimpleTimer(timeout);
	}

	public void resetTimer() {
		if (timer != null) timer.reset();
	}

	public boolean needsResend() {
		if (timer != null) return timer.timeOver();
		return false; 
	}

	@Override
	public String toString() {
		return String.format("Sequence: " + sequence + " Payload: " + Arrays.toString(payload) + " Checksum: " + getChecksumValue(sequence, payload) + " Needs Resend: " + needsResend());
	}
	
	

}