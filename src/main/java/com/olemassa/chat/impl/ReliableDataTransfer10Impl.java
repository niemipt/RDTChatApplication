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

public class ReliableDataTransfer10Impl implements ReliableDataTransfer {

	final Logger logger = LoggerFactory.getLogger(ReliableDataTransfer10Impl.class);

	private DatagramSocket socket;
	private List<Receiver> receivers = new ArrayList<Receiver>();
	private boolean listening;

	public ReliableDataTransfer10Impl(String localhost, int localport, String remotehost, int remoteport) throws SocketException {
		logger.info("ReliableDataTransfer10Impl({}, {}, {}, {})", localhost, localport, remotehost, remoteport);
		socket = new EpavarmaSocket(new InetSocketAddress(localhost, localport));
		socket.connect(new InetSocketAddress(remotehost, remoteport));
		socket.setSoTimeout(1000);
	}

	@Override
	public void send(byte[] outbound) {
		logger.debug("send({})", outbound);
		try {
			logger.debug("{}.send({})", socket, outbound);
			socket.send(new DatagramPacket(
					outbound,
					outbound.length,
					socket.getRemoteSocketAddress()));
		} catch (IOException e) {
			logger.error("Cannot send bytes", e);
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
				logger.trace("Notifying receivers {}", receivers);
				for (Receiver receiver : receivers) {
					logger.trace("Notify receiver {}", receiver);
					receiver.receive(Arrays.copyOfRange(receivedPacket.getData(), 0, receivedPacket.getLength() - 1));
				}

			} catch (SocketTimeoutException e) {
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

}