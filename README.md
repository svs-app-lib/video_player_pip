# video_player_pip

A Flutter plugin that adds picture-in-picture (PiP) support for the `video_player` package.

## Features

- Enter and exit PiP mode for videos playing with `video_player`
- Check if PiP is supported on the current device
- Check if PiP mode is currently active
- Convenient extension methods for `VideoPlayerController`
- Factory methods for creating controllers optimized for PiP support
- Custom `PipController` that supports specifying view type

## Platform Support

| Android | iOS | macOS | Web | Windows | Linux |
| :-----: | :-: | :---: | :-: | :-----: | :---: |
|   ✅    | ✅  |   ❌   | ❌  |   ❌    |  ❌   |

- **Android**: Requires API level 26 (Android 8.0) or higher
- **iOS**: Requires iOS 14.0 or higher

## Installation

```yaml
dependencies:
  video_player_pip: ^0.0.1
```

## Usage

### Using PipController (Recommended)

The `PipController` provides the same API as the standard `VideoPlayerController` but adds PiP functionality and allows you to explicitly specify the view type:

```dart
import 'package:video_player_pip/index.dart';

void main() async {
  // Create a controller with platform view (best for PiP)
  final controller = PipController.network(
    'https://example.com/video.mp4',
    videoViewType: VideoViewType.platformView, // Explicitly set the view type
  );
  
  await controller.initialize();
  controller.play();
  
  // Enter PiP mode
  await controller.enterPipMode();
  
  // Exit PiP mode
  await controller.exitPipMode();
  
  // Toggle PiP mode
  await controller.togglePipMode();
  
  // Check if in PiP mode
  final isInPip = await controller.isInPipMode();
}
```

### Using PipController with VideoPlayer widget

Since the VideoPlayer widget expects a standard VideoPlayerController, you can use the following approach:

```dart
import 'package:flutter/material.dart';
import 'package:video_player_pip/index.dart';

class MyVideoPlayer extends StatefulWidget {
  @override
  _MyVideoPlayerState createState() => _MyVideoPlayerState();
}

class _MyVideoPlayerState extends State<MyVideoPlayer> {
  late PipController _pipController;
  
  @override
  void initState() {
    super.initState();
    
    // Create a PipController
    _pipController = PipController.network(
      'https://example.com/video.mp4',
      videoViewType: VideoViewType.platformView,
    );
    
    _pipController.initialize();
  }
  
  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        // Method 1: Access the internal VideoPlayerController
        AspectRatio(
          aspectRatio: _pipController.value.aspectRatio,
          child: VideoPlayer(_pipController.videoPlayerController),
        ),
        
        // Method 2: Use the extension method
        AspectRatio(
          aspectRatio: _pipController.value.aspectRatio,
          child: VideoPlayer(_pipController.asVideoPlayerController()),
        ),
        
        ElevatedButton(
          onPressed: () => _pipController.enterPipMode(),
          child: Text('Enter PiP Mode'),
        ),
      ],
    );
  }
  
  @override
  void dispose() {
    _pipController.dispose();
    super.dispose();
  }
}
```

The `PipController` provides the same API as the standard controller:

```dart
// Create from different sources
final networkController = PipController.network('https://example.com/video.mp4');
final assetController = PipController.asset('assets/my_video.mp4');
final fileController = PipController.file(File('/path/to/video.mp4'));

// Use just like a regular VideoPlayerController
controller.play();
controller.pause();
controller.seekTo(Duration(seconds: 10));
controller.setVolume(0.5);
```

### Using with existing VideoPlayerController

If you already have code using standard `VideoPlayerController`, you can add PiP functionality:

```dart
import 'package:video_player_pip/index.dart';

void main() async {
  // Create a standard controller
  final controller = VideoPlayerController.network('https://example.com/video.mp4');
  await controller.initialize();
  
  // Enter PiP mode using extension method
  await controller.enterPipMode();
  
  // Or use the static method
  await VideoPlayerPip.enterPipMode(controller);
  
  // Exit PiP mode
  await VideoPlayerPip.exitPipMode();
}
```

### Using the PipVideoPlayerFactory

For a simpler approach without explicitly specifying the view type:

