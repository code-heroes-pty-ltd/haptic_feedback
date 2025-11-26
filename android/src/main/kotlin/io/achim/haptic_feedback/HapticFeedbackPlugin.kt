package io.achim.haptic_feedback

import android.app.Activity
import android.view.View
import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

class HapticFeedbackPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private lateinit var vibrator: Vibrator
    private var activity: Activity? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "haptic_feedback")
        channel.setMethodCallHandler(this)
        vibrator =
            flutterPluginBinding.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (call.method == "canVibrate") {
            canVibrate(result)
        } else {
            val view: View? = activity?.window?.decorView
            if (view == null) {
                result.error("NO_VIEW", "No view available", null)
                return
            }
            if (call.method == "success") {
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (view.isHapticFeedbackEnabled()) {
                        val played = view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        if (played) {
                            // If not played, will fallback
                            result.success(null)
                            return
                        }
                    } else {
                        // Don't play anything. Haptics are disabled
                        result.success(null)
                        return
                    }
                }
            } else if (call.method == "error") {
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (view.isHapticFeedbackEnabled()) {
                        val played = view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        if (played) {
                            // If not played, will fallback
                            result.success(null)
                            return
                        }
                    } else {
                        // Don't play anything. Haptics are disabled
                        result.success(null)
                        return
                    }
                }
            }
            val pattern = Pattern.values().find { it.name == call.method }
            if (pattern != null) {
                val usage = Usage.fromArguments(call.arguments)
                vibratePattern(pattern, usage, result)
            } else {
                result.notImplemented()
            }
        }
    }

    private fun canVibrate(result: Result) {
        result.success(vibrator.hasVibrator())
    }

    private fun vibratePattern(pattern: Pattern, usage: Usage?, result: Result) {
        val shouldNotRepeat = -1


        try {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()) {
                val effect = VibrationEffect.createWaveform(
                    pattern.lengths,
                    pattern.amplitudes,
                    shouldNotRepeat
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && usage != null) {
                    vibrator.vibrate(effect, usage.toVibrationAttributes())
                } else {
                    vibrator.vibrate(effect)
                }
            } else {
                // https://developer.android.com/reference/android/os/Vibrator#vibrate(long[],%20int)
                val leadingDelay = longArrayOf(0)
                val legacyPattern = leadingDelay + pattern.lengths
                vibrator.vibrate(legacyPattern, shouldNotRepeat)
            }
            result.success(null)
        } catch (e: Exception) {
            result.error("VIBRATION_ERROR", "Failed to vibrate", e.localizedMessage)
        }
    }

    private enum class Pattern(val lengths: LongArray, val amplitudes: IntArray) {
        success(longArrayOf(75, 75, 75), intArrayOf(178, 0, 255)),
        warning(longArrayOf(79, 119, 75), intArrayOf(227, 0, 178)),
        error(longArrayOf(75, 61, 79, 57, 75, 57, 97), intArrayOf(203, 0, 200, 0, 252, 0, 150)),
        light(longArrayOf(79), intArrayOf(154)),
        medium(longArrayOf(79), intArrayOf(203)),
        heavy(longArrayOf(75), intArrayOf(252)),
        rigid(longArrayOf(48), intArrayOf(227)),
        soft(longArrayOf(110), intArrayOf(178)),
        selection(longArrayOf(57), intArrayOf(150))
    }

    internal enum class Usage {
        alarm,
        communicationRequest,
        hardwareFeedback,
        media,
        notification,
        physicalEmulation,
        ringtone,
        touch,
        unknown;

        companion object {
            fun fromArguments(arguments: Any?): Usage? {
                val usageValue = (arguments as? Map<*, *>)?.get("usage") as? String ?: return null
                return entries.firstOrNull { it.name.equals(usageValue, ignoreCase = true) }
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun toVibrationAttributes(): VibrationAttributes {
            val usageConstant = when (this) {
                alarm -> VibrationAttributes.USAGE_ALARM
                communicationRequest -> VibrationAttributes.USAGE_COMMUNICATION_REQUEST
                hardwareFeedback -> VibrationAttributes.USAGE_HARDWARE_FEEDBACK
                media -> VibrationAttributes.USAGE_MEDIA
                notification -> VibrationAttributes.USAGE_NOTIFICATION
                physicalEmulation -> VibrationAttributes.USAGE_PHYSICAL_EMULATION
                ringtone -> VibrationAttributes.USAGE_RINGTONE
                touch -> VibrationAttributes.USAGE_TOUCH
                unknown -> VibrationAttributes.USAGE_UNKNOWN
            }
            return VibrationAttributes.Builder().setUsage(usageConstant).build()
        }
    }
}
