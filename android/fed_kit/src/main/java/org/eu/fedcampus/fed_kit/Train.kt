package org.eu.fedcampus.fed_kit

import android.content.Context
import android.util.Log
import io.grpc.ManagedChannel
import kotlinx.coroutines.*
import org.eu.fedcampus.fed_kit_train.FlowerClient
import org.eu.fedcampus.fed_kit_train.SampleSpec
import org.eu.fedcampus.fed_kit_train.db.TFLiteModel
import retrofit2.http.*
import java.io.File
import java.nio.MappedByteBuffer
import kotlin.properties.Delegates

class Train<X : Any, Y : Any> constructor(
    val context: Context,
    backendUrl: String,
    val sampleSpec: SampleSpec<X, Y>,
)
{
    var sessionId: Int? = null
    var telemetry = false
        private set
    var deviceId by Delegates.notNull<Long>()
        private set
    val client = HttpClient(backendUrl)
    var state: TrainState<X, Y> = TrainState.Initialized()

    val jobs = mutableListOf<Job>()
    private val scope = MainScope()
    private fun cleanUpJobs() {
        jobs.removeAll { it.isCompleted }
    }
    private fun launchJob(call: suspend () -> Unit) = scope.launch {
        try {
            call()
        } catch (err: Throwable) {
            logStacktrace(err)
        }
    }
    val TAG = "Flower Train"
    private fun logStacktrace(err: Throwable) {
        Log.e(TAG, err.stackTraceToString())
    }

    fun enableTelemetry(id: Long) {
        deviceId = id
        telemetry = true
    }

    @Suppress("unused")
    fun disableTelemetry() {
        deviceId = 0
        telemetry = false
    }

    /**
     * Download advertised model information.
     */
    @Throws
    suspend fun advertisedModel(dataType: String): TFLiteModel = when (state) {
        is TrainState.Initialized, is TrainState.WithModel -> doAdvertisedModel(dataType)
        else -> throw IllegalStateException("`advertisedModel` called with $state")
    }

    private suspend fun doAdvertisedModel(dataType: String): TFLiteModel {
        val startT = System.currentTimeMillis()
        val model = client.advertisedModel(PostAdvertisedData(dataType))
        Log.d(TAG, "Model: $model")
        state = TrainState.WithModel(model)
        val endT = System.currentTimeMillis()
        return model
    }

    @Suppress("UNUSED_PARAMETER")
    @Throws
    fun modelDownloaded(model: TFLiteModel): Boolean {
        // TODO: Save model to DB.
        return false
    }

    /**
     * Download TFLite files to `"models/$path"` if they have not been saved to DB.
     */
    @Throws
    suspend fun downloadModelFile(modelDir: File): File = state.let {
        when (it) {
            is TrainState.WithModel -> doDownloadModelFile(modelDir, it.model)
            else -> throw IllegalStateException("`downloadModelFile` called with $state")
        }
    }

    private suspend fun doDownloadModelFile(modelDir: File, model: TFLiteModel): File {
//        val startT = System.currentTimeMillis()
        val fileUrl = model.tflite_path
        val fileName = fileUrl.split("/").last()
        if (modelDownloaded(model)) {
            // The model is already in the DB
            Log.i(downloadModelFileTag, "skipping already downloaded model ${model.name}")
            return File(modelDir, fileName)
        }
        val fileDir = client.downloadFile(fileUrl, modelDir, fileName)
        Log.i(downloadModelFileTag, "$fileUrl -> ${fileDir.absolutePath}")
//        val endT = System.currentTimeMillis()
//        upTimesDataTelemetry("ALEX doDownloadModelFile", startT - endT)
        return fileDir
    }

    @Throws
    suspend fun getServerInfo(start_fresh: Boolean = false): ServerData = state.let {
        when (it) {
            is TrainState.WithModel -> doGetServerInfo(it.model, start_fresh)
            else -> throw IllegalStateException("`getServerInfo` called with $state")
        }
    }

    private suspend fun doGetServerInfo(model: TFLiteModel, start_fresh: Boolean): ServerData {
        val serverData = client.postServer(model, start_fresh)
        sessionId = serverData.session_id
        Log.i(TAG, "Server data: $serverData")
        return serverData
    }

    /**
     * Ask backend for advertised model, load model into [state], and download its corresponding file.
     * @return Model file.
     */
    @Throws
    suspend fun prepareModel(dataType: String): File = when (state) {
        is TrainState.Initialized, is TrainState.WithModel -> doPrepareModel(dataType)
        else -> throw IllegalStateException("`prepareModel` called with $state")
    }

    private suspend fun doPrepareModel(dataType: String) =
        withContext(Dispatchers.IO) {
            val model = advertisedModel(dataType)
            val modelDir = model.getModelDir(context)
            downloadModelFile(modelDir)
        }

    /**
     * Initialize Flower Client with TFLite model [buffer] and establish channel connection to Flower server.
     */
    @Throws
    suspend fun prepare(buffer: MappedByteBuffer, address: String, useTLS: Boolean) = state.let {
        when (it) {
            is TrainState.WithModel -> doPrepare(buffer, address, useTLS, it.model)
            else -> throw IllegalStateException("`prepare` called with $state")
        }
    }

    private suspend fun doPrepare(
        buffer: MappedByteBuffer,
        address: String,
        useTLS: Boolean,
        model: TFLiteModel
    ): FlowerClient<X, Y> {
        val startT = System.currentTimeMillis()
        val flowerClient = FlowerClient(buffer, model.tflite_layers, sampleSpec)
        val channel = withContext(Dispatchers.IO) {
            createChannel(address, useTLS)
        }
        state = TrainState.Prepared(model, flowerClient, channel)
        val endT = System.currentTimeMillis()
        return flowerClient
    }

    /**
     * Only call this after loading training data into the Flower Client.
     */
    @Throws
    fun start(callback: (String) -> Unit) = state.let {
        when (it) {
            is TrainState.Prepared -> doStart(callback, it.model, it.flowerClient, it.channel)
            else -> throw IllegalStateException("`start` called with $state")
        }
    }

    @Throws
    private fun doStart(
        callback: (String) -> Unit,
        model: TFLiteModel,
        flowerClient: FlowerClient<X, Y>,
        channel: ManagedChannel
    ) =
        if (flowerClient.trainingSamples.isEmpty() || flowerClient.testSamples.isEmpty()) {
            throw Error("No data loaded for training")
        } else {
//            val startFlowerService = System.currentTimeMillis()
            FlowerServiceRunnable(
                channel,
                this,
                model,
                flowerClient,
                callback
            ).let {
                state = TrainState.Training(model, flowerClient, it)
                it
            }
//            val endFlowerService = System.currentTimeMillis()
//            val upJob = launchJob {
//                upTimesDataTelemetry(
//                    "FlowerServiceRunnable",
//                    endFlowerService - startFlowerService)
//            }
//            cleanUpJobs()
//            jobs.add(upJob)
        }

    /**
     * Ensure that telemetry is enabled and [sessionId] is non-null.
     */
    @Throws(AssertionError::class)
    fun checkTelemetryEnabled() {
        assert(telemetry)
        assert(sessionId !== null)
    }

    @Throws
    suspend fun fitInsTelemetry(start: Long, end: Long) {
        checkTelemetryEnabled()
        val body = FitInsTelemetryData(deviceId, sessionId!!, start, end)
        client.fitInsTelemetry(body)
        Log.i(TAG, "Telemetry: Sent fit instruction telemetry")
    }

    @Throws
    suspend fun evaluateInsTelemetry(
        start: Long,
        end: Long,
        loss: Float,
        accuracy: Float,
        test_size: Int
    ) {
        checkTelemetryEnabled()
        val body =
            EvaluateInsTelemetryData(deviceId, sessionId!!, start, end, loss, accuracy, test_size)
        client.evaluateInsTelemetry(body)
        Log.i(TAG, "Telemetry: Sent evaluate instruction telemetry")
    }

     @Throws
     suspend fun upTimesDataTelemetry(
         function_name: String,
         elapsed_time: Long
     ) {
         checkTelemetryEnabled()
         val body = UpTimesTelemetryData(deviceId, sessionId!!, function_name, elapsed_time)
         client.upTimesDataTelemetry(body)
         Log.i(TAG, "Telemetry: Elapsed time data sent to server")
     }

    companion object {
        const val TAG = "Train"
        const val downloadModelFileTag = "Download TFLite model"
    }
}
