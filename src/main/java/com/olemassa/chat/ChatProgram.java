package com.olemassa.chat;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.olemassa.chat.impl.GoBackNImpl;
import com.olemassa.chat.impl.ReliableDataTransfer10Impl;
import com.olemassa.chat.impl.ReliableDataTransfer20Impl;
import com.olemassa.chat.impl.ReliableDataTransfer21Impl;
import com.olemassa.chat.impl.ReliableDataTransfer22Impl;
import com.olemassa.chat.impl.ReliableDataTransfer30Impl;
import com.olemassa.chat.impl.SelectiveRepeat;


public class ChatProgram implements Runnable, Receiver {

	final static Logger logger = LoggerFactory.getLogger(ChatProgram.class);

	private static final Pattern helpPattern = Pattern.compile("^(?i:help(| protocol| localhost| localport| remotehost| remoteport| \\S+))$");
	private static final Pattern commandPattern = Pattern.compile("^(?i:set (protocol|localhost|localport|remotehost|remoteport) (\\S+))$");

	private InputStream inputStream = System.in;
	private OutputStream outputStream = System.out;

	private Scanner scanner = new  Scanner(inputStream);
	private PrintWriter printWriter = new PrintWriter(outputStream);

	private Thread serverThread = null;
	private ReliableDataTransfer rdt = null;

	private String protocol = "1.0";
	private String localhost = "localhost";
	private int localport = 7777;
	private String remotehost = "localhost";
	private int remoteport = 8888;

	public InputStream getInputStream() {
		return inputStream;
	}

	public void setInputStream(InputStream inputStream) {
		logger.debug("setInputStream({})", inputStream);
		this.inputStream = inputStream;
	}

	public OutputStream getOutputStream() {
		return outputStream;
	}

	public void setOutputStream(OutputStream outputStream) {
		logger.debug("setOutputStream({})", outputStream);
		this.outputStream = outputStream;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		logger.debug("setProtocol({})", protocol);
		this.protocol = protocol;
	}

	public String getLocalhost() {
		return localhost;
	}

	public void setLocalhost(String localhost) throws SocketException, UnknownHostException, InterruptedException {
		logger.info("setLocalhost({})", localhost);
		this.localhost = localhost;
	}

	public int getLocalport() {
		return localport;
	}

	public void setLocalport(int localport) throws SocketException, UnknownHostException, InterruptedException {
		logger.info("setLocalport({})", localport);
		this.localport = localport;
	}

	public String getRemotehost() {
		return remotehost;
	}

	public void setRemotehost(String remotehost) throws SocketException, UnknownHostException, InterruptedException {
		logger.info("setRemotehost({})", remotehost);
		this.remotehost = remotehost;
	}

	public int getRemoteport() {
		return remoteport;
	}

	public void setRemoteport(int remoteport) throws SocketException, UnknownHostException, InterruptedException {
		logger.info("setRemoteport({})", remoteport);
		this.remoteport = remoteport;
	}

	@Override
	public void run() {
		logger.info("run()");

		logger.info("Käynnistetään chat");

		try {
			initializeProtocol(this.protocol);
		} catch (SocketException e) {
			logger.error("Ei onnistu socketin saanti", e);
		} catch (InterruptedException e) {
			logger.error("Thread.join() ei toimi", e);
		}

		logger.debug("Käynnistetään pääluuppi");
		boolean running = true;
		try {
			while (running) try {
				String input = scanner.nextLine();
				logger.debug("Luettiin {}", input);
				Matcher helpMatcher = helpPattern.matcher(input);
				Matcher commandMatcher = commandPattern.matcher(input);
				if (helpMatcher.matches()) {
					//Täällä kysellään helppiä
					String command = helpMatcher.group(1).trim().toLowerCase();
					switch (command) {
					case "":
						printWriter.println("Katso saatavilla olevien komentojen helppi");
						printWriter.println("help (protocol|localhost|localport|remotehost|remoteport)");
						break;
					case "protocol":
						printWriter.println("Asetetaan protokolla");
						printWriter.println("set protocol (1.0|2.0|2.1|2.2|3.0|gobackn)");
						break;
					default:
						printWriter.println("Ei helppiä saatavana komennolle " + command);
						break;
					}
				} else if (commandMatcher.matches()) {
					//Tämä on komento
					String command = commandMatcher.group(1).toLowerCase();
					String parameter = commandMatcher.group(2).toLowerCase();
					switch (command) {
					case "protocol":
						initializeProtocol(parameter);
						break;
					case "localhost":
						setLocalhost(parameter);
						initializeProtocol(protocol);
						break;
					case "localport":
						setLocalport(Integer.valueOf(parameter));
						initializeProtocol(protocol);
						break;
					case "remotehost":
						setRemotehost(parameter);
						initializeProtocol(protocol);
						break;
					case "remoteport":
						setRemoteport(Integer.valueOf(parameter));
						initializeProtocol(protocol);
						break;
					default:
						throw new UnsupportedOperationException("Komentoa " + command + " ei ole toteutettu :(");
					}
				} else {
					// Muuten vain lähetetään
					rdt.send(input.getBytes());
				}

			} catch (UnsupportedOperationException e) {
				logger.error("Pitäis tehhä hommat loppuun..", e);
				continue;
			} catch (IllegalStateException e) {
				logger.error("Tilavirhe", e);
				continue;
			} catch (SocketException e) {
				logger.error("Ei onnistunu protokollan vaihto", e);
				continue;
			} catch (UnknownHostException e) {
				logger.error("Ei onnistu hostin vaihtaminen", e);
				continue;
			} catch (InterruptedException e) {
				logger.error("Thread.join() ei toimi", e);
				continue;
			} finally {
				printWriter.flush();
			}
		} finally {
			running = false;
			scanner.close();
			printWriter.close();
		}

	}

