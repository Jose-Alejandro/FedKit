package org.eu.fedcampus.fed_kit

import android.util.Log
import com.google.protobuf.ByteString
import flwr.android_client.ClientMessage
import flwr.android_client.FlowerServiceGrpc
import flwr.android_client.Parameters
import flwr.android_client.Scalar
import flwr.android_client.ServerMessage
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.eu.fedcampus.fed_kit_train.FlowerClient
import org.eu.fedcampus.fed_kit_train.db.TFLiteModel
import org.eu.fedcampus.fed_kit_train.helpers.assertIntsEqual
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

/**
 * Start communication with Flower server and training in the background.
 * Note: constructing an instance of this class **immediately** starts training.
 * @param flowerServerChannel Channel already connected to Flower server.
 * @param callback Called with information on training events.
 */
class FlowerServiceRunnable<X : Any, Y : Any> @Throws constructor(
    val flowerServerChannel: ManagedChannel,
    val train: Train<X, Y>,
    val model: TFLiteModel,
    val flowerClient: FlowerClient<X, Y>,
    val callback: (String) -> Unit
) : AutoCloseable {
    private val scope = MainScope()
    val finishLatch = CountDownLatch(1)
    val jobs = mutableListOf<Job>()

    val asyncStub = FlowerServiceGrpc.newStub(flowerServerChannel)!!
    val requestObserver = asyncStub.join(object : StreamObserver<ServerMessage> {
        override fun onNext(msg: ServerMessage) = try {
            handleMessage(msg)
        } catch (err: Throwable) {
            logStacktrace(err)
        }

        override fun onError(err: Throwable) {
            logStacktrace(err)
            close()
        }

        override fun onCompleted() {
            close()
        }
    })!!

    @Throws
    fun handleMessage(message: ServerMessage) {
        val clientMessage = if (message.hasGetParametersIns()) {
            handleGetParamsIns()
        } else if (message.hasFitIns()) {
            handleFitIns(message)
        } else if (message.hasEvaluateIns()) {
            handleEvaluateIns(message)
        }  else if (message.hasReconnectIns()) {
            return requestObserver.onCompleted()
        } else {
            throw Error("Unknown client message $message.")
        }
        requestObserver.onNext(clientMessage)
        callback("Response sent to the server")
    }

    @Throws
    fun handleGetParamsIns(): ClientMessage {
        Log.d(TAG, "Handling GetParameters")
        callback("Handling GetParameters message from the server.")
        return weightsAsProto(weightsByteBuffers())
    }

    @Throws
    fun handleFitIns(message: ServerMessage): ClientMessage {
        val startT = System.currentTimeMillis()
        Log.d(TAG, "Handling FitIns")
        callback("Handling Fit request from the server.")
        val start = if (train.telemetry) System.currentTimeMillis() else null
        val layers = message.fitIns.parameters.tensorsList
        assertIntsEqual(layers.size, model.tflite_layers.size)
        val epochConfig = message.fitIns.configMap.getOrDefault(
            "local_epochs", Scalar.newBuilder().setSint64(1).build()
        )!!
        val epochs = epochConfig.sint64.toInt()
        val newWeights = weightsFromLayers(layers)

//        Measure updateParameters Function
//        val startUpdateParameters = System.currentTimeMillis()
        flowerClient.updateParameters(newWeights.toTypedArray())
//        val endUpdateParameters = System.currentTimeMillis()
//        val upUpdateJob = launchJob {
//            train.upTimesDataTelemetry("flowerClient.updateParameters",
//                    endUpdateParameters - startUpdateParameters)
//        }
//        cleanUpJobs()
//        jobs.add(upUpdateJob)

//        Measure fit function (one epoch training time)
        val i = System.currentTimeMillis()
        flowerClient.fit(epochs, lossCallback = {callback("Average loss: ${it.average()}.") } )

        val j = System.currentTimeMillis()
        Log.d(TAG, "ALEX handleFitIns j -i ${j -i}")
        val upJob = launchJob {
            train.upTimesDataTelemetry("flowerClient.fit", j - i)
        }
        cleanUpJobs()
        jobs.add(upJob)
        callback("ALEX flowerClient.fit fn took [ms] ${j-i}") //This is correct
        if (start != null) {
            val end = System.currentTimeMillis()
            val job = launchJob { train.fitInsTelemetry(start, end) }

            cleanUpJobs()
            jobs.add(job)
        }
        val endT = System.currentTimeMillis()
        callback("ALEX fits_in took [ms] ${endT - startT}")
        val startFitAsProto = System.currentTimeMillis()
        val response = fitResAsProto(weightsByteBuffers(), flowerClient.trainingSamples.size)
        val endFitAsProto = System.currentTimeMillis()
        callback("ALEX fitResAsProto fn took [ms] ${endFitAsProto - startFitAsProto}")
        return response
    }

    @Throws
    fun handleEvaluateIns(message: ServerMessage): ClientMessage {
        val s = System.currentTimeMillis()
        Log.d(TAG, "Handling EvaluateIns")
        callback("Handling Evaluate request from the server")
//        callback("ALEX CPU Usage is ${getCpuProcessUsage()}")
        val start = if (train.telemetry) System.currentTimeMillis() else null
        val layers = message.evaluateIns.parameters.tensorsList
        assertIntsEqual(layers.size, model.tflite_layers.size)
        val newWeights = weightsFromLayers(layers)

        flowerClient.updateParameters(newWeights.toTypedArray())

//        evaluate timer
        val startEvaluate = System.currentTimeMillis()
        val (loss, accuracy) = flowerClient.evaluate()
        val endEvaluate = System.currentTimeMillis()
        val jobEval = launchJob {
            train.upTimesDataTelemetry("flowerClient.evaluate",
                    endEvaluate - startEvaluate)
        }
        cleanUpJobs()
        jobs.add(jobEval)

        callback("Test Accuracy after this round = $accuracy")


        val testSize = flowerClient.testSamples.size

        if (start != null) {
            val end = System.currentTimeMillis()
            val job = launchJob { train.evaluateInsTelemetry(start, end, loss, accuracy, testSize) }
            cleanUpJobs()
            jobs.add(job)
        }
        val end= System.currentTimeMillis()
        callback("ALEX evaluate_ins took [ms] ${end - s}")
        return evaluateResAsProto(loss, testSize)
    }

    private fun weightsByteBuffers() = flowerClient.getParameters()

    private fun weightsFromLayers(layers: List<ByteString>) =
        layers.map { ByteBuffer.wrap(it.toByteArray()) }

    private fun launchJob(call: suspend () -> Unit) = scope.launch {
        try {
            call()
        } catch (err: Throwable) {
            logStacktrace(err)
        }
    }

    private fun cleanUpJobs() {
        jobs.removeAll { it.isCompleted }
    }

    private fun logStacktrace(err: Throwable) {
        Log.e(TAG, err.stackTraceToString())
    }

    override fun close() {
        if (finishLatch.count > 0) {
            flowerServerChannel.shutdown()
            runBlocking { jobs.joinAll() }
            Log.d(TAG, "Exiting.")
            finishLatch.countDown()
        } else {
            Log.w(TAG, "Second Exit.")
        }
    }

    companion object {
        private const val TAG = "Flower Service Runnable"
    }
}

