package org.eu.fedcampus.android_client

import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.eu.fedcampus.android_client.databinding.ActivityMainBinding
import org.eu.fedcampus.fed_kit.Train
import org.eu.fedcampus.fed_kit_examples.cifar10.DATA_TYPE
import org.eu.fedcampus.fed_kit_examples.cifar10.Float3DArray
import org.eu.fedcampus.fed_kit_examples.cifar10.loadData
import org.eu.fedcampus.fed_kit_examples.cifar10.sampleSpec
import org.eu.fedcampus.fed_kit_train.FlowerClient
import org.eu.fedcampus.fed_kit_train.helpers.deviceId
import org.eu.fedcampus.fed_kit_train.helpers.loadMappedFile
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val scope = MainScope()
    lateinit var train: Train<Float3DArray, FloatArray>
    lateinit var flowerClient: FlowerClient<Float3DArray, FloatArray>
    private lateinit var binding: ActivityMainBinding
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.connectButton.setOnClickListener { connect() }
        binding.trainButton.setOnClickListener { startTrain() }
    }

    fun appendLog(text: String) {
        val time = dateFormat.format(Date())
        runOnUiThread {
            binding.logsTextView.append("\n$time   $text")
        }
    }

    fun connect() {
        val startTimerTask = System.currentTimeMillis()
        val clientPartitionIdText = binding.clientPartitionIdEditText.text.toString()
        val flServerIpText = binding.flServerIpEditText.text.toString()
        val flServerPortText = binding.flServerPortEditText.text.toString()

        // Validate client partition id
        val partitionId: Int
        try {
            partitionId = clientPartitionIdText.toInt()
        } catch (e: NumberFormatException) {
            appendLog("Invalid client partition id!")
            return
        }

        // Validate backend server host
        val host: Uri
        try {
            host = Uri.parse("http://$flServerIpText")
            if (!host.path.isNullOrEmpty() || host.host.isNullOrEmpty()) {
                throw Exception()
            }
        } catch (e: Exception) {
            appendLog("Invalid backend server host!")
            return
        }

        // Validate backend server port
        val backendPort: Int
        val backendUrl: Uri
        try {
            backendPort = flServerPortText.toInt()
            backendUrl = Uri.parse("http://$flServerIpText:$backendPort")

        } catch (e: NumberFormatException) {
            appendLog("Invalid backend server port!")
            return
        }

        appendLog("Connecting with Partition ID: $partitionId, Server IP: $host, Port: $backendPort")

        scope.launch {
            try {
                connectInBackground(partitionId, backendUrl, host)
            } catch (err: Throwable) {
                appendLog("$err")
                Log.e(TAG, err.stackTraceToString())
                runOnUiThread { binding.connectButton.isEnabled = true }
            }
        }
        binding.connectButton.isEnabled = false
        appendLog("Creating channel object.")
        val endTimerTask = System.currentTimeMillis()
        val totalTime = endTimerTask - startTimerTask
//        appendLog("ALEX Connect in time [ms] is: $totalTime" )
    }

    fun startTrain() {
        scope.launch {
            try {
                val start = System.currentTimeMillis()

                trainInBackground()
                val end = System.currentTimeMillis()
                train.upTimesDataTelemetry("trainInBackground", end - start)
            } catch (err: Throwable) {
                appendLog("$err")
                Log.e(TAG, err.stackTraceToString())
                binding.trainButton.isEnabled = true
            }
        }
    }

    @Throws
    suspend fun connectInBackground(participationId: Int, backendUrl: Uri, host: Uri) {
        val startT = System.currentTimeMillis()
        Log.i(TAG, "Backend URL: $backendUrl")
        train = Train(this, backendUrl.toString(), sampleSpec())
        train.enableTelemetry(deviceId(this))  // Here we have device ID
        appendLog("The device id is: ${deviceId(this)}")

        val prepareModelStart = System.currentTimeMillis()
          val modelFile = train.prepareModel(DATA_TYPE) // We need this to enable flower server
        val prepareModelEnd = System.currentTimeMillis()

        val getServerStart = System.currentTimeMillis()
          val serverData = train.getServerInfo(binding.startFreshCheckBox.isChecked)
//        After this function, we have sessionId
        val getServerEnd = System.currentTimeMillis()

//      Upload prepareModel Timer
        train.upTimesDataTelemetry("prepareModel",prepareModelEnd - prepareModelStart)
//      Upload getServer Timer
        train.upTimesDataTelemetry("getServerInfo", getServerEnd - getServerStart)

        if (serverData.port == null) {
            throw Error("Flower server port not available, status ${serverData.status}")
        }

        val prepareStart = System.currentTimeMillis()
          flowerClient = train.prepare(
            loadMappedFile(modelFile), "dns:///${host.host}:${serverData.port}", false
          )
        val prepareEnd = System.currentTimeMillis()
        train.upTimesDataTelemetry("prepare",prepareEnd - prepareStart )


        val loadDataStart = System.currentTimeMillis()
        loadData(this, flowerClient, participationId)
        val loadDataEnd = System.currentTimeMillis()
        train.upTimesDataTelemetry("loadData", loadDataEnd - loadDataStart)

        appendLog("Connected to Flower server on port ${serverData.port} and loaded data set.")
        runOnUiThread {
            binding.trainButton.isEnabled = true
        }
        val endT = System.currentTimeMillis()
        train.upTimesDataTelemetry("connectInBackground", endT - startT)
        appendLog("ALEX Connect in background time is [ms]: ${endT - startT}")
    }

    suspend fun trainInBackground() {
        val startT = System.currentTimeMillis()

//        This effectively starts training in background (instantiating FlowerServiceRunnable)
        train.start {
            runOnUiThread { appendLog(it) }
        }
        val endT1 = System.currentTimeMillis()

        train.upTimesDataTelemetry("train.start", endT1 - startT)
        appendLog("Started training.")
        runOnUiThread {
            binding.trainButton.isEnabled = false
        }
        val endT = System.currentTimeMillis()
        appendLog("ALEX TrainInBackground time is [ms]: ${endT - startT}")
    }
}

private const val TAG = "MainActivity"
