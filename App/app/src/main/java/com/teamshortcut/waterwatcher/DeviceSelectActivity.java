package com.teamshortcut.waterwatcher;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class DeviceSelectActivity extends AppCompatActivity {
    //Numerical IDs, used internally
    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 10;

    //Bluetooth variables
    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner scanner;

    static class ViewHolder{ //Contains the views required for each BluetoothDevice to be displayed in the listview. Corresponds to device_list_item.xml
        public TextView name;
        public TextView address;
    }

    private BLEListAdapter deviceListAdapter; //Used for the list of BluetoothDevices
    private boolean scanning = false;
    private boolean enabled = false; //Tracks if bluetooth is enabled and permissions are granted

    //Called after a request for an Android permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                //If request is cancelled, the result arrays are empty
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission was granted
                    enabled = true;
                } else {
                    //Permission was denied
                    Toast.makeText(getApplicationContext(), getString(R.string.location_request), Toast.LENGTH_LONG).show();
                    enabled = false;
                }
            }
        }
    }

    //Called during a BLE scan
    private ScanCallback scanCallback =  new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (!scanning){ //If the app should not be scanning at the moment, exit
                return;
            }
            deviceListAdapter.addDevice(result.getDevice()); //Add new device to the list of available BLE devices
            Log.i(ConnectionService.LOG_BLE_DEVICE, String.valueOf(result.getDevice()));
            deviceListAdapter.notifyDataSetChanged(); //Refreshes the ListAdapter
        }
    };

    private void startScanning(){
        if (enabled){
            deviceListAdapter.clear(); //Clears the list of BluetoothDevices
            scanner.startScan(scanCallback);
            scanning = true;
        }
        else{
            Toast.makeText(getApplicationContext(), R.string.bluetooth_enabled, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_select);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        deviceListAdapter = new BLEListAdapter(){
            @Override
            public View getView(int position, View convertView, ViewGroup parent) { //defines the view that is displayed for each item in the list
                final ViewHolder viewHolder;
                if (convertView == null){
                    //Assigns the views in device_list_item.xml to the viewHolder object, to be used in the ListView in this activity
                    convertView = DeviceSelectActivity.this.getLayoutInflater().inflate(R.layout.device_list_item, null);
                    viewHolder = new ViewHolder();
                    viewHolder.name = (TextView) convertView.findViewById(R.id.name);
                    viewHolder.address = (TextView) convertView.findViewById(R.id.address);
                    convertView.setTag(viewHolder);
                }
                else{
                    viewHolder = (ViewHolder) convertView.getTag(); //gets the previously defined ViewHolder
                }

                BluetoothDevice bluetoothDevice = this.getDevice(position); //gets the corresponding device
                String deviceName = bluetoothDevice.getName();
                if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED){
                    deviceName = getString(R.string.bonded_prefix) + deviceName; //Indicates if the device is bonded/paired with the smartphone already
                }
                if(deviceName != null && deviceName.length() > 0){ //If the discovered name is not null or empty
                    viewHolder.name.setText(deviceName);
                    if (deviceName.startsWith(getString(R.string.microbit_name_start)) || deviceName.startsWith(getString(R.string.bonded_prefix) + getString(R.string.microbit_name_start))){
                        //Highlight in textured green any devices that are likely to be micro:bits
                        //viewHolder.name.setBackgroundColor(Color.GREEN);
                        Drawable stripes = getResources().getDrawable(R.drawable.green_stripes);
                        stripes.setAlpha(155);
                        viewHolder.name.setBackground(stripes);
                        viewHolder.name.setTextColor(Color.BLACK); //Ensures text stands out from the background
                    }
                }
                else{
                    viewHolder.name.setText(getString(R.string.unknown_device));
                }
                viewHolder.address.setText(bluetoothDevice.getAddress());

                return convertView; //returns the view to be displayed in the ListView containing the information about the device
            }
        };

        ListView listView = (ListView) this.findViewById(R.id.listView);
        listView.setAdapter(deviceListAdapter);

        //Initialises BLE objects
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
        else{
            Toast.makeText(getApplicationContext(), R.string.bluetooth_enabled, Toast.LENGTH_LONG).show();
        }

        scanner = bluetoothAdapter.getBluetoothLeScanner();

        //Ensures bluetooth is enabled
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //If running Android M or higher, explicitly request location permission (necessary for Bluetooth)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        } else { //Otherwise, notify the user that location permission must be granted for Bluetooth to function correctly
            Toast.makeText(getApplicationContext(), R.string.old_version_location_message, Toast.LENGTH_LONG).show();
        }

        final Button button = (Button) findViewById(R.id.scan_button);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (!scanning){
                    startScanning();
                    button.setText(getString(R.string.stop_scanning));
                }
                else{
                    scanner.stopScan(scanCallback);
                    scanning = false;
                    button.setText(getString(R.string.start_scanning));
                }
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice targetDevice = deviceListAdapter.getDevice(position); //gets the selected BluetoothDevice
                if (targetDevice.getBondState() == BluetoothDevice.BOND_NONE){ //cannot connect to a device that has not been bonded/paired
                    Toast.makeText(getApplicationContext(), R.string.must_pair_device, Toast.LENGTH_LONG).show();
                    return; //exit
                }
                if (scanning){ //Stop the current scan, if one is occurring
                    scanner.stopScan(scanCallback);
                    scanning = false;
                    button.setText(getString(R.string.start_scanning));
                }
                //Launch GraphingActivity, passing the name and address of the selected BluetoothDevice
                Intent intent = new Intent(DeviceSelectActivity.this, InstructionsActivity.class);
                intent.putExtra(ConnectionService.INTENT_DEVICE_ADDRESS, targetDevice.getAddress());
                startActivity(intent);
                finish(); //Destroys this activity
            }
        });
    }
}
