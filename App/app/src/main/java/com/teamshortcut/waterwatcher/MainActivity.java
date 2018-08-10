package com.teamshortcut.waterwatcher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;

import java.util.UUID;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MainActivity extends AppCompatActivity {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    //Bluetooth constants
    public static String BLE_SIGNATURE_UUID_BASE_START = "0000";
    public static String BLE_SIGNATURE_UUID_BASE_END = "-0000-1000-8000-00805f9b34fb";
    //Should be in the form "0000AAAA-0000-1000-8000-00805f9b34fb" where "AAAA" is to be replaced
    public static String ACCELEROMETERSERVICE_SERVICE_UUID = "E95D0753251D470AA062FA1922DFA9A8";
    public static String ACCELEROMETERDATA_CHARACTERISTIC_UUID = "E95DCA4B251D470AA062FA1922DFA9A8";
    public static String ACCELEROMETERPERIOD_CHARACTERISTIC_UUID = "E95DFB24251D470AA062FA1922DFA9A8";
    public static String CLIENT_CHARACTERISTIC_CONFIGURATION_UUID = "2902";

    //TODO: https://stackoverflow.com/questions/36180407/why-the-address-of-my-bluetoothdevice-changes-every-time-i-relaunch-the-app
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
        formattedUUID = uuid.substring(0,8) + "-"
                + uuid.substring(8,12) + "-"
                + uuid.substring(12,16) + "-"
                + uuid.substring(16,20) + "-"
                + uuid.substring(20,32);
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
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                BluetoothGattCharacteristic characteristic = gatt.getService(formatUUID(ACCELEROMETERSERVICE_SERVICE_UUID)).getCharacteristic(formatUUID(ACCELEROMETERDATA_CHARACTERISTIC_UUID));
                characteristic.setValue(new byte[]{1, 1});
                gatt.writeCharacteristic(characteristic);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                processData(characteristic.getValue());
            }
        };

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            TextView textView = (TextView) rootView.findViewById(R.id.section_label);
            textView.setText(getString(R.string.section_format, getArguments().getInt(ARG_SECTION_NUMBER)));
            return rootView;
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 3;
        }
    }
}