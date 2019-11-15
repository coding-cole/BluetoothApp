package com.coding_cole.bluetoothapp;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;


public class MainActivity extends AppCompatActivity implements BluetoothAdapter.LeScanCallback {
	private static final String TAG = "BluetoothGattActivity";

	private static final String DEVICE_NAME = "SensorTag";

	// Humidity service
	private static final UUID HUMIDITY_SERVICE = UUID.fromString("f000aa20-0451-4000-b000-000000000");
	private static final UUID HUMIDITY_DATA_CHAR = UUID.fromString("f000aa21-0451-4000-b000-000000000");
	private static final UUID HUMIDITY_CONFIG_CHAR = UUID.fromString("f000aa22-0451-4000-b000-000000000");

	// Barometric pressure service
	private static final UUID PRESSURE_SERVICE = UUID.fromString("f000aa40-0451-4000-b000-000000000");
	private static final UUID PRESSURE_DATA_CHAR = UUID.fromString("f000aa41-0451-4000-b000-000000000");
	private static final UUID PRESSURE_CONFIG_CHAR = UUID.fromString("f000aa42-0451-4000-b000-000000000");
	private static final UUID PRESSURE_CAL_CHAR = UUID.fromString("f000aa43-0451-4000-b000-000000000");

	// Client configuration description
	private static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	private BluetoothAdapter mBluetoothAdapter;
	private SparseArray<BluetoothDevice> mDevices;

	private BluetoothGatt mConnectedGatt;

	private TextView mTemperature, mHumidity, mPressure;

	private ProgressDialog mProgress;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.activity_main);

//		Toolbar toolbar = findViewById(R.id.toolbar);
//		setSupportActionBar(toolbar);

		setProgressBarIndeterminate(true);

