package com.teamshortcut.waterwatcher;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.alespero.expandablecardview.ExpandableCardView;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.List;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class SettingsActivity extends AppCompatActivity {
    //TODO: convert strings in .xml to the strings.xml file

    //TODO: UUID conversion should be consistent (remove the formatUUID function and just use the java one)
    //TODO: update MainActivity with onServicesDiscovered

    //BLUETOOTH CONSTANTS

    //Should be in the form "0000AAAA-0000-1000-8000-00805f9b34fb" where "AAAA" is to be replaced
    public static String BLE_SIGNATURE_UUID_BASE_START = "0000";
    public static String BLE_SIGNATURE_UUID_BASE_END = "-0000-1000-8000-00805f9b34fb";

    //Each Service has Characteristics, which are used to read/write data
    public static String UARTSERVICE_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static String UART_RX_CHARACTERISTIC_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"; //Reads from the micro:bit
    public static String UART_TX_CHARACTERISTIC_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"; //Writes to the micro:bit
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    public static String TARGET_ADDRESS = "C7:D7:2F:2F:2D:8E"; //MAC address of the micro:bit

    //Numerical IDs, used internally
    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 10;

    //Bluetooth variables
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice targetDevice;
    BluetoothGatt gattClient; //The GATT Client is what scans and requests data over BLE; in this case, the Android device

    //Defines what to happen upon a BLE scan
    public final BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            if(bluetoothDevice.getAddress().equals(TARGET_ADDRESS)){ //Scans for the target micro:bit device until it is found
                targetDevice = bluetoothDevice;
            }
        }
    };

    //Called after a request for an Android permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                //If request is cancelled, the result arrays are empty
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission was granted, start the Bluetooth scan
                    bluetoothAdapter.startLeScan(scanCallback);
                } else {
                    //Permission was denied
                    Toast.makeText(getApplicationContext(), R.string.location_request, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private String getAndFormatSettings(){
        String settings;
        EditText timerEditText = (EditText) findViewById(R.id.timer_textbox);
        Spinner periodSpinner = (Spinner) findViewById(R.id.period_spinner);
        EditText samplesEditText = (EditText) findViewById(R.id.samples_textbox);
        ExpandableCardView card = findViewById(R.id.advanced);
        EditText xEditText = card.findViewById(R.id.x_textbox);
        EditText yEditText = card.findViewById(R.id.y_textbox);
        EditText thresholdEditText = card.findViewById(R.id.threshold_textbox);
        settings = timerEditText.getText() + "," + periodSpinner.getSelectedItem().toString() + "," + samplesEditText.getText() + "," + xEditText.getText() + "," + yEditText.getText() + "," + thresholdEditText.getText();
        return settings;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Initialises up BLE objects
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter(); //TODO: handle null exception?

        //Ensures bluetooth is enabled
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //Defines what to do when the device is connected; handles discovering and interacting with BLE services
        final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == STATE_CONNECTED){ //Once a device is connected...
                    gatt.discoverServices(); //...discover its services
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status){
                boolean enabled = true;
                //Loops through all available services on the device
                if (status == BluetoothGatt.GATT_SUCCESS){
                    List<BluetoothGattService> gattServices = gatt.getServices();
                    for (BluetoothGattService gattService : gattServices){
                        Log.i("Services Discovered", gattService.getUuid().toString());
                    }
                }

                //Gets the accelerometer service from GATT Client (the mobile phone)
                BluetoothGattService uartService = gatt.getService(java.util.UUID.fromString(UARTSERVICE_SERVICE_UUID));

                //Sometimes only generic services are initially found, so the accelerometer service will return null even if it exists on the device
                if (uartService != null){
                    BluetoothGattCharacteristic uartReadCharacteristic = uartService.getCharacteristic(java.util.UUID.fromString(UART_RX_CHARACTERISTIC_UUID));

                    //Sets up notifications for the Accelerometer Data characteristic. When the characteristic has changed (ie. data has been sent), onCharacteristicChanged() will be called
                    gatt.setCharacteristicNotification(uartReadCharacteristic, enabled);

                    //GATT Descriptor is used to write to the micro:bit, to enable notifications and tell the device to start streaming data
                    BluetoothGattDescriptor uartDescriptor = uartReadCharacteristic.getDescriptor(java.util.UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                    uartDescriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    gatt.writeDescriptor(uartDescriptor);
                }
                else{ //If the UART service was not found
                    Log.e("BLE Services", "Refreshing services");
                    try{
                        /*Android's BluetoothGatt has a method "refresh" that will clear the internal cache and force a refresh of the services from the device
                        This is because Android only requests the device once to discover its services, and all subsequent calls to discoverServices simply fetch the cached results from the first call
                        However, this method is normally inaccessible, and to call it, reflection - a feature of Java that allows the program to examine itself and manipulate its internal properties - must be used.
                        Calling this "refresh" method will force a rediscovery of all BLE services, causing the non-generic services to be found if they weren't previously.
                        */
                        BluetoothGatt localGatt = gatt;
                        Method localMethod = localGatt.getClass().getMethod("refresh", new Class[0]); //Gets the "refresh" method
                        if (localMethod != null){
                            boolean bool = ((Boolean) localMethod.invoke(localGatt, new Object[0])).booleanValue(); //Invokes the method, essentially forcing BLE services to be rediscovered
                        }
                    }
                    catch (Exception localException){
                        Log.e("BLE Services", "An exception occurred while refreshing the device");
                    }
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                byte[] data  = characteristic.getValue(); //The data the micro:bit has sent over BLE
                int length = data.length;
                byte[] bytes = new byte[length];
                System.arraycopy(data, 0, bytes, 0, length); //System.arraycopy() is used for performance+efficiency
                String ascii = "NULL";
                try { //Convert from a bytearray to a string
                    ascii = new String(bytes,"US-ASCII");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    Log.i("BLE Data Received", "ENCODING ERROR");
                }
                Log.i("BLE Data Received", ascii);
            }
        };

        //If running Android M or higher, explicitly request location permission (necessary for Bluetooth)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }
        else{ //Otherwise, notify the user that location permission must be granted for Bluetooth to function correctly
            Toast.makeText(getApplicationContext(), R.string.old_version_location_message, Toast.LENGTH_LONG).show();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (targetDevice == null){ //If targetDevice is null, the micro:bit has not yet been found in the BLE scan
                    Snackbar.make(view, R.string.no_microbit_found, Snackbar.LENGTH_LONG).setAction("Action", null).show();
                }
                else{
                    Snackbar.make(view, R.string.microbit_found, Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    gattClient = targetDevice.connectGatt(SettingsActivity.this, false, gattCallback); //Connects the GATT callback to start receiving data; autoConnect is set to True
                }
            }
        });

        final Button button = (Button) findViewById(R.id.settings_button);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (bluetoothManager.getConnectionState(targetDevice, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED){
                    try {
                        String text = getAndFormatSettings() + "\\";
                        Log.i("Data", text);
                        byte[] ascii = text.getBytes("US-ASCII"); //Convert from string to a bytearray to be sent
                        BluetoothGattService gattService = gattClient.getService(java.util.UUID.fromString(UARTSERVICE_SERVICE_UUID));
                        BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(java.util.UUID.fromString(UART_TX_CHARACTERISTIC_UUID));
                        //Write (send) the bytearray to the micro:bit over BLE
                        characteristic.setValue(ascii);
                        gattClient.writeCharacteristic(characteristic);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
                else{
                    Snackbar.make(view, "No micro:bit connected!", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    Log.e("BLE Connection State", "Attempted to send settings with no available connection");
                }
            }
        });
    }
}
