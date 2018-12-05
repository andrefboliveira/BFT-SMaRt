package bftsmart.tom.server.defaultservices;

import java.util.Arrays;

public class Test {
	static byte[][] concat(byte[][]... arrays) {
		// Determine the length of the result array
		int totalLength = 0;
		for (int i = 0; i < arrays.length; i++) {
			totalLength += arrays[i].length;
		}

		// create the result array
		byte[][] result = new byte[totalLength][];

		// copy the source arrays into the result array
		int currentIndex = 0;
		for (int i = 0; i < arrays.length; i++) {
			System.arraycopy(arrays[i], 0, result, currentIndex, arrays[i].length);
			currentIndex += arrays[i].length;
		}

		return result;
	}

	public static void main(String[] args) {
		byte[][] a = new byte[5][];
		a[0] = new byte[3];
		a[1] = new byte[4];

		byte[][] b = new byte[3][];
		b[0] = new byte[6];
		b[1] = new byte[1];
		b[2] = new byte[7];


		for (byte[] bytes : a) {
			System.out.println(Arrays.toString(bytes));
		}

		System.out.println("");

		for (byte[] bytes : b) {
			System.out.println(Arrays.toString(bytes));
		}

		byte[][] c = concat(a, b);

		System.out.println("");

		System.out.println("CONCAT");

		for (byte[] bytes : c) {
			System.out.println(Arrays.toString(bytes));
		}

	}
}
