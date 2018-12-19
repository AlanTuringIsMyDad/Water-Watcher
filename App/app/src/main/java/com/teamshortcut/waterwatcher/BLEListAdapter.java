package com.teamshortcut.waterwatcher;

import android.bluetooth.BluetoothDevice;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;

//Used for displaying a list of available devices in a BLE scan
public class BLEListAdapter extends BaseAdapter {
    private ArrayList<BluetoothDevice> BLEDevices; //stores the list of BluetoothDevices

    public BLEListAdapter(){
        super();
        BLEDevices = new ArrayList<BluetoothDevice>();
    }

    public void addDevice(BluetoothDevice bluetoothDevice){
        if (!BLEDevices.contains(bluetoothDevice)) //prevents duplicate entries
            BLEDevices.add(bluetoothDevice);
    }

    public boolean contains(BluetoothDevice bluetoothDevice){
        return BLEDevices.contains(bluetoothDevice);
    }

    public BluetoothDevice getDevice(int position){
        return BLEDevices.get(position);
    }

    public void clear(){
        BLEDevices.clear();
    }

    @Override
    public int getCount() {
        return BLEDevices.size();
    }

    @Override
    public Object getItem(int position) {
        return BLEDevices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return null;
    }
}
