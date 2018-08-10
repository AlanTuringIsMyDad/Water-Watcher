/*
//To keep track of the code to add to MainActivity.java, following https://medium.com/@avigezerit/bluetooth-low-energy-on-android-22bc7310387a
HEART_RATE_SERVICE_UUID -> ACCELEROMETERSERVICE_SERVICE_UUID
HEART_RATE_MEASUREMENT_CHAR_UUID -> ACCELEROMETERDATA_CHARACTERISTIC_UUID
HEART_RATE_CONTROL_POINT_CHAR_UUID -> ??
CLIENT_CHARACTERISTIC_CONFIG_UUID -> CLIENT_CHARACTERISTIC_CONFIGURATION_UUID
*/


//Bluetooth constants
//Should be in the form "0000AAAA-0000-1000-8000-00805f9b34fb" where "AAAA" is to be replaced
public static String BLE_SIGNATURE_UUID_BASE_START = "0000";
public static String BLE_SIGNATURE_UUID_BASE_END = "-0000-1000-8000-00805F9B34FB";

public static String ACCELEROMETERSERVICE_SERVICE_UUID = "E95D0753251D470AA062FA1922DFA9A8";
public static String ACCELEROMETERDATA_CHARACTERISTIC_UUID = "E95DCA4B251D470AA062FA1922DFA9A8";
public static String ACCELEROMETERPERIOD_CHARACTERISTIC_UUID = "E95DFB24251D470AA062FA1922DFA9A8";
public static String CLIENT_CHARACTERISTIC_CONFIGURATION_UUID = "2902";
 
public static String TARGET_ADDRESS = "C7:D7:2F:2F:2D:8E"; //MAC address of the micro:bit
private final static int REQUEST_ENABLE_BT = 1;

BluetoothAdapter bluetoothAdapter;
BluetoothDevice targetDevice;
BluetoothGatt gatt;



//TODO: combine both formatUUID functions
public static UUID formatUUIDShort(String uuid){
    String formattedUUID = uuid;
    formattedUUID = BLE_SIGNATURE_UUID_BASE_START+uuid+BLE_SIGNATURE_UUID_BASE_END;
    return UUID.fromString(formattedUUID);
}

//Used to insert "-"s into the UUID
public static UUID formatUUID(String uuid) {
    String formattedUUID = uuid;
    formattedUUID = uuid.substring(0,8)+"-"+uuid.substring(8,12)+"-"+uuid.substring(12,16)+"-"+uuid.substring(16,20)+"-"+uuid.substring(20,32);
    return UUID.fromString(formattedUUID);
}

private void processData(byte[] value) {
    Log.v("Recieved data:", String.valueOf(value));
}



@RequiresApi(api = Build.VERSION_CODES.KITKAT)
@Override
protected void onCreate(Bundle savedInstanceState) {    
    final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    bluetoothAdapter = bluetoothManager.getAdapter();

    //Ensures bluetooth is enabled
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
        Intent enableBtIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            if(bluetoothDevice.getAddress() == TARGET_ADDRESS){
                targetDevice = bluetoothDevice;
            }
        }
    };

    bluetoothAdapter.startLeScan(scanCallback);

    //TODO: find UUID parallels (which one corresponds to which?) particularly the CLIENT_CONFIG UUID
    final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == STATE_CONNECTED){
                gatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status){
            boolean enabled = true;
            BluetoothGattCharacteristic characteristic = gatt.getService(formatUUID(ACCELEROMETERSERVICE_SERVICE_UUID)).getCharacteristic(formatUUID(ACCELEROMETERDATA_CHARACTERISTIC_UUID));
            gatt.setCharacteristicNotification(characteristic, enabled);

            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(formatUUIDShort(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID));

            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status){
            BluetoothGattCharacteristic characteristic = gatt.getService(formatUUID(ACCELEROMETERSERVICE_SERVICE_UUID)).getCharacteristic(formatUUID(ACCELEROMETERDATA_CHARACTERISTIC_UUID));
            characteristic.setValue(new byte[]{1, 1});
            gatt.writeCharacteristic(characteristic);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            processData(characteristic.getValue());
        }
    };



    //...other fragment/tabs code



    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
    fab.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (targetDevice == null){
                Snackbar.make(view, "No corresponding micro:bit found", Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
            else{
                Snackbar.make(view, "micro:bit found", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                gatt = targetDevice.connectGatt(MainActivity.this, true, gattCallback); //TODO: "this" may be incorrect, possible that gattCallback should not be final
            }
        }
    });
}