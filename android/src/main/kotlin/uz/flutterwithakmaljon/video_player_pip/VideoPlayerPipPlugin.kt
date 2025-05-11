package uz.flutterwithakmaljon.video_player_pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** VideoPlayerPipPlugin */
class VideoPlayerPipPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  private val TAG = "VideoPlayerPipPlugin"
  
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel: MethodChannel
  private lateinit var context: Context
  private var activity: Activity? = null
  private var isInPipMode = false
  private var activityBinding: ActivityPluginBinding? = null
  
  // Cache of player ID to view mappings to improve performance for multiple calls
  private val playerViewCache = mutableMapOf<Int, View?>()
  
  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "video_player_pip")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "isPipSupported" -> {
        result.success(isPipSupported())
      }
      "enterPipMode" -> {
        val playerId = call.argument<Int>("playerId")
        val width = call.argument<Int>("width")
        val height = call.argument<Int>("height")
        if (playerId != null) {
          result.success(enterPipMode(playerId, width, height))
        } else {
          result.error("INVALID_ARGUMENT", "Player ID is required", null)
        }
      }
      "exitPipMode" -> {
        result.success(exitPipMode())
      }
      "isInPipMode" -> {
        result.success(isInPipMode)
      }
      else -> {
        result.notImplemented()
      }
    }
  }
  
  private fun isPipSupported(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
  }
  
  private fun enterPipMode(playerId: Int, customWidth: Int?, customHeight: Int?): Boolean {
    if (!isPipSupported() || activity == null) {
      Log.d(TAG, "PiP not supported or activity is null")
      return false
    }
    
    try {
      // Clear cache if different player ID
      if (!playerViewCache.containsKey(playerId)) {
        playerViewCache.clear()
      }
      
      // Find the video player view in the hierarchy
      val videoView = playerViewCache.getOrPut(playerId) { 
        findVideoPlayerView(playerId) 
      }
      
      if (videoView == null) {
        Log.e(TAG, "Could not find video player view for ID: $playerId")
        return false
      }
      
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Use custom dimensions if provided, otherwise use the view's dimensions
        val width = customWidth ?: videoView.width
        val height = customHeight ?: videoView.height
        
        // Default to 16:9 if dimensions are invalid or too small
        val aspectRatio = if (width > 0 && height > 0 && width >= 100 && height >= 100) {
          Rational(width, height)
        } else {
          Rational(16, 9)
        }
        
        val paramsBuilder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
        
        // Add auto-enter PiP for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            paramsBuilder.setAutoEnterEnabled(true)
        }
        
        val params = paramsBuilder.build()
        
        // Enter PiP mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return activity?.enterPictureInPictureMode(params) ?: false
        }
      }
      
      return false
    } catch (e: Exception) {
      Log.e(TAG, "Error entering PiP mode", e)
      return false
    }
  }
  
  private fun exitPipMode(): Boolean {
    if (!isPipSupported() || activity == null) {
      return false
    }
    
    try {
      if (isInPipMode) {
        // For Android 10+, we can use a better approach to exit PiP mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          activity?.let {
            // This will bring the app back to full screen
            it.requestedOrientation = it.requestedOrientation
          }
        } else {
          // For older versions, moving task to back and then bringing it forward is a workaround
          activity?.moveTaskToBack(false)
          activity?.let {
            it.startActivity(it.packageManager.getLaunchIntentForPackage(it.packageName))
          }
        }
        return true
      }
      return false
    } catch (e: Exception) {
      Log.e(TAG, "Error exiting PiP mode", e)
      return false
    }
  }
  
  /**
   * Finds the video player view for the given player ID.
   * This is implemented based on analysis of how video_player creates its views.
   */
  private fun findVideoPlayerView(playerId: Int): View? {
    if (activity == null) return null
    
    val rootView = activity?.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)
    
    // Try to find a tag or ID that might match the player ID
    return findVideoPlayerViewRecursively(rootView, playerId)
  }
  
  /**
   * Recursively searches the view hierarchy for the SurfaceView or TextureView used by video_player.
   * Tries to match by playerID and also looks for platform view containers.
   */
  private fun findVideoPlayerViewRecursively(view: View?, playerId: Int): View? {
    if (view == null) return null
    
    // Check if the view has a tag matching our player ID
    if (view.tag != null && view.tag is String && (view.tag as String).contains("$playerId")) {
      return view
    }
    
    // Check if this is a SurfaceView (what video_player uses in platform view mode)
    if (view is SurfaceView) {
      // This could be our video view
      val parent = view.parent as? View
      if (isFlutterPlatformView(parent)) {
        // Check if we can find any references to the player ID in the view hierarchy
        if (parent?.tag != null && parent.tag.toString().contains("$playerId")) {
          return view
        }
        // If we can't find a direct reference but this is the only video view, use it
        return view
      }
    }
    
    // Check if it's a platform view with a SurfaceView child
    if (isFlutterPlatformView(view)) {
      if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
          val child = view.getChildAt(i)
          if (child is SurfaceView) {
            return child
          }
        }
      }
    }
    
    // Recursively check children
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        val found = findVideoPlayerViewRecursively(view.getChildAt(i), playerId)
        if (found != null) return found
      }
    }
    
    // If we've checked everything and still can't find a match, return the first SurfaceView
    // This is a fallback for when we can't find a direct match
    if (view is ViewGroup && playerId != -1) {
      var firstSurfaceView: SurfaceView? = null
      
      for (i in 0 until view.childCount) {
        val child = view.getChildAt(i)
        if (child is SurfaceView && firstSurfaceView == null) {
          firstSurfaceView = child
        }
      }
      
      if (firstSurfaceView != null) {
        return firstSurfaceView
      }
    }
    
    return null
  }
  
  /**
   * Checks if the view is a Flutter platform view.
   */
  private fun isFlutterPlatformView(view: View?): Boolean {
    if (view == null) return false
    
    // The class name for platform views contains "PlatformView"
    val className = view.javaClass.name
    return className.contains("PlatformView") && !className.contains("Factory")
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    playerViewCache.clear()
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    activityBinding = binding
    
    // Set up PiP mode change listener
    setupPipModeChangeListener(binding)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    cleanupPipModeChangeListener()
    activity = null
    activityBinding = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
    activityBinding = binding
    
    // Re-set up PiP mode change listener
    setupPipModeChangeListener(binding)
  }

  override fun onDetachedFromActivity() {
    cleanupPipModeChangeListener()
    activity = null
    activityBinding = null
    playerViewCache.clear()
  }
  
  private fun setupPipModeChangeListener(binding: ActivityPluginBinding) {
    binding.addActivityResultListener { requestCode, resultCode, data ->
      // Update PiP state on activity result as a fallback mechanism
      updatePipState()
      false // Not consuming the result
    }
    
    // Use Configuration.onPictureInPictureModeChanged for API 26+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      activity?.registerComponentCallbacks(object : android.content.ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
          val isInPictureInPictureMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.isInPictureInPictureMode ?: false
          } else {
            false
          }
          
          if (isInPipMode != isInPictureInPictureMode) {
            isInPipMode = isInPictureInPictureMode
            notifyPipModeChanged()
          }
        }
        
        override fun onLowMemory() {
          // No implementation needed
        }
      })
    }
  }
  
  private fun cleanupPipModeChangeListener() {
    // Clean up any listeners to prevent memory leaks
    activityBinding = null
  }
  
  private fun updatePipState() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val newPipState = activity?.isInPictureInPictureMode ?: false
      if (newPipState != isInPipMode) {
        isInPipMode = newPipState
        notifyPipModeChanged()
      }
    }
  }
  
  private fun notifyPipModeChanged() {
    try {
      channel.invokeMethod("pipModeChanged", mapOf(
          "isInPipMode" to isInPipMode
      ))
    } catch (e: Exception) {
      Log.e(TAG, "Error notifying PiP mode change", e)
    }
  }
}
