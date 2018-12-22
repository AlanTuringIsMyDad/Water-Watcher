package com.teamshortcut.waterwatcher;

import android.app.Service;
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
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.UUID.fromString;

//TODO: improve debug messages; standardise .d vs .i (.i should be just received data maybe) and tags (eg. with constants) (across all files)

public class ConnectionService extends Service {
    //STATUS ID CONSTANTS
    public static final int GATT_CONNECTED = 1;
    public static final int GATT_DISCONNECTED = 2;
    public static final int GATT_SERVICES_DISCOVERED = 3;
    public static final int NOTIFICATION_INDICATION_RECEIVED = 4;
    public static final int GATT_DESCRIPTOR_WRITTEN = 5;

    //BUNDLE MESSAGE ID CONSTANTS
    public static final String BUNDLE_DESCRIPTOR_UUID = "DESCRIPTOR_UUID";
    public static final String BUNDLE_CHARACTERISTIC_UUID = "CHARACTERISTIC UUID";
    public static final String BUNDLE_SERVICE_UUID = "SERVICE UUID";
    public static final String BUNDLE_VALUE = "VALUE";

    //BLUETOOTH UUID CONSTANTS
    //Each Service has Characteristics, which are used to read/write data
    public static String GENERICACCESS_SERVICE_UUID = "00001800-0000-1000-8000-00805f9b34fb";
    public static String DEVICENAME_CHARACTERISTIC_UUID = "00002a00-0000-1000-8000-00805f9b34fb";
    public static String ACCELEROMETERSERVICE_SERVICE_UUID = "e95d0753-251d-470a-a062-fa1922dfa9a8";
    public static String ACCELEROMETERDATA_CHARACTERISTIC_UUID = "e95dca4b-251d-470a-a062-fa1922dfa9a8";
    public static String ACCELEROMETERPERIOD_CHARACTERISTIC_UUID = "e95dfb24-251d-470a-a062-fa1922dfa9A8";
    public static String UARTSERVICE_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static String UART_RX_CHARACTERISTIC_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"; //Reads from the micro:bit
    public static String UART_TX_CHARACTERISTIC_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"; //Writes to the micro:bit
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    //Bluetooth Objects
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothManager bluetoothManager;
    private BluetoothDevice device;

    private Handler activityHandler; //The handler assigned to this service

    private long timestamp; //The current System time in milliseconds at the time of last BLE activity
    private KeepAlive keepAlive; //Creates an instance of the KeepAlive class where timestamp is used

    private boolean connected = false; //Tracks connection state
    private boolean servicesDiscovered = false; //Tracks if services have been discovered yet

