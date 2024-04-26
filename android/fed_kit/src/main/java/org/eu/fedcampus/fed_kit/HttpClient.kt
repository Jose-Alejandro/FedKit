package org.eu.fedcampus.fed_kit

import okhttp3.ResponseBody
import org.eu.fedcampus.fed_kit_train.db.TFLiteModel
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import retrofit2.http.*
import java.io.File

class HttpClient constructor(url: String) {
    private val retrofit = Retrofit.Builder()
        // https://developer.android.com/studio/run/emulator-networking#networkaddresses
        .baseUrl(url)
        .addConverterFactory(GsonConverterFactory.create()).build()

    interface Advertised {
        @POST("train/advertised")
        suspend fun advertised(@Body body: PostAdvertisedData): TFLiteModel
    }

    /**
     * Download advertised model information.
     */
    @Throws
    suspend fun advertisedModel(body: PostAdvertisedData): TFLiteModel {
        val startT = System.currentTimeMillis()
        val advertised = retrofit.create<Advertised>()
        val response = advertised.advertised(body)
        val endT = System.currentTimeMillis()
        return response
    }

    interface DownloadFile {
        @GET
        @Streaming
        suspend fun download(@Url url: String): ResponseBody
    }

    @Throws
    suspend fun downloadFile(url: String, parentDir: File, fileName: String): File {
        val startT = System.currentTimeMillis()
        parentDir.mkdirs()
        val file = File(parentDir, fileName)
        val download = retrofit.create<DownloadFile>()
        download.download(url).byteStream().use { inputStream ->
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        val endT = System.currentTimeMillis()
//        var body = UpTimesTelemetryData(session_id = 2, device_id = 4, function_name = "downloadFile", elapsed_time = endT - startT)
//        upTimesDataTelemetry(body)
        return file
    }

    interface PostServer {
        @POST("train/server")
        suspend fun postServer(@Body body: PostServerData): ServerData
    }

    @Throws
    suspend fun postServer(model: TFLiteModel, start_fresh: Boolean): ServerData {
        val body = PostServerData(model.id, start_fresh)
        val postServer = retrofit.create<PostServer>()
        return postServer.postServer(body)
    }

    interface FitInsTelemetry {
        @POST("telemetry/fit_ins")
        suspend fun fitInsTelemetry(@Body body: FitInsTelemetryData)
    }

    @Throws
    suspend fun fitInsTelemetry(body: FitInsTelemetryData) {
        val fitInsTelemetry = retrofit.create<FitInsTelemetry>()
        fitInsTelemetry.fitInsTelemetry(body)
    }

    interface EvaluateInsTelemetry {
        @POST("telemetry/evaluate_ins")
        suspend fun evaluateInsTelemetry(@Body body: EvaluateInsTelemetryData)
    }

    @Throws
    suspend fun evaluateInsTelemetry(body: EvaluateInsTelemetryData) {
        val evaluateInsTelemetry = retrofit.create<EvaluateInsTelemetry>()
        evaluateInsTelemetry.evaluateInsTelemetry(body)
    }

    interface UpTimesDataTelemetry {
        @POST("telemetry/up_times")
        suspend fun upTimesDataTelemetry(@Body body: UpTimesTelemetryData)
    }

    @Throws
    suspend fun upTimesDataTelemetry(body: UpTimesTelemetryData) {
        val upTimesDataTelemetry = retrofit.create<UpTimesDataTelemetry>()
        upTimesDataTelemetry.upTimesDataTelemetry(body)
    }
}

data class PostAdvertisedData(val data_type: String)

// Always change together with Python `train.data.ServerData`.
data class ServerData(val status: String, val session_id: Int?, val port: Int?)

data class PostServerData(val id: Long, val start_fresh: Boolean)

// Always change together with Python `telemetry.models.FitInsTelemetryData`.
data class FitInsTelemetryData(
    val device_id: Long,
    val session_id: Int,
    val start: Long,
    val end: Long
)

// Always change together with Python `telemetry.models.EvaluateInsTelemetryData`.
data class EvaluateInsTelemetryData(
    val device_id: Long,
    val session_id: Int,
    val start: Long,
    val end: Long,
    val loss: Float,
    val accuracy: Float,
    val test_size: Int
)

data class UpTimesTelemetryData(
        val device_id: Long,
        val session_id: Int,
        val function_name: String,
        val elapsed_time: Long
)
