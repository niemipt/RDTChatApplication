package com.olemassa.chat.impl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.olemassa.chat.EpavarmaSocket;
import com.olemassa.chat.Receiver;
import com.olemassa.chat.ReliableDataTransfer;

public class GoBackNImpl implements ReliableDataTransfer {

	static final Logger logger = LoggerFactory.getLogger(GoBackNImpl.class);

	private DatagramSocket socket = null;
	private int bufferSize = 10;
	long timeout = 10000;
	private List<Receiver> receivers = new ArrayList<Receiver>();

	private boolean listening;

	private SendBuffer packetBuffer;
	private int waitForSequenceRequest = 0;

	public GoBackNImpl(String localhost, int localport, String remotehost, int remoteport, int bufferSize, int timeout) throws SocketException {
		logger.info("GoBackNImpl({}, {}, {}, {}, {}, {})", localhost, localport, remotehost, remoteport, bufferSize, timeout);
		socket = new EpavarmaSocket(new InetSocketAddress(localhost, localport));
		socket.connect(new InetSocketAddress(remotehost, remoteport));
		socket.setSoTimeout(100);
		this.bufferSize = bufferSize;
		this.packetBuffer = new SendBuffer(this.bufferSize);
		this.timeout = timeout;		
	}

	@Override
	public void send(byte[] outbound) throws IllegalStateException {
		logger.debug("send({})", outbound);
		Packet sendPacket = new Packet((byte) 0, outbound);
		sendPacket.initializeTimer(timeout);
		packetBuffer.send(sendPacket);
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

	@Override
	public void run() {

		logger.debug("run()", socket.getLocalSocketAddress());
		logger.debug("socket.getLocalSocketAddress() {}", socket.getLocalSocketAddress());
		logger.debug("Receivers: {}", receivers);

		try {
			listening = true;	
			while (listening) try {

				byte[] inbound = new byte[1024];
				DatagramPacket receivedPacket = new DatagramPacket(inbound, inbound.length);
				socket.receive(receivedPacket);
				logger.debug("{}.receive({})", socket, receivedPacket.getData());

				Packet packet = Packet.readPacket(Packet.trim(receivedPacket.getData()));
				logger.debug("Sequence: {} Payload: {} ChecksumValue {}",
						packet.getSequence(),
						packet.getPayload(),
						Packet.getChecksumValue(packet.getSequence(), packet.getPayload()));

				if (packet.isAck()) {
					logger.debug("Packet is ACK {}", packet);
					logger.debug("PacketBuffer: {}", packetBuffer);
					packetBuffer.receive(packet);
					logger.debug("PacketBuffer: {}", packetBuffer);
				} else {
					logger.debug("Packet is regular message {}", packet);
					logger.debug("packet.getSequence() - (byte) receiveSequence {} - {}",
							packet.getSequence(),
							(byte) waitForSequenceRequest);
					if (packet.getSequence() == (byte) waitForSequenceRequest) {
						logger.trace("Huomautetaan vastaanottajia {}", receivers);
						for (Receiver receiver : receivers) {
							logger.trace("Huomautetaan vastaanottajaa {}", receiver);
							receiver.receive(packet.getPayload());
							waitForSequenceRequest = packet.getSequence() + 1;
						}
					} else {
						logger.debug("Tämä paketti on jo vastaanotettu");
					}
					sendPacket(
							new Packet(
									packet.getSequence(),
									"ACK".getBytes()));
				}

			} catch (SocketTimeoutException ignore) {
			} catch (IllegalArgumentException ignore) {
				logger.debug("Pakettia ei voitu lukea {}", ignore.getMessage());
			}  catch (IOException e) {
				logger.error("Serverithreadissa ongelmia", e);
			} finally {
				packetBuffer.resendUnsent();
			}
		} finally {
			if (socket != null) if (!socket.isClosed()) socket.close();
			logger.debug("Listening stopped");
		}
	}

	private void sendPacket(Packet packet) {
		logger.debug("sendPacket({})", packet);
		byte [] packetBytes = packet.getPacketBytes();
		try {
			logger.debug("{}.send({})", socket, packetBytes);
			socket.send(new DatagramPacket(
					packetBytes,
					packetBytes.length,
					socket.getRemoteSocketAddress()));
		} catch (IOException e) {
			logger.error("Cannot send bytes", e);
		}
	}

	private class SendBuffer {

		private Packet[] buffer;
		private int waitForAckSequence;
		private int nextFreeSequence;

		public SendBuffer(int bufferSize) {
			buffer = new Packet[bufferSize];
			waitForAckSequence = 0;
			nextFreeSequence = 0;
		}

		public void send(Packet packet) {
			logger.info("addToBuffer({})", packet);
			if (traverseSequence(nextFreeSequence, 1) != waitForAckSequence) {
				packet.setSequence((byte) nextFreeSequence);
				packet.resetTimer();
				buffer[nextFreeSequence] = packet;
				sendPacket(packet);
				nextFreeSequence = traverseSequence(nextFreeSequence, 1);
			} else {
				throw new IllegalStateException("Viestibufferi on täynnä");
			}
		}

		public void resendUnsent() {
			logger.trace("flushUnsent()");
			if (buffer  != null)
				for (int i = waitForAckSequence; i < nextFreeSequence; i++) {
					if (buffer[i] != null)
						if (buffer[i].needsResend()) {
							sendPacket(buffer[i]);
							buffer[i].resetTimer();
						}
				}
		}

		public void receive(Packet packet) {
			logger.info("receive({})", packet);
			logger.info("Kuitataan bufferista kuittaamattomat välillä [{},{}]",
					waitForAckSequence,
					packet.getSequence());
			if (buffer != null)
				if (packet.isAck())
					while (waitForAckSequence <= (int) packet.getSequence()) {
						buffer[waitForAckSequence] = null;
						waitForAckSequence = traverseSequence(waitForAckSequence, 1);
					}
		}

		public String toString() {
			String string = String.format("WaitForAck: %s Next Free: %s\n", waitForAckSequence, nextFreeSequence);
			for (Packet packet : buffer)
				if (packet != null) {
					string += (packet.toString() + "\n");
				} else {
					string += (null + "\n");
				}
			return string;
		}

		private int traverseSequence(int sequence, int steps) {
			logger.debug("getSequenceAtLocation({}, {})", sequence, steps);
			return (sequence + steps) % buffer.length;
		}

	}

}