//		FloatingActionButton fab = findViewById(R.id.fab);
//		fab.setOnClickListener(new View.OnClickListener() {
//			@Override
//			public void onClick(View view) {
//				Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//					.setAction("Action", null).show();
//			}
//		});

		//view to display results
		mTemperature = (TextView) findViewById(R.id.text_temp);
		mHumidity = (TextView) findViewById(R.id.text_humidity);
		mPressure = (TextView) findViewById(R.id.text_pressure);

		BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
		mBluetoothAdapter = manager.getAdapter();

		mDevices = new SparseArray<BluetoothDevice>();

		/* a progress dialog will be needed while the
		 * connection process is taking place
		 */

		mProgress = new ProgressDialog(this);
		mProgress.setIndeterminate(true);
		mProgress.setCancelable(false);
	}

	@Override
	protected void onResume() {
		super.onResume();

		/* we need to enforce that bluetooth is first enabled
		 * and if not take the use to settings
		 */
		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
			// Bluetooth is disabled
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivity(enableBtIntent);
			finish();
			return;
		}


		/* check for Bluetooth LE support. In production, our manifest entry will keep this
		 * from installing on these devices, but this will allow test devices or other
		 * sideloads to report whether or not the feature exists
		 */
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, "No LE Support.", Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		// make sure that dialog is hidden
		mProgress.dismiss();

		// cancel any scan in progress
		mHandler.removeCallbacks(mStopRunnable);
		mHandler.removeCallbacks(mStartRunnable);
		mBluetoothAdapter.stopLeScan(this);
	}

	@Override
	protected void onStop() {
		super.onStop();

		// diconnect from any active tag connection
		if (mConnectedGatt != null) {
			mConnectedGatt.disconnect();
			mConnectedGatt = null;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		// add the scan option to the menu
		getMenuInflater().inflate(R.menu.menu_main, menu);
		// add any device we've discovered to the overflow menu
		for (int i = 0; i < mDevices.size(); i++) {
			BluetoothDevice device = mDevices.valueAt(i);
			menu.add(0, mDevices.keyAt(i), 0, device.getName());
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.action_scan:
				mDevices.clear();
				startScan();
				return true;

			default:
				// obtain the discovered devices to connect with
				BluetoothDevice device = mDevices.get(item.getItemId());
				Log.i(TAG, "Connecting to  " + device.getName());

				/*
				 * Make a connection with the device uding the special LE-specific
				 * connectGatt() method, passing in a callback for GATT events
				 */
				mConnectedGatt = device.connectGatt(this, true, mGattCallback);

				// display progress ui
				mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Connecting to " + device.getName() + "..."));

				return super.onOptionsItemSelected(item);
		}
	}

	private void clearDisplayValue() {
		mTemperature.setText("___");
		mHumidity.setText("___");
		mPressure.setText("___");
	}

	private Runnable mStopRunnable = new Runnable() {
		@Override
		public void run() {
			stopScan();
		}
	};

	private Runnable mStartRunnable = new Runnable() {
		@Override
		public void run() {
			startScan();
		}
	};

	private void startScan() {
		mBluetoothAdapter.startLeScan(this);
		setProgressBarIndeterminateVisibility(true);

		mHandler.postDelayed(mStopRunnable, 2500);
	}

	private void stopScan() {
		mBluetoothAdapter.stopLeScan(this);
		setProgressBarIndeterminateVisibility(false);
	}

	// BluetootAdapter.LeScanCallback


	@Override
	public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
		Log.i(TAG, "New LE Device: " + device.getName() + " @ " + rssi);
		/*
		 * We are looking for sensory tag devices only, so validate th name
		 * that each device reports before adding it to our collection
		 */
		if (DEVICE_NAME.equals(device.getName())) {
			mDevices.put(device.hashCode(), device);
			invalidateOptionsMenu();
		}
	}

	/*
	 * In this callback we've created a bit of a state machine to enforce that only
	 * one characteristic be read or written at a time until all of our sensors
	 * are enabled and we are registered to get notification
	 */

	private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

		// state machine tracking
		private int mState = 0;

		private void reset() {
			mState = 0;
		}

		private void advance() {
			mState++;
		}

		/*
		 * Send an enable command to each sensor by writing a config
		 * characteristic. This is specific to the SensorTag to keep power
		 * low by disableing sensors you arent using.
		 */
		private void enableNextSensor(BluetoothGatt gatt) {
			BluetoothGattCharacteristic characteristic;

			switch (mState) {
				case 0:
					Log.d(TAG, "Enabling pressure cal");
					characteristic = gatt.getService(PRESSURE_SERVICE)
							.getCharacteristic(PRESSURE_CONFIG_CHAR);
					characteristic.setValue(new byte[]{0x02});
					break;

				case 1:
					Log.d(TAG, "Enabling Pressure");
					characteristic = gatt.getService(PRESSURE_SERVICE)
							.getCharacteristic(PRESSURE_CONFIG_CHAR);
					characteristic.setValue(new byte[]{0x01});
					break;

				case 2:
					Log.d(TAG, "Enabling humidity");
					characteristic = gatt.getService(HUMIDITY_SERVICE)
							.getCharacteristic(HUMIDITY_CONFIG_CHAR);
					characteristic.setValue(new byte[]{0x01});
					break;

				default:
					mHandler.sendEmptyMessage(MSG_DISMISS);
					Log.i(TAG, "All Sensors Enabled");
					return;
			}
			gatt.writeCharacteristic(characteristic);
		}

		// read the data characteristic's value for each sensor explicitly
		private void readNextSensor(BluetoothGatt gatt) {
			BluetoothGattCharacteristic characteristic;

			switch (mState) {
				case 0:
					Log.d(TAG, "Reading pressure cal");
					characteristic = gatt.getService(PRESSURE_SERVICE)
							.getCharacteristic(PRESSURE_CAL_CHAR);
					break;

				case 1:
					Log.d(TAG, "Reading pressure");
					characteristic = gatt.getService(PRESSURE_SERVICE)
							.getCharacteristic(PRESSURE_DATA_CHAR);
					break;

				case 2:
					Log.d(TAG, "Reading humidity");
					characteristic = gatt.getService(HUMIDITY_SERVICE)
							.getCharacteristic(HUMIDITY_DATA_CHAR);
					break;

				default:
					return;
			}
			gatt.readCharacteristic(characteristic);
		}

		/*
		 * Enable notification of cahnges on the data characteristic for each sensor
		 * by writing the ENABLE_NOTIFICATION_VALUE flag to that characteristic's
		 * configuration descriptor
		 */
		private void setNotifyNextSensor(BluetoothGatt gatt) {
			BluetoothGattCharacteristic characteristic;

			switch (mState) {
				case 0:
					Log.d(TAG, "Set notify pressure cal");
					characteristic = gatt.getService(PRESSURE_SERVICE)
							.getCharacteristic(PRESSURE_CAL_CHAR);
					break;

				case 1:
					Log.d(TAG, "Set notify Pressure");
					characteristic = gatt.getService(PRESSURE_SERVICE)
							.getCharacteristic(PRESSURE_DATA_CHAR);
					break;

				case 2:
					Log.d(TAG, "Set notify humidity");
					characteristic = gatt.getService(HUMIDITY_SERVICE)
							.getCharacteristic(HUMIDITY_CONFIG_CHAR);
					break;

				default:
					mHandler.sendEmptyMessage(MSG_DISMISS);
					Log.i(TAG, "All Sensors Enabled");
					return;
			}

			// Enable local notification
			gatt.setCharacteristicNotification(characteristic, true);

			// Enabled remote notification
			BluetoothGattDescriptor desc = characteristic.getDescriptor(CONFIG_DESCRIPTOR);
			desc.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
			gatt.writeDescriptor(desc);
		}

		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			Log.d(TAG, "onConnection State Change: " + status + " -> " + connectionState(newState));
			if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {

				/*
				 * Once sucessfully connected, we must next discover the services on the
				 * device before we can read and write their characteristics.
				 */
				gatt.discoverServices();
				mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Discovering Services..."));
			} else if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {

				// If at any point we disconnect, send a message to clear the whether values out of the ui.
				mHandler.sendEmptyMessage(MSG_CLEAR);
			} else if (status != BluetoothGatt.GATT_SUCCESS) {

				// If there is a failure at any stage, simply disconnect.
				gatt.disconnect();
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			Log.d(TAG, "onServices Discovered: " + status);
			mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Enabling sensors..."));
			/*
			 * with services discovered, we are going to reset our state and start working
			 * through the sensors we need to enable.
			 */
			reset();
			enableNextSensor(gatt);
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			// for each read, pass the data up to the ui thread to update the display
			if (HUMIDITY_DATA_CHAR.equals(characteristic.getUuid())) {
				mHandler.sendMessage(Message.obtain(null, MSG_HUMIDITY, characteristic));
			}
			if (PRESSURE_DATA_CHAR.equals(characteristic.getUuid())) {
				mHandler.sendMessage(Message.obtain(null, MSG_PRESSURE, characteristic));
			}
			if (PRESSURE_CAL_CHAR.equals(characteristic.getUuid())) {
				mHandler.sendMessage(Message.obtain(null, MSG_PRESSURE_CAL, characteristic));
			}

			// After reading the initial value, next we enable notification
			setNotifyNextSensor(gatt);
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

			// After writing the enable tag, we read the initial value
			readNextSensor(gatt);
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

			/*
			 * After notifications are enabled, all updates from the device on characteristic
			 * value changes will be posted here. Similar to read, we hand this up to
			 * the ui thread to update the display
			 */
			if (HUMIDITY_DATA_CHAR.equals(characteristic.getUuid())) {
				mHandler.sendMessage(Message.obtain(null, MSG_HUMIDITY, characteristic));
			}
			if (PRESSURE_DATA_CHAR.equals(characteristic.getUuid())) {
				mHandler.sendMessage(Message.obtain(null, MSG_PRESSURE, characteristic));
			}
			if (PRESSURE_CAL_CHAR.equals(characteristic.getUuid())) {
				mHandler.sendMessage(Message.obtain(null, MSG_PRESSURE_CAL, characteristic));
			}
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

			// once notifications are enabled, we move to the next sensor and start over with enable
			advance();
			enableNextSensor(gatt);
		}


		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			Log.d(TAG, "onReadRemote RSSI: " + rssi);
		}

		private String connectionState(int status) {
			switch (status) {
				case BluetoothProfile.STATE_CONNECTED:
					return "Connected";

				case BluetoothProfile.STATE_DISCONNECTED:
					return "Disconnected";

				case BluetoothProfile.STATE_CONNECTING:
					return "Connecting";

				case BluetoothProfile.STATE_DISCONNECTING:
					return "Disconnecting";

				default:
					return String.valueOf(status);
			}
		}
	};

	// we have Handler to process event result on the main thresd
	private static final int MSG_HUMIDITY = 101;
	private static final int MSG_PRESSURE = 102;
	private static final int MSG_PRESSURE_CAL = 103;
	private static final int MSG_PROGRESS = 201;
	private static final int MSG_DISMISS = 202;
	private static final int MSG_CLEAR = 301;

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			BluetoothGattCharacteristic characteristic;
			switch (msg.what) {
				case MSG_HUMIDITY:
					characteristic = (BluetoothGattCharacteristic) msg.obj;
					if (characteristic.getValue() == null) {
						Log.w(TAG, "Error obtaining humidity value");
						return;
					}
					updateHumidityValues(characteristic);
					break;

				case MSG_PRESSURE:
					characteristic = (BluetoothGattCharacteristic) msg.obj;
					if (characteristic.getValue() == null) {
						Log.w(TAG, "Error obtaining pressure value");
						return;
					}
					updatePressureValues(characteristic);
					break;

				case MSG_PRESSURE_CAL:
					characteristic = (BluetoothGattCharacteristic) msg.obj;
					if (characteristic.getValue() == null) {
						Log.w(TAG, "Error obtaining cal value");
						return;
					}
					updatePressureCals(characteristic);
					break;

				case MSG_PROGRESS:
					mProgress.setMessage((String) msg.obj);
					if (!mProgress.isShowing()) {
						mProgress.show();
					}

				case MSG_DISMISS:
					mProgress.hide();
					break;

				case MSG_CLEAR:
					clearDisplayValue();
					break;
			}

		}

		;

		private void updateHumidityValues(BluetoothGattCharacteristic characteristic) {
			double humidity = SensorTagData.extractHumidity(characteristic);
			mHumidity.setText(String.format("%.0f%%", humidity));
		}

		private int[] mPressureCals;

		private void updatePressureCals(BluetoothGattCharacteristic characteristic) {
			mPressureCals = SensorTagData.extractCalibrationCoefficients(characteristic);
		}

		private void updatePressureValues(BluetoothGattCharacteristic characteristic) {
			if (mPressureCals == null) return;
			double pressure = SensorTagData.extractBarometer(characteristic, mPressureCals);
			double temp = SensorTagData.extractBarTemp(characteristic, mPressureCals);

			mTemperature.setText(String.format("%.1f\u0000C", temp));
			mPressure.setText(String.format("%.2f", pressure));
		}
	};
}