package com.example.androiditagpersonaltracker;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String NAME_iTAG = "itag";
    private static final int REQUEST_ENABLE_BT = 1;

    Button startScanButton;
    Button stopScanButton;
    ListView deviceListView;

    ArrayAdapter<String> listAdapter;
    ArrayList<BluetoothDevice> deviceList;

    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner bluetoothLeScanner;
    BluetoothGatt bluetoothGatt;

    Timer timer;
    ArrayList<Double> distanceList = new ArrayList<>();
    private final static int MAX_DISTANCE_VALUES = 10;

    @SuppressLint({"MissingPermission", "NewApi"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startScanButton = findViewById(R.id.startScanButton);
        stopScanButton = findViewById(R.id.stopScanButton);
        deviceListView = findViewById(R.id.deviceListView);
        stopScanButton.setVisibility(View.INVISIBLE);

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        deviceList = new ArrayList<>();
        deviceListView.setAdapter(listAdapter);

        startScanButton.setOnClickListener(view -> startScanning());
        stopScanButton.setOnClickListener(view -> stopScanning());
        initializeBluetooth();

        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            stopScanning();
            listAdapter.clear();
            BluetoothDevice device = deviceList.get(position);
            device.connectGatt(this, true, gattCallback);
        });

        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect peripherals.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        }
    }

    final void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initializeBluetooth() {
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

    }

    @SuppressLint("MissingPermission")
    public void startScanning() {
        listAdapter.clear();
        deviceList.clear();
        startScanButton.setVisibility(View.INVISIBLE);
        stopScanButton.setVisibility(View.VISIBLE);
        AsyncTask.execute(() -> bluetoothLeScanner.startScan(leScanCallback));
    }

    @SuppressLint("MissingPermission")
    public void stopScanning() {
        startScanButton.setVisibility(View.VISIBLE);
        stopScanButton.setVisibility(View.INVISIBLE);
        AsyncTask.execute(() -> bluetoothLeScanner.stopScan(leScanCallback));
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result.getDevice() != null) {
                if (!isDuplicate(result.getDevice())) {
                    synchronized (result.getDevice()) {
                        if (result.getDevice().getName() != null && result.getDevice().getName().toLowerCase().contains(NAME_iTAG)) {
                            listAdapter.add(result.getDevice().getName());
                            deviceList.add(result.getDevice());
                        }
                    }
                }
            }
        }
    };

    @SuppressLint("MissingPermission")
    private boolean isDuplicate(BluetoothDevice device) {
        for (int i = 0; i < listAdapter.getCount(); i++) {
            String addedDeviceDetail = listAdapter.getItem(i);
            if (addedDeviceDetail.equals(device.getAddress()) || addedDeviceDetail.equals(device.getName())) {
                return true;
            }
        }
        return false;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i(TAG, "onConnectionStateChange: STATE_CONNECTED");
                bluetoothGatt = gatt;
//                Start a timer here
                timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
//                        Called each time after 5000 milliseconds
                        @SuppressLint("MissingPermission") boolean rssiStatus = bluetoothGatt.readRemoteRssi();
                    }
                }, 0, 5000);
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i(TAG, "onConnectionStateChange: STATE_DISCONNECTED");
                timer.cancel();
                timer = null;
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, String.format("BluetoothGatt ReadRssi[%d]", rssi));
                double distance = getDistance(rssi, 1);
                Log.i(TAG, "Distance is: " + distance);
                if (distanceList.size() == MAX_DISTANCE_VALUES) {
                    double sum = 0;
                    for (int i = 0; i < MAX_DISTANCE_VALUES; i++) {
                        sum = sum + distanceList.get(i);
                    }
                    final double averageDistance = sum / MAX_DISTANCE_VALUES;
                    distanceList.clear();
                    showToast("iTag is " + averageDistance + " mts. away");
                } else {
                    showToast("Gathering Data");
                    distanceList.add(distance);
                }
            }
        }
    };

    double getDistance(int rssi, int txPower) {
        /*
         * RSSI = TxPower - 10 * n * lg(d)
         * n is usually 2 or 4 in free space
         * d = 10 ^ ((TxPower - RSSI) / (10 * n))
         */
        return (Math.pow(10d, ((double) txPower - rssi) / (10 * 4))) / 10;
    }
}