```dart
import 'package:video_player_pip/index.dart';

void main() async {
  // Create a controller optimized for PiP support
  final controller = PipVideoPlayerFactory.network(
    'https://example.com/video.mp4',
    allowBackgroundPlayback: true, // Ensures video continues to play in PiP
  );
  
  await controller.initialize();
  controller.play();
  
  // Enter PiP mode
  await controller.enterPipMode();
}
```

### Complete Example

```dart
import 'package:flutter/material.dart';
import 'package:video_player_pip/index.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: VideoPlayerScreen(),
    );
  }
}

class VideoPlayerScreen extends StatefulWidget {
  @override
  _VideoPlayerScreenState createState() => _VideoPlayerScreenState();
}

class _VideoPlayerScreenState extends State<VideoPlayerScreen> {
  late PipController _controller;
  bool _isPipSupported = false;
  bool _isInPipMode = false;

  @override
  void initState() {
    super.initState();
    _initializeVideo();
    _checkPipSupport();
  }

  Future<void> _initializeVideo() async {
    // Use PipController with platform view for best PiP support
    _controller = PipController.network(
      'https://flutter.github.io/assets-for-api-docs/assets/videos/butterfly.mp4',
      videoViewType: VideoViewType.platformView, // Explicitly use platform view
    );
    
    await _controller.initialize();
    setState(() {});
  }

  Future<void> _checkPipSupport() async {
    final bool isPipSupported = await VideoPlayerPip.isPipSupported();
    setState(() {
      _isPipSupported = isPipSupported;
    });
  }

  Future<void> _togglePip() async {
    // Using the controller's built-in toggle method
    await _controller.togglePipMode();
    
    // Update PiP state
    final bool isInPipMode = await _controller.isInPipMode();
    setState(() {
      _isInPipMode = isInPipMode;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Video Player with PiP'),
      ),
      body: Column(
        children: [
          if (_controller.value.isInitialized)
            AspectRatio(
              aspectRatio: _controller.value.aspectRatio,
              // Use the internal VideoPlayerController with the VideoPlayer widget
              child: VideoPlayer(_controller.videoPlayerController),
            ),
          const SizedBox(height: 20),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              ElevatedButton(
                onPressed: () {
                  setState(() {
                    _controller.value.isPlaying
                        ? _controller.pause()
                        : _controller.play();
                  });
                },
                child: Icon(
                  _controller.value.isPlaying ? Icons.pause : Icons.play_arrow,
                ),
              ),
              if (_isPipSupported)
                ElevatedButton(
                  onPressed: _togglePip,
                  child: Icon(_isInPipMode ? Icons.fullscreen : Icons.picture_in_picture_alt),
                ),
            ],
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}
```

## Android Setup

Add this to your `AndroidManifest.xml` inside the `<activity>` tag of your main activity:

```xml
<activity
    android:name=".MainActivity"
    android:supportsPictureInPicture="true"
    android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"
    ... />
```

### Required Configuration Changes:

1. **Manifest Changes**: The `android:configChanges` attribute is crucial for PiP to work properly. Without it, your app may restart when entering PiP mode.

2. **Android Version Support**:
   - Android 8.0+ (API 26): Basic PiP support
   - Android 9.0+ (API 28): Improved stability
   - Android 10+ (API 29): Better exit PiP handling
   - Android 12+ (API 31): Auto-enter PiP mode support

3. **Permissions**: The plugin automatically includes the `FOREGROUND_SERVICE` permission which may be required on some devices.

### Handling PiP Permission:

On some Android devices, users need to manually enable PiP permission for your app:
1. Settings → Apps → Your App → Advanced → Picture-in-picture
2. Enable the "Allow picture-in-picture" toggle

You might want to guide users to this setting if PiP doesn't work, which can be detected using the `isPipSupported()` method.

## iOS Setup

No additional setup is required for iOS, but note that PiP is only supported on iOS 14.0 and above.

## Important Notes

- For Android, the user must grant PiP permission in Settings for your app
- For best iOS support, use `PipController` with `VideoViewType.platformView`
- `allowBackgroundPlayback` is important for PiP to work properly when the app is in the background

## License

[MIT License](LICENSE)