    public class LocalBinder extends Binder { //Used to return a ConnectionService instance when bound as an Android Service
        public ConnectionService getService() {
            return ConnectionService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    private void initialiseVariables(){ //Resets all variables to their default values, ready for a new connection
        bluetoothAdapter = null;
        bluetoothGatt = null;
        bluetoothManager = null;
        device = null;
        keepAlive = new KeepAlive();
        timestamp = 0;
        connected = false;
        servicesDiscovered = false;
    }

    /*Defines what to do in various Bluetooth situations
    Passes messages back to the current activity, so that the Bluetooth event can be handled according
    to what is needed in that activity, while leaving handling communications in this service*/
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            setTimestamp();
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("Gatt Callback", "onConnectionStateChange: CONNECTED");
                Message msg = Message.obtain(activityHandler, GATT_CONNECTED);
                msg.sendToTarget(); //Notify the activity a device has connected
                connected = true;
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                Log.d("Gatt Callback", "onConnectionStateChange: DISCONNECTED");
                Message msg = Message.obtain(activityHandler, GATT_DISCONNECTED);
                msg.sendToTarget(); //Notify the activity a device has disconnected
                if (bluetoothGatt != null){
                    Log.d("Gatt Callback", "Closing connection from BluetoothGatt object");
                    bluetoothGatt.close(); //Close the connection and nullify bluetoothGatt so that a new connection can later be started
                    bluetoothGatt = null;
                }
                connected = false;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d("Gatt Callback", "onServicesDiscovered");
            servicesDiscovered = true;

            ArrayList<String> stringGattServices = new ArrayList<String>();
            //Loops through all available services on the device
            if (status == BluetoothGatt.GATT_SUCCESS){
                List<BluetoothGattService> gattServices = gatt.getServices();
                for (BluetoothGattService gattService : gattServices){
                    stringGattServices.add(gattService.getUuid().toString());
                    Log.i("Services Discovered", gattService.getUuid().toString());
                }
            }

            setTimestamp();
            //Sends the list of discovered services back to the activity
            Bundle bundle = new Bundle();
            bundle.putStringArrayList("GATT_SERVICES_LIST", stringGattServices);
            Message msg = Message.obtain(activityHandler, GATT_SERVICES_DISCOVERED);
            msg.setData(bundle);
            msg.sendToTarget();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d("Gatt Callback", "onDescriptorWrite");
            setTimestamp();
            if (status == BluetoothGatt.GATT_SUCCESS){
                //Sends the UUIDs of the service, characteristic and descriptor, and the stored value for the descriptor, to the activity
                Bundle bundle = new Bundle();
                bundle.putString(BUNDLE_DESCRIPTOR_UUID, descriptor.getUuid().toString());
                bundle.putString(BUNDLE_CHARACTERISTIC_UUID, descriptor.getCharacteristic().getUuid().toString());
                bundle.putString(BUNDLE_SERVICE_UUID, descriptor.getCharacteristic().getService().getUuid().toString());
                bundle.putByteArray(BUNDLE_VALUE, descriptor.getValue());
                Message msg = Message.obtain(activityHandler, GATT_DESCRIPTOR_WRITTEN);
                msg.setData(bundle);
                msg.sendToTarget();
            }
            else{
                Log.e("Gatt Callback", "onDescriptorWrite: ERROR. Status: "+status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) { //Data has been received from a characteristic
            Log.d("Gatt Callback", "onCharacteristicChanged");
            setTimestamp();
            //Sends the UUIDs of the service and characteristic, and the received data, to the activity
            Bundle bundle = new Bundle();
            bundle.putString(BUNDLE_CHARACTERISTIC_UUID, characteristic.getUuid().toString());
            bundle.putString(BUNDLE_SERVICE_UUID, characteristic.getService().getUuid().toString());
            bundle.putByteArray(BUNDLE_VALUE, characteristic.getValue());
            Message msg = Message.obtain(activityHandler, NOTIFICATION_INDICATION_RECEIVED);
            msg.setData(bundle);
            msg.sendToTarget();
        }
    };

    public BluetoothDevice getDevice(){
        return device;
    }

    private void setTimestamp(){ //Called whenever BLE activity occurs
        timestamp = System.currentTimeMillis();
    }

    //Keeps the Bluetooth connection alive
    class KeepAlive implements Runnable{
        int sleepTime = 10000; //In milliseconds
        boolean running = false;

        public void start() {
            Thread thread = new Thread(this);
            thread.start(); //Runs in a new thread
        }

        public void stop() {
            running = false;
        }

        @Override
        public void run() {
            Log.d("ConnectionService", "KeepAlive thread starting");
            running = true;
            try {
                Thread.sleep((long) (Math.random() * 1000)); //Sleep for random number of milliseconds
            }
            catch (InterruptedException e){
                e.printStackTrace();
            }
            while (running){
                try{
                    Thread.sleep(sleepTime);
                }
                catch (InterruptedException e){
                    e.printStackTrace();
                }
                //If the time since last BLE activity is more than the specified timeout in milliseconds and the device is still connected, interact with the connection to keep it alive
                if (running && ((System.currentTimeMillis() - timestamp) > sleepTime)){
                    if (connected && servicesDiscovered){ //If device is still connected and its services have been discovered
                        Log.d("ConnectionService", "Keeping connection alive by reading device name");
                        try{
                            bluetoothGatt.readCharacteristic(bluetoothGatt.getService(fromString(GENERICACCESS_SERVICE_UUID)).getCharacteristic(fromString(DEVICENAME_CHARACTERISTIC_UUID)));
                            setTimestamp();
                        }
                        catch (NullPointerException e){
                            Log.e("ConnectionService", "Read Generic Access service and Device Name characteristic failed due to null pointer exception, service may not be enabled on the micro:bit");
                        }
                    }
                    else{
                        Log.d("ConnectionService", "Time Exceeded but not connected or services not yet discovered");
                    }
                }
            }
            Log.d("ConnectionService", "KeepAlive thread exiting");
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        keepAlive.start(); //Start keeping the connection alive once the service has been bound
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent){
        keepAlive.stop();
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        initialiseVariables();
        //Get a new BluetoothManager is there is not one already
        if (bluetoothManager == null){
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null){
                return;
            }
        }
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null){
            return;
        }
    }

    @Override
    public void onDestroy(){ //Runs when the service is stopped, meaning the connection has been terminated
        initialiseVariables();
    }

    public boolean isEnabled(){ //If Bluetooth is enabled and variables are initialised
        if (bluetoothManager==null || bluetoothAdapter==null){
            return false;
        }
        else if (!bluetoothAdapter.isEnabled()) {
            return false;
        }
        else {
            return true;
        }
    }

    public boolean isConnected(){
        return connected;
    }

    public boolean connect(final String address){
        if (bluetoothAdapter == null || address == null){
            Log.e("ConnectionService", "ERROR: null BluetoothAdapter");
            return false;
        }

        device = bluetoothAdapter.getRemoteDevice(address); //Gets the device with the specified MAC address
        if (device == null){
            Log.e("ConnectionService", "ERROR: null BluetoothDevice");
            return false;
        }

        //Starts a connection to the device with the GattCallback defined above
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
        Log.d("ConnectionService", "Successfully connected");
        return true;
    }

