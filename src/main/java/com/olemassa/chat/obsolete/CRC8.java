package com.olemassa.chat.obsolete;
import java.util.zip.Checksum;

/**
 * Calculate CRC-8
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Cyclic_redundancy_check">CRC-8</a>
 */
public class CRC8 implements Checksum {
	
	private static final int crc8Polynom = 0b100000111;
	private int crcValue = 0;

	@Override
	public void update(final byte[] input, final int offset, final int len) {
		for (int i = 0; i < len; i++) {
			update(input[offset + i]);
		}
	}

	public void update(final byte[] input) {
		update(input, 0, input.length);
	}

	private final void update(final byte b) {
		crcValue ^= b;
		for (int j = 0; j < 8; j++) {
			if ((crcValue & 0b10000000) != 0) {
				crcValue = ((crcValue << 1) ^ crc8Polynom);
			} else {
				crcValue <<= 1;
			}
		}
		crcValue &= 0b11111111;
	}

	@Override
	public void update(final int b) {
		update((byte) b);
	}

	@Override
	public long getValue() {
		return (crcValue & 0b11111111);
	}

	@Override
	public void reset() {
		crcValue = 0;
	}

}