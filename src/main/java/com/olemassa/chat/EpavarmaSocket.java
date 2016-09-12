package com.olemassa.chat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author pete
 *
 * Äärettömän huono ja epävarma implementaatio socketista..
 *
 */
public class EpavarmaSocket extends DatagramSocket {
	
	final Logger logger = LoggerFactory.getLogger(EpavarmaSocket.class);

	private static Random random = new Random();
	private static double success_probability = 1.0d;
	private static double in_time_probability = 1.0d;
	private static double correct_probability = 0.5d;
	private static double max_delay_millis = 1000d;

	public EpavarmaSocket() throws SocketException {
		super();
	}

	public EpavarmaSocket(DatagramSocketImpl impl) {
		super(impl);
	}

	public EpavarmaSocket(int port, InetAddress laddr) throws SocketException {
		super(port, laddr);
	}

	public EpavarmaSocket(int port) throws SocketException {
		super(port);
	}

	public EpavarmaSocket(SocketAddress bindaddr) throws SocketException {
		super(bindaddr);
	}

	@Override
	public synchronized void receive(DatagramPacket packet) throws IOException {
		while (true) {
			super.receive(packet);
			//Kurkkaa hävisikö paketti
			if (success_probability <= random.nextDouble()) {
				logger.warn("Paketti hävisi kokonaan :O");
			} else {
				//Vilase sattuko se viivästymään
				if (in_time_probability <= random.nextDouble())
					try {
						long delay_millis = (long) (random.nextDouble() * max_delay_millis);
						Thread.sleep(delay_millis);
						logger.warn(String.format("Paketti myöhästy {} millisekuntia", delay_millis));
					} catch (InterruptedException ignoreThis) {}
				//Ihmettele muuttoko sielä joku tavu
				if (correct_probability <= random.nextDouble()) {
					int messed_byte_index = random.nextInt(packet.getLength());
					int flippedBitLeftOffset = random.nextInt(8);
					byte[] packetData = packet.getData();
					byte originalByte = packetData[messed_byte_index];
					byte messedByte = (byte) (originalByte ^ (1 << flippedBitLeftOffset));
					packetData[messed_byte_index] = messedByte;
					packet.setData(packetData);
					logger.warn(String.format(
							"Paketin %s tavun %s bitti vaihtu (%s -> %s)",
							new Object[] {
									messed_byte_index + 1,
									8 - flippedBitLeftOffset,
									byteToString(originalByte),
									byteToString(messedByte)}));
				}
				return;
			}
		}
	}

	//Apumetodi tavujen ihmettelyyn
	private static String byteToString(byte byte_to_string) {
		return "0b" + ("0000000" + Integer.toBinaryString(0xFF & byte_to_string)).replaceAll(".*(.{8})$", "$1");
	}

}
