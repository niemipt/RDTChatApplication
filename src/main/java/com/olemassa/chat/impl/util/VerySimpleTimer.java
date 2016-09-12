package com.olemassa.chat.impl.util;

public class VerySimpleTimer {
	
	private long startTime;
	private long timeout;
	
	public VerySimpleTimer(long timeout) {
		startTime = System.currentTimeMillis();
		this.timeout = timeout;
	}
	
	public long elapsedTime() {
		return System.currentTimeMillis() - startTime;
	}
	
	public void reset() {
		startTime = System.currentTimeMillis();
	}
	
	public boolean timeOver() {
		return elapsedTime() > timeout;
	}
	
}