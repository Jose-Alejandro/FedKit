package org.eu.fedcampus.fed_kit_train.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

@Throws(IOException::class)
fun loadMappedFile(file: File): MappedByteBuffer {
    Log.i("Loading mapped file", "$file")
    val accessFile = RandomAccessFile(file, "r")
    val channel = accessFile.channel
    return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
}

@Throws(IOException::class)
fun loadMappedAssetFile(context: Context, filePath: String): MappedByteBuffer {
    val fileDescriptor = context.assets.openFd(filePath)
    val fileChannel = fileDescriptor.createInputStream().channel
    val startOffset = fileDescriptor.startOffset
    val declaredLength = fileDescriptor.declaredLength
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
}

infix fun <T, R> Iterable<T>.lazyZip(other: Array<out R>): Sequence<Pair<T, R>> {
    val ours = iterator()
    val theirs = other.iterator()

    return sequence {
        while (ours.hasNext() && theirs.hasNext()) {
            yield(ours.next() to theirs.next())
        }
    }
}

fun <T, R> Iterable<T>.lazyMap(transform: (T) -> R) = sequence {
    iterator().forEach { yield(transform(it)) }
}

fun FloatArray.argmax(): Int = indices.maxBy { this[it] }

fun List<Double>.toFloatArray(): FloatArray = map { it.toFloat() }.toFloatArray()

fun stringToLong(string: String): Long {
    val hashCode = string.hashCode().toLong()
    val secondHashCode = string.reversed().hashCode().toLong()
    return (hashCode shl 32) or secondHashCode
}

@SuppressLint("HardwareIds")
fun deviceId(context: Context): Long {
    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    return stringToLong(androidId)
}

@Throws(AssertionError::class)
fun assertIntsEqual(expected: Int, actual: Int) {
    if (expected != actual) {
        throw AssertionError("Test failed: expected `$expected`, got `$actual` instead.")
    }
}

fun getCpuProcessUsage(): Int {
    try {
        val pid = android.os.Process.myPid().toString()
        val cores = Runtime.getRuntime().availableProcessors()
        val process = Runtime.getRuntime().exec("top -n 1 -o PID,%CPU")
        val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
        var line = bufferedReader.readLine()
        while (line != null) {
            if (line.contains(pid)) {
                val rawCpu = line.split(" ").last().toInt()
                return rawCpu
            }
            line = bufferedReader.readLine()
        }
    } catch (e: Exception) {
        return 0
    }
    return 0
}


//  Dummy function
//@Throws
//fun handleUpTimes(message: ServerMessage): ClientMessage {
////        Log.d(TAG, "Handling UpTimes")
////            callback("Handling Up telemetry data function")
////        val start = if (train.telemetry) System.currentTimeMillis() else null
////        val layers = message.evaluateIns.parameters.tensorsList
////        assertIntsEqual(layers.size, model.tflite_layers.size)
////        val newWeights = weightsFromLayers(layers)
////        flowerClient.updateParameters(newWeights.toTypedArray())
////        val (loss, accuracy) = flowerClient.evaluate()
////        callback("Test Accuracy after this round = $accuracy")
////        val testSize = flowerClient.testSamples.size
////        val total_time:Long = 0
////        if (start != null) {
////            val end = System.currentTimeMillis()
////            val total_time = end - start
////            val job = launchJob { train.getTimeDataTelemetry(start, end, total_time) }
////            cleanUpJobs()
////            jobs.add(job)
////        }
//    return getDataAsProto(total_time)
//}