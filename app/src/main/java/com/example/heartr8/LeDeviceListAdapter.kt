package com.example.heartr8

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView


class LeDeviceListAdapter(private val context: Context) : BaseAdapter() {
    private val devices = mutableListOf<BluetoothDevice>()

    fun addDevice(device: BluetoothDevice) {
        if (!devices.contains(device)) {
            devices.add(device)
        }
    }

    override fun getCount(): Int {
        return devices.size
    }

    override fun getItem(position: Int): Any {
        return devices[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val viewHolder: ViewHolder

        if (convertView == null) {
            // If convertView is null, inflate a new view
            view = LayoutInflater.from(context).inflate(R.layout.list_item_device, parent, false)
            viewHolder = ViewHolder(view)
            view.tag = viewHolder
        } else {
            // If convertView is not null, reuse it
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        // Set data to the views
        val device = getItem(position) as BluetoothDevice

        return view
    }

    private fun getDeviceName(device: BluetoothDevice): String? {
        return try {
            device.name
        } catch (e: SecurityException) {
            // Handle the case where permission is not granted
            null
        }
    }

    // ViewHolder pattern for better performance
    private class ViewHolder(view: View) {
    }
}