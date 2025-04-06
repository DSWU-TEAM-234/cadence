package kr.ac.duksung.sensor

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var saveButton: Button
    private lateinit var messageTextView: TextView
    private lateinit var cadenceEditText: EditText

    private var isMeasuring = false
    private val sensorDataList = mutableListOf<String>()

    private val handler = Handler(Looper.getMainLooper())
    private val samplingInterval = 20

    private val sensorRunnable = object : Runnable {
        override fun run() {
            if (isMeasuring) {
                if (!isSensorListenerRegistered) {
                    registerSensorListeners()
                }
                handler.postDelayed(this, samplingInterval.toLong())
            }
        }
    }

    private var isSensorListenerRegistered = false

    private fun registerSensorListeners() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        isSensorListenerRegistered = true
    }

    private fun unregisterSensorListeners() {
        sensorManager.unregisterListener(this)
        isSensorListenerRegistered = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        saveButton = findViewById(R.id.saveButton)
        messageTextView = findViewById(R.id.messageTextView)
        cadenceEditText = findViewById(R.id.cadenceEditText)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        startButton.setOnClickListener { startMeasurementWithDelay() } // 수정: startMeasurementWithDelay 호출
        stopButton.setOnClickListener { stopMeasurement() }
        saveButton.setOnClickListener { saveMeasurement() }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
            1
        )
    }

    // 5초 딜레이를 주는 함수
    private fun startMeasurementWithDelay() {
        showMessage("5초 후에 측정을 시작합니다...")
        handler.postDelayed({
            startMeasurement()
        }, 5000) // 5000 milliseconds = 5 seconds
    }

    private fun startMeasurement() {
        if (accelerometer != null && gyroscope != null) {
            sensorDataList.clear()
            isMeasuring = true
            registerSensorListeners()
            handler.post(sensorRunnable)
            showMessage("측정 시작")
        } else {
            showMessage("센서를 사용할 수 없습니다.")
        }
    }

    private fun stopMeasurement() {
        if (isMeasuring) {
            isMeasuring = false
            unregisterSensorListeners()
            handler.removeCallbacks(sensorRunnable)
            showMessage("측정 종료")
        }
    }

    private fun saveMeasurement() {
        if (sensorDataList.isEmpty()) {
            showMessage("저장할 데이터가 없습니다.")
            return
        }

        val cadence = cadenceEditText.text.toString()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "sensor_data_${cadence}_$timeStamp.csv" // 케이던스를 파일명에 추가

        saveSensorDataToFile(sensorDataList, fileName)
    }

    private fun saveSensorDataToFile(dataList: MutableList<String>, fileName: String) {
        if (dataList.isEmpty()) {
            Log.d("MainActivity", "데이터가 없습니다.")
            return
        }

        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        try {
            val writer = FileWriter(file)
            writer.write("Timestamp,Accelerometer X,Accelerometer Y,Accelerometer Z,Gyroscope X,Gyroscope Y,Gyroscope Z,Cadence\n")
            for (data in dataList) {
                writer.write(data + "\n")
            }
            writer.close()
            showMessage("파일 저장 성공: ${file.absolutePath}")

            // 파일 저장 후 다운로드 시작
            downloadFile(this, fileName)

        } catch (e: IOException) {
            showMessage("파일 저장 실패: ${e.message}")
        }
    }

    // **다운로드 함수 추가**
    private fun downloadFile(context: Context, fileName: String) {
        val sourceFile =
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        val destinationDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val destinationFile = File(destinationDir, fileName)

        try {
            FileInputStream(sourceFile).use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } > 0) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }

            showMessage("파일이 다운로드 폴더에 저장되었습니다.")

            // 다운로드된 파일을 바로 열도록 유도 (선택 사항)
            openDownloadedFile(context, destinationFile)

        } catch (e: IOException) {
            showMessage("파일 다운로드에 실패했습니다: ${e.message}")
            e.printStackTrace()
        }
    }

    // **파일 열기 함수 추가**
    private fun openDownloadedFile(context: Context, file: File) {
        // fileprovider name 수정
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "text/csv")
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            showMessage("파일을 열 수 있는 앱이 없습니다.")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val timestamp = System.currentTimeMillis()
        var accelerometerX = ""
        var accelerometerY = ""
        var accelerometerZ = ""
        var gyroscopeX = ""
        var gyroscopeY = ""
        var gyroscopeZ = ""

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelerometerX = event.values[0].toString()
                accelerometerY = event.values[1].toString()
                accelerometerZ = event.values[2].toString()
            }

            Sensor.TYPE_GYROSCOPE -> {
                gyroscopeX = event.values[0].toString()
                gyroscopeY = event.values[1].toString()
                gyroscopeZ = event.values[2].toString()
            }
        }

        val cadence = cadenceEditText.text.toString()

        val data =
            "$timestamp,$accelerometerX,$accelerometerY,$accelerometerZ,$gyroscopeX,$gyroscopeY,$gyroscopeZ,$cadence"
        sensorDataList.add(data)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 센서 정확도 변경 시 처리
    }

    private fun showMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            messageTextView.text = message
        }
    }

    override fun onPause() {
        super.onPause()
        stopMeasurement() // onPause 시 측정 중지
    }
}