    public void disconnect() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.e("ConnectionService", "Tried to disconnect with null BluetoothAdapter or BluetoothGatt");
            return;
        }
        keepAlive.stop(); //Stop keeping the connection alive, as it will be closed
        if (bluetoothGatt != null){
            Log.d("ConnectionService", "DISCONNECTING");
            bluetoothGatt.disconnect();
            connected = false;
        }
    }

    public void discoverServices(){
        bluetoothGatt.discoverServices();
    }

    //Enables notifications for a characteristic
    public boolean setCharacteristicNotification(String service, String characteristic, boolean enabled){
        //Convert Strings to UUIDs
        UUID serviceUUID = fromString(service);
        UUID characteristicUUID = fromString(characteristic);

        //Check there are no null variables
        if (bluetoothGatt == null){
            Log.e("ConnectionService", "Null bluetoothGatt in setCharacteristicNotification");
            return false;
        }
        if (bluetoothGatt.getService(serviceUUID) == null){
            Log.e("ConnectionService", "Null service in setCharacteristicNotification");
            return false;
        }
        if (bluetoothGatt.getService(serviceUUID).getCharacteristic(characteristicUUID) == null) {
            Log.e("ConnectionService", "Null characteristic in setCharacteristicNotification");
            return false;
        }

        //Enable notifications
        bluetoothGatt.setCharacteristicNotification(bluetoothGatt.getService(serviceUUID).getCharacteristic(characteristicUUID), enabled);
        return true;
    }

    //Set value for a descriptor and then write it
    public boolean setDescriptorValueAndWrite (String service, String characteristic, String descriptor, byte[] value){
        //Convert Strings to UUIDs
        UUID serviceUUID = fromString(service);
        UUID characteristicUUID = fromString(characteristic);
        UUID descriptorUUID = fromString(descriptor);

        //Check there are no null variables
        if (bluetoothGatt == null){
            Log.e("ConnectionService", "Null bluetoothGatt in setDescriptorValue");
            return false;
        }
        if (bluetoothGatt.getService(serviceUUID) == null){
            Log.e("ConnectionService", "Null service in setDescriptorValue");
            return false;
        }
        if (bluetoothGatt.getService(serviceUUID).getCharacteristic(characteristicUUID) == null) {
            Log.e("ConnectionService", "Null characteristic in setDescriptorValue");
            return false;
        }
        if (bluetoothGatt.getService(serviceUUID).getCharacteristic(characteristicUUID).getDescriptor(descriptorUUID) == null) {
            Log.e("ConnectionService", "Null descriptor in setDescriptorValue");
            return false;
        }

        BluetoothGattDescriptor valueDescriptor = bluetoothGatt.getService(serviceUUID).getCharacteristic(characteristicUUID).getDescriptor(descriptorUUID);
        valueDescriptor.setValue(value); //Set the value of the descriptor
        boolean result = bluetoothGatt.writeDescriptor(valueDescriptor); //Write the value of the descriptor to the device
        return result;
    }

    //Set value for a characteristic and then write it
    public boolean setCharacteristicValueAndWrite(String service, String characteristic, byte[] value){
        //Convert Strings to UUIDs
        UUID serviceUUID = fromString(service);
        UUID characteristicUUID = fromString(characteristic);

        //Check there are no null variables
        if (bluetoothGatt == null){
            Log.e("ConnectionService", "Null bluetoothGatt in writeCharacteristic");
            return false;
        }
        if (bluetoothGatt.getService(serviceUUID) == null){
            Log.e("ConnectionService", "Null service in writeCharacteristic");
            return false;
        }
        if (bluetoothGatt.getService(serviceUUID).getCharacteristic(characteristicUUID) == null) {
            Log.e("ConnectionService", "Null characteristic in writeCharacteristic");
            return false;
        }

        BluetoothGattCharacteristic valueCharacteristic = bluetoothGatt.getService(serviceUUID).getCharacteristic(characteristicUUID);
        valueCharacteristic.setValue(value); //Set the value of the characteristic
        bluetoothGatt.writeCharacteristic(valueCharacteristic); //Write the characteristic and its value to the device
        return true;
    }

    //Sets the handler that messages will be passed to
    public void setActivityHandler(Handler handler){
        activityHandler = handler;
    }

    public boolean refreshDeviceCache(){
        try{
            /*Android's BluetoothGatt has a method "refresh" that will clear the internal cache and force a refresh of the services from the device
            This is because Android only requests the device once to discover its services, and all subsequent calls to discoverServices simply fetch the cached results from the first call
            However, this method is normally inaccessible, and to call it, reflection - a feature of Java that allows the program to examine itself and manipulate its internal properties - must be used.
            Calling this "refresh" method will force a rediscovery of all BLE services, causing the non-generic services to be found if they weren't previously.
            */
            BluetoothGatt localGatt = bluetoothGatt;
            Method localMethod = localGatt.getClass().getMethod("refresh", new Class[0]); //Gets the "refresh" method
            if (localMethod != null){
                boolean bool = ((Boolean) localMethod.invoke(localGatt, new Object[0])).booleanValue(); //Invokes the method, essentially forcing BLE services to be rediscovered
                return bool;
            }
        }
        catch (Exception localException){
            Log.e("BLE Services", "An exception occurred while refreshing the device");
        }
        return false;
    }
}