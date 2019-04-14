package com.mosscurve.rowing



import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message

import android.util.Log

import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.Toast
import com.mosscurve.rowing.Calculation.calculation
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

// https://developer.android.com/guide/topics/connectivity/bluetooth

val TAG = "tag_main"

// Defines several constants used when transmitting messages between the
// service and the UI.
private val MESSAGE_READ: Int = 0
private val MESSAGE_WRITE: Int = 1
private val MESSAGE_TOAST: Int = 2
// ... (Add other message types here as needed.)

class MainActivity : AppCompatActivity() {
    // Get the default adapter
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    lateinit var handler_sensor_data: Handler

    private val REQUEST_ENABLE_BT = 1 // must be greater than 0
    private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") //For BT module


    private lateinit var dataTransferThread: DataTransferThread
    private lateinit var connectThread: ConnectThread


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        defineHandler()
        checkBTState()
        registerBroadcastReceiver()
        setBtnListener()
    }


    private fun checkBTState() {
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Toast.makeText(this, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show()

        } else {
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                // Enabling discoverability automatically enables Bluetooth.  If you plan to consistently enable device discoverability before performing Bluetooth activity, you can skip step 2 above.

            } else {
                getPairedDevices()
            }
        }
    }


    private fun registerBroadcastReceiver() {
        // Register for broadcasts when a device is discovered.
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(receiver, filter)
    }


    private fun defineHandler() {
        handler_sensor_data = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message?) {
                super.handleMessage(msg)
                when (msg?.what) {
                    MESSAGE_READ -> {
                        //contentToString()) -> [97]
                        //toString()) -> [B@3b975ac
                        val which_sensor = (msg.obj as ByteArray).toString(Charsets.UTF_8) //-> a
                        calculation(which_sensor)
                    }
                    MESSAGE_WRITE -> Log.d(TAG, "WRITE: " + msg.toString())
                    MESSAGE_TOAST -> Log.d(TAG, "TOAST: " + msg.toString())
                }
            }


        }
    }





    private fun setBtnListener() {
        btn_disconnect.setOnClickListener {
            Log.d(TAG, "btn_disconnect")
            if (::dataTransferThread.isInitialized) {
                dataTransferThread.closeSocket()
            }
        }

        btn_go_ready.setOnClickListener {
            startActivity(Intent(this, ModeActivity::class.java))
        }

        btn_start_discovery.setOnClickListener {
            val is_successfully_started = bluetoothAdapter?.startDiscovery()
//            Log.d(TAG, "is_successfully_started: $is_successfully_started")
        }

    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth Enabled", Toast.LENGTH_SHORT).show()
                getPairedDevices()
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Bluetooth NOT Enabled", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun getPairedDevices() {
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            addDeviceView(device, "paired")
        }
    }


    // Create a BroadcastReceiver for ACTION_FOUND.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            when(action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> { progress_discovery.visibility = VISIBLE }

                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = device.name
                    val deviceHardwareAddress = device.address // MAC address
                    Log.d(TAG, "found_device_name: $deviceName, found_device_mac: $deviceHardwareAddress")

                    addDeviceView(device, "discovered")
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> { progress_discovery.visibility = INVISIBLE }
            }
        }
    }


    private fun addDeviceView(device: BluetoothDevice, which: String) {
        val button = Button(this)
        button.text = "${device.name}\n${device.address}"

        button.setOnClickListener {
            //start()를 호출해야 Thread가 작동한다. run()은 그냥 메서드를 호출 하는 것이다.
            //handler도 Thread여야 작동한다.
            if (::connectThread.isInitialized) {
                if (connectThread.mmSocket!!.isConnected) {
                    Log.d(TAG, "Socket is already connected.")
                } else {
                    connectThread = ConnectThread(device)
                    connectThread.start()
                    progress_connect.visibility = VISIBLE

                    this.startActivity(Intent(this, ModeActivity::class.java))
                }

            } else {
                connectThread = ConnectThread(device)
                connectThread.start()
                progress_connect.visibility = VISIBLE

                startActivity(Intent(this, ModeActivity::class.java))
            }
        }

        when (which) {
            "paired" -> layout_paired_container.addView(button)
            "discovered" -> layout_discovered_container.addView(button)
        }
    }

    // 스레드 안에서 progress_connect.visibility = VISIBLE //에러난다.
    private inner class ConnectThread(val device: BluetoothDevice) : Thread() {
        val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(MY_UUID)
        }

        override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter?.cancelDiscovery()

            //use는 실행이 끝난후 바로 리소스가 제거된다. 그래서 소켓이 클로즈된다. 구글 가이드에는 왜 이걸 쓰라고 되어있지.
//            mmSocket?.use { socket ->
                try {
                    // Connect to the remote device through the socket. This call blocks
                    // until it succeeds or throws an exception.
                    mmSocket!!.connect()

                    // The connection attempt succeeded. Perform work associated with
                    // the connection in a separate thread.
                    manageMyConnectedSocket(mmSocket!!)
                } catch (e: IOException) {
                    e.printStackTrace()

                }
//            }
        }

        // Closes the client socket and causes the thread to finish.
        fun closeSocket() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }

        private fun manageMyConnectedSocket(socket: BluetoothSocket) {
            progress_connect.visibility = INVISIBLE
            dataTransferThread = DataTransferThread(socket, handler_sensor_data)
            dataTransferThread.start()
        }
    }



    private inner class DataTransferThread(private val mmSocket: BluetoothSocket, private val handler: Handler) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1) // mmBuffer store for the stream

        override fun run() {
            var numBytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                // Read from the InputStream.
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }

                // Send the obtained bytes to the UI activity.
                val readMsg = handler.obtainMessage(
                    MESSAGE_READ, numBytes, -1,
                    mmBuffer)
                readMsg.sendToTarget()
            }
        }

        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)

                // Send a failure message back to the activity.
                val writeErrorMsg = handler.obtainMessage(MESSAGE_TOAST)
                val bundle = Bundle().apply {
                    putString("toast", "Couldn't send data to the other device")
                }
                writeErrorMsg.data = bundle
                handler.sendMessage(writeErrorMsg)
                return
            }

            // Share the sent message with the UI activity.
            val writtenMsg = handler.obtainMessage(
                MESSAGE_WRITE, -1, -1, mmBuffer)
            writtenMsg.sendToTarget()
        }

        // Call this method from the main activity to shut down the connection.
        fun closeSocket() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }




    override fun onDestroy() {
        super.onDestroy()
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver)

        if (::dataTransferThread.isInitialized) {
            dataTransferThread.closeSocket()
        }
    }


}







