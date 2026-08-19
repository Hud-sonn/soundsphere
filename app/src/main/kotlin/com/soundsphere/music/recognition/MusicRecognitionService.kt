/**
 * Music Recognition Feature
 * 
 * This feature is based on the original MusicRecognizer project by Aleksey Saenko.
 * Original project: https://github.com/aleksey-saenko/MusicRecognizer
 * 
 * Special thanks to Aleksey Saenko for the music recognition implementation.
 */

package com.soundsphere.music.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.soundsphere.shazamkit.Shazam
import com.soundsphere.shazamkit.models.RecognitionResult
import com.soundsphere.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder

/**
 * Service for recognizing music using audio fingerprinting.
 * Records audio from the microphone, generates a Shazam-compatible fingerprint,
 * and sends it to the Shazam API for recognition.
 */
object MusicRecognitionService {
    
    // Recording parameters
    private const val RECORDING_SAMPLE_RATE = 44100
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    // Recording duration: 12 seconds for better recognition accuracy
    // We use 12s directly to match the fallback duration for maximum compatibility
    private const val RECORDING_DURATION_MS = 12000L
    private const val TAG = "MusicRecognitionService"

    private val _recognitionStatus = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Ready)
    val recognitionStatus: StateFlow<RecognitionStatus> = _recognitionStatus.asStateFlow()

    /**
     * Set to true by the widget service after it has already persisted the result to the
     * database, so that [RecognitionScreen] skips the duplicate insert.
     * Reset to false by [reset].
     */
    var resultSavedExternally: Boolean = false
    
    fun hasRecordPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Start the music recognition process.
     * Records audio, generates fingerprint, and queries Shazam API.
     */
    @SuppressLint("MissingPermission")
    suspend fun recognize(context: Context): RecognitionStatus = withContext(Dispatchers.IO) {
        if (!hasRecordPermission(context)) {
            Timber.tag(TAG).w("Microphone permission not granted, aborting recognition")
            return@withContext RecognitionStatus.Error("Microphone permission not granted")
        }

        _recognitionStatus.value = RecognitionStatus.Listening
        Timber.tag(TAG).d("Starting music recognition")
        
        try {
            // Step 1: Record audio
            val audioData = recordAudio(context)
            Timber.tag(TAG).d("Audio recorded: %d bytes", audioData.size)

            _recognitionStatus.value = RecognitionStatus.Processing
            
            // Step 2: Convert to mono if needed and resample to 16kHz
            Timber.tag(TAG).d("Resampling audio from %dHz to %dHz", RECORDING_SAMPLE_RATE, VibraSignature.REQUIRED_SAMPLE_RATE)
            val decodedAudio = DecodedAudio(
                data = audioData,
                channelCount = 1,
                sampleRate = RECORDING_SAMPLE_RATE,
                pcmEncoding = AUDIO_FORMAT
            )
            
            val resampledAudio = AudioResampler.resample(
                decodedAudio,
                VibraSignature.REQUIRED_SAMPLE_RATE
            ).getOrElse { error ->
                Timber.tag(TAG).e(error, "Audio resampling failed")
                _recognitionStatus.value = RecognitionStatus.Error("Failed to resample audio: ${error.message}")
                return@withContext _recognitionStatus.value
            }
            Timber.tag(TAG).d("Resampled audio: %d bytes, %d channels, %dHz", resampledAudio.data.size, resampledAudio.channelCount, resampledAudio.sampleRate)
            
            // Verify format
            require(
                resampledAudio.channelCount == 1 &&
                resampledAudio.sampleRate == VibraSignature.REQUIRED_SAMPLE_RATE &&
                resampledAudio.pcmEncoding == AudioFormat.ENCODING_PCM_16BIT &&
                ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN &&
                resampledAudio.data.isNotEmpty() && 
                resampledAudio.data.size % 2 == 0
            ) { "Invalid audio format for fingerprint generation" }
            
            // Step 3: Generate fingerprint using native library
            val signature = try {
                VibraSignature.fromI16(resampledAudio.data)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Fingerprint generation failed")
                _recognitionStatus.value = RecognitionStatus.Error("Failed to generate fingerprint: ${e.message}")
                return@withContext _recognitionStatus.value
            }
            
            // Step 4: Send to Shazam API
            val sampleDurationMs = (resampledAudio.data.size / 2) * 1000L / VibraSignature.REQUIRED_SAMPLE_RATE
            Timber.tag(TAG).d("Fingerprint generated, sampleDurationMs=%d", sampleDurationMs)
            
            val result = Shazam.recognize(signature, sampleDurationMs)
            
            result.fold(
                onSuccess = { recognitionResult ->
                    Timber.tag(TAG).i("Recognition successful: '%s' by %s", recognitionResult.title, recognitionResult.artist)
                    _recognitionStatus.value = RecognitionStatus.Success(recognitionResult)
                },
                onFailure = { error ->
                    val message = error.message ?: "Unknown error"
                    Timber.tag(TAG).w(error, "Recognition API returned failure: %s", message)
                    _recognitionStatus.value = if (message.contains("No match", ignoreCase = true)) {
                        RecognitionStatus.NoMatch("No matches found. Try again with clearer audio.")
                    } else {
                        RecognitionStatus.Error(message)
                    }
                }
            )
            
            _recognitionStatus.value
        } catch (e: CancellationException) {
            // A cancelled recognition (e.g. leaving the screen or stopping the
            // widget) must propagate cancellation instead of turning into an
            // Error state.
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Recognition failed with exception")
            _recognitionStatus.value = RecognitionStatus.Error(e.message ?: "Recognition failed")
            _recognitionStatus.value
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun recordAudio(context: Context): ByteArray = withContext(Dispatchers.IO) {
        // Re-check the permission at the exact point of construction: if it was
        // revoked while the app was backgrounded, AudioRecord silently constructs
        // an uninitialized instance (STATE_UNINITIALIZED) instead of throwing.
        if (!hasRecordPermission(context)) {
            Timber.tag(TAG).w("Microphone permission not granted at record time, aborting")
            throw IllegalStateException("Microphone permission not granted")
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            RECORDING_SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        if (bufferSize <= 0) {
            Timber.tag(TAG).e("AudioRecord.getMinBufferSize returned invalid size %d", bufferSize)
            throw IllegalStateException("Microphone is unavailable (invalid buffer size)")
        }
        Timber.tag(TAG).d("Recording audio: sampleRate=%d, bufferSize=%d, durationMs=%d", RECORDING_SAMPLE_RATE, bufferSize, RECORDING_DURATION_MS)

        var audioRecord: AudioRecord? = null
        var recordingStarted = false

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RECORDING_SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            // The constructor can silently fail (mic in use by another app, audio
            // policy, etc.) leaving the instance unusable. Bail out before any
            // lifecycle call is made on it.
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Timber.tag(TAG).w("AudioRecord failed to initialize (state=%d)", audioRecord.state)
                throw IllegalStateException("Microphone is unavailable")
            }

            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(bufferSize)
            val startTime = System.currentTimeMillis()

            audioRecord.startRecording()
            recordingStarted = true
            Timber.tag(TAG).d("AudioRecord started, recording for %dms", RECORDING_DURATION_MS)

            while (System.currentTimeMillis() - startTime < RECORDING_DURATION_MS && isActive) {
                val bytesRead = audioRecord.read(buffer, 0, bufferSize)
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }

            val totalBytes = outputStream.size()
            Timber.tag(TAG).d("Audio recording complete: %d bytes collected", totalBytes)
            outputStream.toByteArray()
        } finally {
            val record = audioRecord
            if (record != null) {
                // Only stop an instance that actually started recording. Calling
                // stop() on an uninitialized or already-released AudioRecord throws
                // "stop() called on an uninitialized AudioRecord" — guard the call.
                if (recordingStarted) {
                    try {
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            record.stop()
                        }
                    } catch (e: IllegalStateException) {
                        Timber.tag(TAG).e(e, "Failed to stop AudioRecord")
                    }
                }
                try {
                    record.release()
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to release AudioRecord")
                }
            }
        }
    }
    
    fun reset() {
        Timber.tag(TAG).d("Recognition state reset")
        _recognitionStatus.value = RecognitionStatus.Ready
        resultSavedExternally = false
    }
}
