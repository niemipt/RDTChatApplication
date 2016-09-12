package com.olemassa.chat;

//Just an interface for RDT protocol implementations
public interface ReliableDataTransfer extends Runnable {
	void send(byte[] outbound) throws IllegalStateException;
	void addReceiver(Receiver receiver);
	void stopListening();
}
