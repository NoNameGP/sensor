package com.example.sensor

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {

    private val REQUEST_ENABLE_BT = 10 // 블루투스 활성화 상태
    private var bluetoothAdapter: BluetoothAdapter? = null // 블루투스 어댑터
    private var devices: Set<BluetoothDevice>? = null // 블루투스 디바이스 데이터 셋
    lateinit var bluetoothDevice: BluetoothDevice // 블루투스 디바이스
    lateinit var bluetoothSocket: BluetoothSocket// 블루투스 소켓
    private var outputStream: OutputStream? = null // 블루투스에 데이터를 출력하기 위한 출력 스트림
    lateinit var inputStream: InputStream// 블루투스에 데이터를 입력하기 위한 입력 스트림
    private var workerThread: Thread? = null // 문자열 수신에 사용되는 쓰레드
    private var readBuffer: ByteArray? = null // 수신된 문자열 저장 버퍼
    private var readBufferPosition = 0 // 버퍼 내 문자 저장 위치
    val array = arrayOf("0") // 수신된 문자열을 쪼개서 저장할 배열


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

// 위치권한 허용 코드
        val permissionList = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        ActivityCompat.requestPermissions(this, permissionList, 1)

        //블루투스 활성화 코드
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() // 블루투스 어댑터를 디폴트 어댑터로 설정

        if (bluetoothAdapter == null) { // 기기가 블루투스를 지원하지 않을 때
            Toast.makeText(applicationContext, "Bluetooth 미지원", Toast.LENGTH_SHORT).show()
            // 처리 코드 작성
        } else { // 기기가 블루투스를 지원할 때
            if (bluetoothAdapter!!.isEnabled) { // 기기의 블루투스 기능이 켜져 있을 경우
                selectBluetoothDevice() // 블루투스 디바이스 선택 함수 호출
            } else { // 기기의 블루투스 기능이 꺼져 있을 경우
                // 블루투스 활성화하기 위한 대화상자 출력
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                // 선택 값이 onActivityResult 함수에서 콜백
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                startActivityForResult(intent, REQUEST_ENABLE_BT)
                selectBluetoothDevice()
            }
        }
    }


        var pairedDeviceCount: Int = 0

        fun selectBluetoothDevice() {
            Log.d("blue", "1")
            //페어링된 기기 탐색
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            {
                Log.d("blue", "2")
            }

            devices = bluetoothAdapter!!.bondedDevices
            pairedDeviceCount = (devices as MutableSet<BluetoothDevice>?)!!.size
            //페어링된 기기가 없는 경우
            if (pairedDeviceCount == 0) {
                Toast.makeText(applicationContext, "페어링 하세요", Toast.LENGTH_SHORT).show()
            }
            //페어링된 기기가 있는 경우
            else {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("페어링 된 블루투스 디바이스 목록")

                val list = ArrayList<String>()

                for (bluetoothDevice in devices as MutableSet<BluetoothDevice>) {
                    list.add(bluetoothDevice.name)
                }
                list.add("취소")

                val charSequences = list.toTypedArray()

                builder.setItems(charSequences, object : DialogInterface.OnClickListener {
                    override fun onClick(p0: DialogInterface?, p1: Int) {
                        connectDevice(charSequences[p1].toString())
                    }

                })
                builder.setCancelable(false)
                val alertDialog = builder.create()
                alertDialog.show()
            }
        }

        fun connectDevice(deviceName: String) {
            Log.d("blue", "3")
            for (tempDevice in devices!!) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d("blue", "4")
                }
                if (deviceName == tempDevice.name) {
                    bluetoothDevice = tempDevice
                    break
                }
            }
            Toast.makeText(applicationContext, "${bluetoothDevice.name} 연결 완료", Toast.LENGTH_SHORT).show()
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            try {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket.connect()

                outputStream = bluetoothSocket.outputStream
                inputStream = bluetoothSocket.inputStream
                receiveData()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun receiveData() {
            val handler = Handler()

            readBufferPosition = 0
            readBuffer = ByteArray(1024)

            workerThread = Thread (object : Runnable {
                override fun run() {
                    while (!Thread.currentThread().isInterrupted) {
                        try {
                            val byteAvailable = inputStream.available()
                            if (byteAvailable > 0) {
                                val bytes = ByteArray(byteAvailable)
                                inputStream.read(bytes)

                                for (i in 0 until byteAvailable) {
                                    val tempByte = bytes[i]

                                    if (tempByte == '\n'.toByte()) {
                                        val encodedBytes = ByteArray(readBufferPosition)
                                        System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.size)
                                        val text = String(encodedBytes, charset("UTF-8"))
                                        readBufferPosition = 0
                                        handler.post(object : Runnable {
                                            override fun run() {
                                                var texttt: TextView = findViewById(R.id.text)
                                                texttt.text = text
                                            }
                                            // 여기에서 text를 활용하여 무엇인가를 수행하십시오.
                                            // 예를 들어, UI 업데이트 또는 다른 작업을 수행할 수 있습니다.
                                        })
                                    } else {
                                        readBuffer!![readBufferPosition++] = tempByte
                                    }
                                }
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                    try {
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }

            })
            workerThread!!.start()

        }
}