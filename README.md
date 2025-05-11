# video_player_pip

A Flutter plugin that adds Picture-in-Picture (PiP) support for the video_player package.

## Features

- Enter/exit PiP mode for videos played with video_player
- Check if PiP is supported on the current device
- Monitor PiP state changes via stream
- Toggle PiP mode
- Works on both Android and iOS platforms

## Requirements

- **Android**: API level 26 (Android 8.0) or higher
- **iOS**: iOS 14.0 or higher
- Flutter 3.3.0 or higher
- Dart SDK 3.7.2 or higher

## Installation

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  video_player_pip: ^0.0.1
```

### Platform-specific Setup

#### Android

Ensure your Android app has the proper permissions in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>
```

For Android 12 and higher, also add:

```xml
<activity
    android:name="YOUR_ACTIVITY_NAME"
    android:supportsPictureInPicture="true"
    android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"
    ... >
</activity>
```

#### iOS

For iOS, no additional setup is required. The plugin handles the necessary setup internally.

## Usage

Here's a simple example of how to use the plugin:

```dart
import 'package:flutter/material.dart';
import 'package:video_player_pip/index.dart';

class VideoScreen extends StatefulWidget {
  @override
  _VideoScreenState createState() => _VideoScreenState();
}

class _VideoScreenState extends State<VideoScreen> {
  late VideoPlayerController _controller;
  bool _isInitialized = false;

  @override
  void initState() {
    super.initState();
    
    // Create a video controller
    _controller = VideoPlayerController.network(
      'https://example.com/sample-video.mp4',
    );
    
    // Initialize
    _controller.initialize().then((_) {
      setState(() {
        _isInitialized = true;
      });
      _controller.play();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Video Player with PiP')),
      body: Center(
        child: _isInitialized
            ? AspectRatio(
                aspectRatio: _controller.value.aspectRatio,
                child: VideoPlayer(_controller),
              )
            : CircularProgressIndicator(),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () async {
          // Calculate dimensions based on aspect ratio
          final aspectRatio = _controller.value.aspectRatio;
          const width = 300;
          final height = (width / aspectRatio).round();
          
          // Enter PiP mode
          await _controller.enterPipMode(
            context: context,
            width: width,
            height: height,
          );
        },
        child: Icon(Icons.picture_in_picture),
      ),
    );
  }
}
```

## Additional Features

### Check if PiP is supported

```dart
bool isPipSupported = await VideoPlayerPip.isPipSupported();
```

### Listen to PiP state changes

```dart
VideoPlayerPip.instance.onPipModeChanged.listen((isInPipMode) {
  print('Is in PiP mode: $isInPipMode');
});
```

### Toggle PiP mode

```dart
await VideoPlayerPip.instance.togglePipMode(
  _controller, 
  width: 300, 
  height: 200, 
  context: context,
);
```

### Exit PiP mode programmatically

```dart
await VideoPlayerPip.exitPipMode();
```

## Example

Check out the [example app](example/) for a complete implementation.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Issues and Feedback

Please file issues, bugs, or feature requests in our [issue tracker](https://github.com/AkmaljonAbdirakhimov/video_player_pip/issues).

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
