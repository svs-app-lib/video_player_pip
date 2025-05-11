import Flutter
import UIKit
import AVFoundation
import AVKit

public class VideoPlayerPipPlugin: NSObject, FlutterPlugin, AVPictureInPictureControllerDelegate {
  private var channel: FlutterMethodChannel?
  private var pipController: AVPictureInPictureController?
  private var isInPipMode = false
  
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "video_player_pip", binaryMessenger: registrar.messenger())
    let instance = VideoPlayerPipPlugin(channel: channel)
    registrar.addMethodCallDelegate(instance, channel: channel)
  }
  
  init(channel: FlutterMethodChannel) {
    self.channel = channel
    super.init()
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "isPipSupported":
      result(isPipSupported())
      
    case "enterPipMode":
      guard let args = call.arguments as? [String: Any],
            let playerId = args["playerId"] as? Int else {
        result(FlutterError(code: "INVALID_ARGUMENTS", message: "Missing playerId", details: nil))
        return
      }
      
      let width = args["width"] as? Int
      let height = args["height"] as? Int
      
      enterPipMode(playerId: playerId, width: width, height: height, completion: result)
      
    case "exitPipMode":
      exitPipMode(completion: result)
      
    case "isInPipMode":
      result(isInPipMode)
      
    default:
      result(FlutterMethodNotImplemented)
    }
  }
  
  private func isPipSupported() -> Bool {
    if #available(iOS 14.0, *) {
      return AVPictureInPictureController.isPictureInPictureSupported()
    }
    return false
  }
  
  private func enterPipMode(playerId: Int, width: Int?, height: Int?, completion: @escaping FlutterResult) {
    if !isPipSupported() {
      completion(false)
      return
    }
    
    // Find the AVPlayerLayer
    guard let playerLayer = findAVPlayerLayer(playerId: playerId) else {
      NSLog("VideoPlayerPip: Could not find player layer for ID: \(playerId)")
      completion(false)
      return
    }
    
    // Create and configure the PiP controller
    if #available(iOS 14.0, *) {
      pipController = AVPictureInPictureController(playerLayer: playerLayer)
      pipController?.delegate = self
      
      // Configure custom size if provided (iOS 15.0+)
      if #available(iOS 15.0, *), let width = width, let height = height {
        if let pipController = pipController {
          let customSize = CGSize(width: width, height: height)
          pipController.preferredContentSize = customSize
        }
      }
      
      // Start PiP
      pipController?.startPictureInPicture()
      completion(true)
    } else {
      completion(false)
    }
  }
  
  private func exitPipMode(completion: @escaping FlutterResult) {
    if isInPipMode, pipController != nil {
      pipController?.stopPictureInPicture()
      completion(true)
    } else {
      completion(false)
    }
  }
  
  /**
   * Find the AVPlayerLayer for the specified player ID.
   * This searches through the view hierarchy to find the platform view created by video_player.
   */
  private func findAVPlayerLayer(playerId: Int) -> AVPlayerLayer? {
    if let rootViewController = UIApplication.shared.keyWindow?.rootViewController {
      // Start with the root view and search recursively
      return findAVPlayerLayerInView(rootViewController.view)
    }
    return nil
  }
  
  /**
   * Recursively search for an AVPlayerLayer in the view hierarchy.
   */
  private func findAVPlayerLayerInView(_ view: UIView) -> AVPlayerLayer? {
    // Check if this view's layer is an AVPlayerLayer
    if let playerLayer = view.layer as? AVPlayerLayer {
      return playerLayer
    }
    
    // Check for class name matching FVPPlayerView which has an AVPlayerLayer as its layer
    let className = NSStringFromClass(type(of: view))
    if className.contains("FVPPlayerView") {
      return view.layer as? AVPlayerLayer
    }
    
    // Check sublayers directly in case AVPlayerLayer is a sublayer
    if let sublayers = view.layer.sublayers {
      for sublayer in sublayers {
        if let playerLayer = sublayer as? AVPlayerLayer {
          return playerLayer
        }
      }
    }
    
    // Recursively check subviews
    for subview in view.subviews {
      if let layer = findAVPlayerLayerInView(subview) {
        return layer
      }
    }
    
    return nil
  }
  
  // MARK: - AVPictureInPictureControllerDelegate
  
  public func pictureInPictureControllerDidStartPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
    isInPipMode = true
    channel?.invokeMethod("pipModeChanged", arguments: ["isInPipMode": true])
  }
  
  public func pictureInPictureControllerDidStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
    isInPipMode = false
    channel?.invokeMethod("pipModeChanged", arguments: ["isInPipMode": false])
  }
  
  public func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, failedToStartPictureInPictureWithError error: Error) {
    NSLog("VideoPlayerPip: Failed to start PiP: \(error.localizedDescription)")
    channel?.invokeMethod("pipError", arguments: ["error": error.localizedDescription])
  }
}
