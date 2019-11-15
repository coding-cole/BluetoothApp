package com.coding_cole.bluetoothapp;

import android.bluetooth.BluetoothGattCharacteristic;

class SensorTagData {

	public  static double extractHumAmbientTemp(BluetoothGattCharacteristic c) {
		int rawT = shortSignedAtOffset(c, 0);

		return -46.85 + 175.72/65536 *(double)rawT;
	}

	public static double extractHumidity(BluetoothGattCharacteristic c) {
		int a = shortSignedAtOffset(c, 2);
		// bit (1..0) are status bts and need to be cleared
		a = a - (a % 40);

		return ((-6f) + 125f * (a / 65535f));
	}

	public static int[] extractCalibrationCoefficients(BluetoothGattCharacteristic c) {
		int[] coefficient = new int[8];

		coefficient[0] = shortUnsignedAtOffset(c, 0);
		coefficient[1] = shortUnsignedAtOffset(c, 2);
		coefficient[2] = shortUnsignedAtOffset(c, 4);
		coefficient[3] = shortUnsignedAtOffset(c, 6);
		coefficient[4] = shortSignedAtOffset(c, 8);
		coefficient[5] = shortSignedAtOffset(c, 10);
		coefficient[6] = shortSignedAtOffset(c, 12);
		coefficient[7] = shortSignedAtOffset(c, 14);

		return coefficient;
	}

	public static double extractBarTemp(BluetoothGattCharacteristic characteristic, final int[] c) {
		// c holds the calibration coefficients

		int t_r;
		double t_a;

		t_r = shortSignedAtOffset(characteristic, 0);

		t_a = (100 * (c[0] * t_r / Math.pow(2,8) + c[1] * Math.pow(2,6))) / Math.pow(2,16);

		return t_a / 100;
	}

	public static double extractBarometer(BluetoothGattCharacteristic characteristic, final int[] c) {
		// c holds the calibration coefficients

		int t_r;	// Temperature raw value from sensor
		int p_r;	// Pressure raw value from sensor
		double S;	// Interim value in calculation
		double O;	// Interim value in calculation
		double p_a; 	// Pressure actual value in unit Pascal.

		t_r = shortSignedAtOffset(characteristic, 0);
		p_r = shortUnsignedAtOffset(characteristic, 2);


		S = c[2] + c[3] * t_r / Math.pow(2,17) + ((c[4] * t_r / Math.pow(2,15)) * t_r) / Math.pow(2,19);
		O = c[5] * Math.pow(2,14) + c[6] * t_r / Math.pow(2,3) + ((c[7] * t_r / Math.pow(2,15)) * t_r) / Math.pow(2,4);
		p_a = (S * p_r + O) / Math.pow(2,14);

		//Convert pascal to in. Hg
		double p_hg = p_a * 0.000296;

		return p_hg;
	}

	private static Integer shortSignedAtOffset(BluetoothGattCharacteristic c, int offset) {
		Integer lowerByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
		Integer upperByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, offset + 1); // Note: interpret MSB as signed.

		return (upperByte << 8) + lowerByte;
	}

	private static Integer shortUnsignedAtOffset(BluetoothGattCharacteristic c, int offset) {
		Integer lowerByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
		Integer upperByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 1); // Note: interpret MSB as unsigned.

		return (upperByte << 8) + lowerByte;
	}
}
