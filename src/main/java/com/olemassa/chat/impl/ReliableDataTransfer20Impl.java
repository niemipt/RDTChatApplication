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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.olemassa.chat.EpavarmaSocket;
import com.olemassa.chat.Receiver;
import com.olemassa.chat.ReliableDataTransfer;
import com.olemassa.chat.State;

import jonelo.jacksum.JacksumAPI;
import jonelo.jacksum.algorithm.AbstractChecksum;

public class ReliableDataTransfer20Impl implements ReliableDataTransfer {

	final Logger logger = LoggerFactory.getLogger(ReliableDataTransfer20Impl.class);

	private DatagramSocket socket = null;
	private List<Receiver> receivers = new ArrayList<Receiver>();
	private State state = State.WAIT_FOR_REQUEST;
	private AbstractChecksum checksum;
	private byte[] lastSent;

	boolean listening;

	public ReliableDataTransfer20Impl(String localhost, int localport, String remotehost, int remoteport) throws SocketException {
		logger.info("ReliableDataTransfer20Impl({}, {}, {}, {})", localhost, localport, remotehost, remoteport);
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

	@Override
	public void send(byte[] outbound) {
		logger.debug("send({})", outbound);
		if (State.WAIT_FOR_ACK.equals(state)) {
			throw new IllegalStateException("Ei voida lähettää, sillä odotellaan kuittausta");
		}
		sendBytes(outbound);
		lastSent = outbound;
		setState(State.WAIT_FOR_ACK);
	}

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

				byte[] payload = new byte[receivedPacket.getLength() - 1];
				payload = Arrays.copyOfRange(receivedPacket.getData(), 0, receivedPacket.getLength() - 1);
				byte checkByte = receivedPacket.getData()[receivedPacket.getLength() - 1];
				logger.debug("Payload: {} CheckByte {}", payload, checkByte);

				checksum.reset();
				checksum.update(payload);
				
				logger.debug("{} == {}", (byte) checksum.getValue(), checkByte);
				boolean isValid = (byte) checksum.getValue() == checkByte;

				if (State.WAIT_FOR_REQUEST.equals(this.state)) {
					if (isValid) {
						sendBytes("ACK".getBytes());
						logger.trace("Notifying receivers {}", receivers);
						for (Receiver receiver : receivers) {
							logger.trace("Notify receiver {}", receiver);
							receiver.receive(payload);
						}
					} else {
						logger.debug("Ei tullu viesti oikein");
						sendBytes("NAK".getBytes());
					}
				} else if (State.WAIT_FOR_ACK.equals(this.state)) {
					if ("ACK".equals(new String(payload)) && isValid) {
						setState(State.WAIT_FOR_REQUEST);
					} else if ("NAK".equals(new String(payload)) && isValid) {
						sendBytes(lastSent);
					} else {
						logger.debug("Ei kunnollinen ACK tai NAK, joten ei voida olla varmoja saatiinko viesti perille.");
						setState(State.WAIT_FOR_REQUEST);
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
	
	private void sendBytes(byte[] outbound) {
		logger.debug("sendBytes({})", outbound);

		checksum.reset();
		checksum.update(outbound);

		byte sendCheckByte = (byte) checksum.getValue();
		byte[] sendBytes = new byte[outbound.length + 1];
		System.arraycopy(outbound, 0, sendBytes, 0, outbound.length);
		sendBytes[sendBytes.length - 1] = sendCheckByte;

		try {
			logger.debug("{}.send({})", socket, sendBytes);
			socket.send(new DatagramPacket(
					sendBytes,
					sendBytes.length,
					socket.getRemoteSocketAddress()));
		} catch (IOException e) {
			logger.error("Cannot send bytes", e);
		}
	}
	
	private void setState(State state) {
		logger.debug("setState({})", state);
		this.state = state;
	}
	
}