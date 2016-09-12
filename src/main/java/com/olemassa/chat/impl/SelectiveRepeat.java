package com.olemassa.chat.impl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.olemassa.chat.EpavarmaSocket;
import com.olemassa.chat.Receiver;
import com.olemassa.chat.ReliableDataTransfer;

public class SelectiveRepeat implements ReliableDataTransfer {

	static final Logger logger = LoggerFactory.getLogger(SelectiveRepeat.class);

	private DatagramSocket socket = null;
	private int bufferSize = 10;
	long timeout = 10000;
	private List<Receiver> receivers = new ArrayList<Receiver>();

	private boolean listening;

	private SelectiveRepeatBuffer selectiveRepeatBuffer;

	public SelectiveRepeat(String localhost, int localport, String remotehost, int remoteport, int bufferSize, int timeout) throws SocketException {
		logger.info("SelectiveRepeat({}, {}, {}, {}, {}, {})", localhost, localport, remotehost, remoteport, bufferSize, timeout);
		socket = new EpavarmaSocket(new InetSocketAddress(localhost, localport));
		socket.connect(new InetSocketAddress(remotehost, remoteport));
		socket.setSoTimeout(100);
		this.bufferSize = bufferSize;
		this.selectiveRepeatBuffer = new SelectiveRepeatBuffer(this.bufferSize);
		this.timeout = timeout;		
	}

	@Override
	public void send(byte[] outbound) throws IllegalStateException {
		logger.info("send({})", outbound);
		Packet sendPacket = new Packet((byte) 0, outbound);
		sendPacket.initializeTimer(timeout);
		selectiveRepeatBuffer.send(sendPacket);
	}

	@Override
	public void addReceiver(Receiver receiver) {
		logger.info("addReceiver({})", receiver);
		receivers.add(receiver);
	}

	@Override
	public void stopListening() {
		logger.info("stopListening()");
		listening = false;
		receivers.clear();
	}

	@Override
	public void run() {

		logger.info("run()", socket.getLocalSocketAddress());
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


				logger.debug("PacketBuffer: {}", selectiveRepeatBuffer);
				Packet[] pckts = selectiveRepeatBuffer.receive(packet);
				logger.debug("PacketBuffer: {}", selectiveRepeatBuffer);
				logger.trace("Huomautetaan vastaanottajia {}", receivers);
				for (Packet pckt : pckts)
					for (Receiver receiver : receivers) {
						logger.trace("Huomautetaan vastaanottajaa {} paketista {}", receiver, pckt);
						receiver.receive(packet.getPayload());
					}

			} catch (SocketTimeoutException ignore) {
			} catch (IllegalArgumentException ignore) {
				logger.debug("Pakettia ei voitu lukea {}", ignore.getMessage());
			}  catch (IOException e) {
				logger.error("Serverithreadissa ongelmia", e);
			} finally {
				selectiveRepeatBuffer.resendUnsent();
			}
		} finally {
			if (socket != null) if (!socket.isClosed()) socket.close();
			logger.debug("Listening stopped");
		}
	}

	private void sendPacket(Packet packet) {
		logger.info("sendPacket({})", packet);
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

	private class SelectiveRepeatBuffer {

		private Packet[] sendBuffer;
		private int sendBaseSequence;
		private int sendNextSequence;

		private Packet[] receiveBuffer;
		private int receiveBaseSequence;

		public SelectiveRepeatBuffer(int bufferSize) {
			sendBuffer = new Packet[bufferSize];
			sendBaseSequence = 0;
			sendNextSequence = 0;
			receiveBuffer = new Packet[bufferSize];
			receiveBaseSequence = 0;
		}

		public void send(Packet packet) {
			logger.info("addToBuffer({})", packet);
			if (traverseSequence(sendNextSequence, 1) != sendBaseSequence) {
				packet.setSequence((byte) sendNextSequence);
				packet.resetTimer();
				sendBuffer[sendNextSequence] = packet;
				sendPacket(packet);
				sendNextSequence = traverseSequence(sendNextSequence, 1);
			} else {
				throw new IllegalStateException("Viestibufferi on täynnä");
			}
		}

		public void resendUnsent() {
			if (sendBuffer != null && sendBuffer.length != 0) {
				int seq = sendBaseSequence;
				while (seq != sendNextSequence) {
					if (sendBuffer[seq] != null)
						if (sendBuffer[seq].needsResend()) {
							sendPacket(sendBuffer[seq]);
							sendBuffer[seq].resetTimer();
						}
					seq = traverseSequence(seq, 1);
				}
			}
		}

		public Packet[] receive(Packet packet) {
			logger.info("receive({})", packet);
			ArrayList<Packet> readyPackets = new ArrayList<Packet>();
			if (packet.isAck()) {
				logger.debug("ACK järjestysnumerolla {}, lähetysbufferissa tilanne on Pohja: {} Seuraava: {}", (int) packet.getSequence(), sendBaseSequence, sendNextSequence);
				if (sendBuffer != null) {
					logger.debug("Kuitataan paketti lähetysbufferista kohdasta {}", packet.getSequence());
					sendBuffer[(int) packet.getSequence()] = null;
					if (sendBaseSequence == (byte) packet.getSequence()) {
						logger.debug("Paketti oli kuittaus lähetyspohjaan, siirretään lähetyspohja eteenpäin");
						while (sendBuffer[sendBaseSequence] == null && sendBaseSequence < sendNextSequence) {
							sendBaseSequence = traverseSequence(sendBaseSequence, 1);
						}
						logger.debug("Lähetysbufferissa tilanne on Pohja: {} Seuraava: {}", sendBaseSequence, sendNextSequence);
					}
				}
			} else {
				logger.debug("Tavallinen paketti järjestysnumerolla {}, vastaanottobufferin tilanne on Pohja: {}", (int) packet.getSequence(), receiveBaseSequence);
				if (receiveBuffer != null) {
					logger.trace("Pistetään paketti vastaanottobufferiin kohtaan {}", packet.getSequence());
					receiveBuffer[(int) packet.getSequence()] = packet;
					if (receiveBaseSequence == (byte) packet.getSequence()) {
						logger.debug("Paketti oli vastaanottopohja aetaan palautettavat paketit ja siirretään vastaanoton pohja eteenpäin");
						while (receiveBuffer[receiveBaseSequence] != null) {
							readyPackets.add(receiveBuffer[receiveBaseSequence]);
							receiveBuffer[receiveBaseSequence] = null;
							receiveBaseSequence = traverseSequence(receiveBaseSequence, 1);
						}
						logger.debug("Vastaanottobufferin tilanne on Pohja: {}", receiveBaseSequence);
					}
				}
				logger.debug("Lähetetään ACK");
				sendPacket(
						new Packet(
								packet.getSequence(),
								"ACK".getBytes()));
			}
			logger.trace("Palautetaan paketit, jotka saatiin poimittua järjetykssä vastaanottopohjasta eteenpäin");
			return readyPackets.toArray(new Packet[readyPackets.size()]);
		}

		public String toString() {
			String string = String.format("\nLähetyspohja: %s Lähetyksen seuraava järjestysnumero: %s\n", sendBaseSequence, sendNextSequence);
			string += (Arrays.toString(sendBuffer) + "\n");
			string += String.format("Vastaanottopohja: %s\n", receiveBaseSequence);
			string += (Arrays.toString(receiveBuffer) + "\n");
			return string;
		}

		private int traverseSequence(int sequence, int steps) {
			logger.trace("getSequenceAtLocation({}, {})", sequence, steps);
			return (sequence + steps) % sendBuffer.length;
		}

	}

}
