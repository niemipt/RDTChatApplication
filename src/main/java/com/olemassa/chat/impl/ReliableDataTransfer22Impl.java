package com.olemassa.chat.impl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.olemassa.chat.EpavarmaSocket;
import com.olemassa.chat.Receiver;
import com.olemassa.chat.ReliableDataTransfer;
import com.olemassa.chat.State;

import jonelo.jacksum.JacksumAPI;
import jonelo.jacksum.algorithm.AbstractChecksum;

public class ReliableDataTransfer22Impl implements ReliableDataTransfer {

	final Logger logger = LoggerFactory.getLogger(ReliableDataTransfer22Impl.class);

	private DatagramSocket socket = null;
	private List<Receiver> receivers = new ArrayList<Receiver>();
	private AbstractChecksum checksum;

	private State state;
	private Packet lastSent;

	boolean listening;

	public ReliableDataTransfer22Impl(String localhost, int localport, String remotehost, int remoteport) throws SocketException {
		logger.info("ReliableDataTransfer22Impl({}, {}, {}, {})", localhost, localport, remotehost, remoteport);
		socket = new EpavarmaSocket(new InetSocketAddress(localhost, localport));
		socket.connect(new InetSocketAddress(remotehost, remoteport));
		this.socket.setSoTimeout(1000);
		try {
			this.checksum = JacksumAPI.getChecksumInstance("crc8");
			this.checksum.setEncoding(AbstractChecksum.BASE16);
		} catch (NoSuchAlgorithmException e) {
			logger.error("Ei saada jacksummia toimiin..", e);
		}
	}

	@Override
	public void addReceiver(Receiver receiver) {
		logger.debug("addReceiver({})", receiver);
		receivers.add(receiver);
	}

	@Override
	public void stopListening() {
		logger.debug("stopListening()");
		listening = false;
		receivers.clear();
	}

	private void setState(State state) {
		logger.debug("setState({})", state);
		this.state = state;
	}

	@Override
	public void send(byte[] outbound) {
		logger.debug("send({})", outbound);
		if (state == State.WAIT_FOR_ACK) {
			throw new IllegalStateException("Ei voida lähettää, sillä odotellaan kuittausta");
		}
		setState(State.WAIT_FOR_ACK);
		lastSent = new Packet(state.getSequence(), outbound);
		sendPacket(lastSent);
	}

	private void sendPacket(Packet packet) {
		logger.debug("sendPacket({})", packet);
		try {
			logger.debug("{}.send({})", socket, packet.getPacketBytes());
			socket.send(new DatagramPacket(
					packet.getPacketBytes(),
					packet.getPacketBytes().length,
					socket.getRemoteSocketAddress()));
		} catch (IOException e) {
			logger.error("Cannot send bytes", e);
		}
	}

	public void run() {

		logger.debug("run()", socket.getLocalSocketAddress());
		logger.debug("socket.getLocalSocketAddress() {}", socket.getLocalSocketAddress());
		logger.debug("Receivers: {}", receivers);
		
		State.WAIT_FOR_REQUEST.setSequence((byte) 0);
		State.WAIT_FOR_ACK.setSequence((byte) 0);
		setState(State.WAIT_FOR_REQUEST);
		
		try {
			listening = true;	
			while (listening) try {

				byte[] inbound = new byte[1024];
				DatagramPacket receivedPacket = new DatagramPacket(inbound, inbound.length);
				socket.receive(receivedPacket);
				logger.debug("{}.receive({})", socket, receivedPacket.getData());

				Packet packet = new Packet(receivedPacket.getData());
				logger.debug("State: {} Sequence: {}", state.getState(), state.getSequence());
				logger.debug("Sequence: {} Payload: {} CheckByte {}", packet.getSequence(), packet.getPayload(), packet.getCheckByte());

				if (State.WAIT_FOR_REQUEST.equals(this.state)) {
					if (packet.isValid() && packet.getSequence().equals(state.getSequence())) {
						logger.trace("Notifying receivers {}", receivers);
						for (Receiver receiver : receivers) {
							logger.trace("Notify receiver {}", receiver);
							receiver.receive(packet.getPayload());
						}
						sendPacket(new Packet(state.getSequence(), "ACK".getBytes()));
						State.WAIT_FOR_REQUEST.addSequence();
					} else {
						sendPacket(new Packet((byte) ((Math.floorMod(state.getSequence() - 1, 2))), "ACK".getBytes()));
					}
				} else if (State.WAIT_FOR_ACK.equals(state)) {
					if (!packet.isValid() || (Arrays.equals("ACK".getBytes(), packet.getPayload()) && !packet.getSequence().equals(state.getSequence()))) {
						logger.debug("We are waiting sequence {} Resending Packet because response is valid {}, payload {} and state {}",
								new Object[] {
										state.getSequence(),
										packet.isValid(),
										new String(packet.getPayload()),
										packet.getSequence()});
						sendPacket(lastSent);
					} else if (packet.isValid() && Arrays.equals("ACK".getBytes(), packet.getPayload())) {
						setState(State.WAIT_FOR_REQUEST);
						State.WAIT_FOR_ACK.addSequence();
					} else {
						logger.debug("Ei vastaanoteta nyt muuta kuink ACK viestejä");
					}
				}
			} catch (SocketTimeoutException ignore) {
				//Tämä ja timeout siksi, että saadaan socketti nätisti lopettaan kuuntelu.
				continue;
			} catch (IOException e) {
				logger.error("Serverithreadissa ongelmia", e);
			}
		} finally {
			if (socket != null) if (!socket.isClosed()) socket.close();
			logger.debug("Listening stopped");
		}

	}

	private class Packet {

		private byte sequence;
		private byte[] payload;
		private byte checkByte;
		private Boolean isValid = null;
		
		public Packet(byte sequence, byte[] payload) {
			logger.debug("Packet({}, {})", sequence, payload);
			checksum.reset();
			checksum.update(ArrayUtils.add(payload, 0, sequence));
			this.sequence = sequence;
			this.payload = payload;
			checkByte = (byte) checksum.getValue();
		}

		public Packet(byte[] receivedPacket) {
			logger.debug("Packet({})", receivedPacket);
			sequence = receivedPacket[0];
			payload = ArrayUtils.subarray(receivedPacket, 1, ArrayUtils.indexOf(receivedPacket, (byte) 0, 1) - 1);
			checkByte = receivedPacket[ArrayUtils.indexOf(receivedPacket, (byte) 0, 1) - 1];			 
		}

		public Byte getSequence() {
			return sequence;
		}
		
		public byte[] getPayload() {
			return payload;
		}

		public Byte getCheckByte() {
			return checkByte;
		}

		private byte[] getPacketBytes() {
			return ArrayUtils.add(ArrayUtils.add(payload, 0, sequence), getCheckByte());
		}

		private boolean isValid() {
			if (isValid == null) {
				checksum.reset();
				checksum.update(ArrayUtils.add(payload, 0, sequence));
				isValid = (byte) checksum.getValue() == getCheckByte().byteValue();
			}
			return isValid;
		}

	}

}