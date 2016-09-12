package com.olemassa.chat;

public enum State {
	WAIT_FOR_REQUEST("Waiting for Request"),
	WAIT_FOR_ACK("Waiting for ACK");
	
	private String state;
	private byte sequence = 0;

	private State(String state) {
		this.state = state;
	}
	public String getState() {return state;}
	public byte getSequence() {return sequence;}
	public void setSequence(byte sequence) {this.sequence = sequence;}
	public void addSequence() {sequence = (byte) ((sequence + 1) % 2);}
}