	public void initializeProtocol(String protocol) throws SocketException, InterruptedException {
		logger.debug("initializeProtocol({})", protocol);
		switch (protocol) {
		case "1.0":
			if (rdt != null) rdt.stopListening();
			if (serverThread != null) serverThread.join();
			rdt = new ReliableDataTransfer10Impl(localhost, localport, remotehost, remoteport);
			rdt.addReceiver(this);
			serverThread = new Thread(rdt);
			serverThread.start();
			break;
		case "2.0":
			if (rdt != null) rdt.stopListening();
			if (serverThread != null) serverThread.join();
			rdt = new ReliableDataTransfer20Impl(localhost, localport, remotehost, remoteport);
			rdt.addReceiver(this);
			serverThread = new Thread(rdt);
			serverThread.start();
			break;
		case "2.1":
			if (rdt != null) rdt.stopListening();
			if (serverThread != null) serverThread.join();
			rdt = new ReliableDataTransfer21Impl(localhost, localport, remotehost, remoteport);
			rdt.addReceiver(this);
			serverThread = new Thread(rdt);
			serverThread.start();
			break;
		case "2.2":
			if (rdt != null) rdt.stopListening();
			if (serverThread != null) serverThread.join();
			rdt = new ReliableDataTransfer22Impl(localhost, localport, remotehost, remoteport);
			rdt.addReceiver(this);
			serverThread = new Thread(rdt);
			serverThread.start();
			break;
		case "3.0":
			if (rdt != null) rdt.stopListening();
			if (serverThread != null) serverThread.join();
			rdt = new ReliableDataTransfer30Impl(localhost, localport, remotehost, remoteport);
			rdt.addReceiver(this);
			serverThread = new Thread(rdt);
			serverThread.start();
			break;
		case "gobackn":
			if (rdt != null) rdt.stopListening();
			if (serverThread != null) serverThread.join();
			rdt = new GoBackNImpl(localhost, localport, remotehost, remoteport, 10, 10000);
			rdt.addReceiver(this);
			serverThread = new Thread(rdt);
			serverThread.start();
			break;
		case "selectiverepeat":
			if (rdt != null) rdt.stopListening();
			if (serverThread != null) serverThread.join();
			rdt = new SelectiveRepeat(localhost, localport, remotehost, remoteport, 10, 10000);
			rdt.addReceiver(this);
			serverThread = new Thread(rdt);
			serverThread.start();
			break;
		default:
			break;
		}
	}

	public static void main(String[] args) throws SocketException, UnknownHostException, InterruptedException {
		ChatProgram chatProgram = new ChatProgram();
		if (args.length == 0) {	
		} else if (args.length == 4) {
			chatProgram.setLocalhost(args[0]);
			chatProgram.setLocalport(Integer.valueOf(args[1]));
			chatProgram.setRemotehost(args[2]);
			chatProgram.setRemoteport(Integer.valueOf(args[3]));
		} else if (args.length == 5) {
			chatProgram.setLocalhost(args[0]);
			chatProgram.setLocalport(Integer.valueOf(args[1]));
			chatProgram.setRemotehost(args[2]);
			chatProgram.setRemoteport(Integer.valueOf(args[3]));
			chatProgram.setProtocol(args[4]);
		} else {
			System.out.println("java com.olemassa.chat.ChatProgram [[localhost localport remotehost remoteport] protocol]");
		}
		chatProgram.run();
	}

	@Override
	public void receive(byte[] request) {
		logger.trace("receive({})", request);
		try{
			printWriter.println(new String(request));
		} finally {
			printWriter.flush();
		}
	}

}