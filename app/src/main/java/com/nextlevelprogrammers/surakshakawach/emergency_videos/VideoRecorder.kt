package com.nextlevelprogrammers.surakshakawach.emergency_videos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VideoRecorder(private val context: Context) {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null // ✅ Store active recording session
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val storageReference: StorageReference = FirebaseStorage.getInstance().reference.child("emergency_videos")
    private var isRecordingActive = true

    /**
     * Initializes CameraX and sets up Video Capture in SD quality.
     */
    fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val qualitySelector = QualitySelector.from(Quality.SD)
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                context as androidx.lifecycle.LifecycleOwner,
                cameraSelector,
                videoCapture
            )

            Log.d("VideoRecorder", "✅ CameraX Initialized with SD Quality")
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Starts continuous video recording: 30s recording, then 10s gap.
     */
    fun startContinuousRecording(userId: String, onVideoUploaded: (String, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            while (isRecordingActive) {
                val videoFile = startVideoRecording() ?: continue // Skip if recording fails
                delay(15000) // 🎥 Record for 15 seconds

                stopVideoRecording(videoFile) { uploadedUrl, bucketUrl ->
                    onVideoUploaded(uploadedUrl, bucketUrl)
                }

                delay(10000) // ⏳ Wait for 10 seconds before the next recording
            }
        }
    }

    /**
     * ✅ Checks Camera and Audio Permissions
     */
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * ✅ Starts recording a video safely and returns the file.
     */
    private fun startVideoRecording(): File? {
        if (!hasCameraPermission() || !hasAudioPermission()) {
            Log.e("VideoRecorder", "❌ Camera or Audio permission not granted. Cannot start recording!")
            return null
        }

        val outputFile = createVideoFile()
        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        currentRecording = videoCapture?.output
            ?.prepareRecording(context, outputOptions)
            ?.withAudioEnabled()
            ?.start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Start) {
                    Log.d("VideoRecorder", "🎥 Recording Started")
                }
            }

        return outputFile
    }

    /**
     * ✅ Stops recording correctly by using `currentRecording?.stop()`
     */
    private fun stopVideoRecording(file: File, onUploaded: (String, String) -> Unit) {
        currentRecording?.stop() // ✅ Properly stop recording using stored session
        currentRecording = null // Reset recording session

        Log.d("VideoRecorder", "⏹ Video Recording Stopped: ${file.absolutePath}")

        CoroutineScope(Dispatchers.IO).launch {
            val compressedFile = compressVideoFile(file, context) // ✅ Compress video before uploading
            uploadVideoToFirebase(compressedFile, onUploaded)
        }
    }

    /**
     * ✅ Creates a temporary file to store the video before uploading.
     */
    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File.createTempFile("SOS_$timeStamp", ".mp4", storageDir)
    }

    /**
     * ✅ Compresses the video file using FFmpeg before uploading to Firebase.
     */
    private fun compressVideoFile(inputFile: File, context: Context): File {
        val outputFile = File(context.getExternalFilesDir(null), "compressed_${inputFile.name}")

        // ✅ Ensure the file exists before processing
        if (!inputFile.exists() || !inputFile.canRead()) {
            Log.e("VideoCompression", "❌ Cannot read input video file: ${inputFile.absolutePath}")
            return inputFile // Use the original file if it's unreadable
        }

        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath) // 🛑 May fail if the file is not readable

            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    trackIndex = i
                    extractor.selectTrack(i)
                    break
                }
            }
            if (trackIndex == -1) {
                Log.e("VideoCompression", "❌ No video track found!")
                return inputFile
            }

            val format = extractor.getTrackFormat(trackIndex)
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: "video/avc"

            val encoder = MediaCodec.createEncoderByType(mimeType)
            val outputFormat = MediaFormat.createVideoFormat(mimeType, 640, 360)

            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, 512_000)
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()
            val byteBuffer = ByteBuffer.allocate(1024 * 1024)

            var isMuxerStarted = false
            var muxerTrackIndex = -1

            while (true) {
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputBufferIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(outputBufferIndex) ?: continue

                    if (!isMuxerStarted) {
                        muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        isMuxerStarted = true
                    }

                    muxer.writeSampleData(muxerTrackIndex, encodedData, bufferInfo)
                    encoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }

            muxer.stop()
            muxer.release()
            encoder.stop()
            encoder.release()
            extractor.release()

            return if (outputFile.length() > 5 * 1024 * 1024) inputFile else outputFile

        } catch (e: Exception) {
            Log.e("VideoCompression", "❌ Compression failed: ${e.localizedMessage}")
            return inputFile
        }
    }

    /**
     * ✅ Uploads recorded video to Firebase Storage.
     */
    private suspend fun uploadVideoToFirebase(file: File, onUploaded: (String, String) -> Unit) {
        val fileName = file.name

        // ✅ Use the specific Firebase bucket
        val storage = FirebaseStorage.getInstance("gs://suraksha-kawach-151024-v2-development")
        val storageReference = storage.reference.child("emergency_videos/$fileName")

        try {
            Log.d("FirebaseUpload", "Uploading file: $fileName to Firebase Storage in 'suraksha-kawach-151024-v2-development' bucket")

            storageReference.putFile(Uri.fromFile(file)).await() // ✅ Upload file
            val downloadUrl = storageReference.downloadUrl.await().toString()
            val bucketUrl = "gs://suraksha-kawach-151024-v2-development/emergency_videos/$fileName"

            Log.d("FirebaseUpload", "✅ File Uploaded Successfully: $downloadUrl")

            onUploaded(downloadUrl, bucketUrl)
            file.delete() // ✅ Delete local file after successful upload
        } catch (e: Exception) {
            Log.e("FirebaseUpload", "❌ Upload Failed: ${e.localizedMessage}")
        }
    }

    /**
     * ✅ Stops continuous video recording.
     */
    fun stopRecording() {
        isRecordingActive = false
        Log.d("VideoRecorder", "⛔ Stopped Continuous Recording")
    }
}