fun weightsAsProto(weights: Array<ByteBuffer>): ClientMessage {
    val layers = weights.map { ByteString.copyFrom(it) }
    val p = Parameters.newBuilder().addAllTensors(layers).setTensorType("ND").build()
    val res = ClientMessage.GetParametersRes.newBuilder().setParameters(p).build()
    return ClientMessage.newBuilder().setGetParametersRes(res).build()
}

fun fitResAsProto(weights: Array<ByteBuffer>, training_size: Int): ClientMessage {
    val layers: MutableList<ByteString> = ArrayList()
    for (weight in weights) {
        layers.add(ByteString.copyFrom(weight))
    }
    val p = Parameters.newBuilder().addAllTensors(layers).setTensorType("ND").build()
    val res =
        ClientMessage.FitRes.newBuilder().setParameters(p).setNumExamples(training_size.toLong())
            .build()
    return ClientMessage.newBuilder().setFitRes(res).build()
}

fun evaluateResAsProto(accuracy: Float, testing_size: Int): ClientMessage {
    val res = ClientMessage.EvaluateRes.newBuilder().setLoss(accuracy)
        .setNumExamples(testing_size.toLong()).build()
    return ClientMessage.newBuilder().setEvaluateRes(res).build()
}

//fun getDataAsProto(total_time: Long/*, testing_size: Int*/): ClientMessage {
//    val res = ClientMessage.GetDataRes.newBuilder().setTotalTime(total_time)
//            //.setNumExamples(testing_size.toLong()).build()
//    return ClientMessage.newBuilder().setGetDataRes(res).build()
//}


/**
 * @param address Address of the gRPC server, like "dns:///$host:$port".
 */
suspend fun createChannel(address: String, useTLS: Boolean = false): ManagedChannel {
    val channelBuilder =
        ManagedChannelBuilder.forTarget(address).maxInboundMessageSize(HUNDRED_MEBIBYTE)
    if (!useTLS) {
        channelBuilder.usePlaintext()
    }
    return withContext(Dispatchers.IO) {
        channelBuilder.build()
    }
}

const val HUNDRED_MEBIBYTE = 100 * 1024 * 1024
