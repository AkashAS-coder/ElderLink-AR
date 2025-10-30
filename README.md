# Elderly Care App

A comprehensive Android application designed to provide AI-powered companionship and exercise guidance for elderly users.

## Features

### 🤖 AI Companion Chatbot

- **Voice Support**: Full voice input and output capabilities
- **Initial Prompt System**: Set custom context for AI behavior
- **Secure API Management**: Firebase-based API key security
- **Real-time Responses**: Powered by Google Gemini AI

### 📸 AI Exercise Coach

- **Pose Detection**: Real-time exercise form analysis
- **Multiple Exercises**: Support for various elderly-friendly exercises
- **Emulator Support**: Test mode for development environments
- **Camera Integration**: Front/back camera support

### 🔔 Smart Reminders

- **Customizable Timing**: Set daily reminder notifications
- **Permission Handling**: Proper notification permission management
- **Background Processing**: Reliable reminder delivery

## Setup Instructions

### 1. Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 24+ (API level 24)
- Google Gemini API key
- Firebase project (optional, for enhanced security)

### 2. API Key Configuration

#### Option A: Local Properties (Development)

1. Create `local.properties` in the project root
2. Add your Gemini API key:

```properties
GEMINI_API_KEY=your_actual_gemini_api_key_here
```

#### Option B: Firebase Remote Config (Production)

1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Download `google-services.json` and place it in the `app/` directory
3. Configure Remote Config with key `gemini_api_key`
4. The app will automatically fetch the API key from Firebase

### 3. Camera Emulator Support

The app automatically detects emulator environments and provides:

- Test frame generation for development
- Simulated camera functionality
- Clear indicators when running in emulator mode

### 4. Building the App

```bash
# Clone the repository
git clone <repository-url>
cd ElderlyCareApp

# Sync Gradle files
./gradlew clean build

# Install on device/emulator
./gradlew installDebug
```

## Architecture

### Core Components

- **MainActivity**: Main application entry point and navigation
- **GeminiService**: AI service integration with secure API management
- **SecureApiService**: Firebase-based API key security
- **EmulatorCameraService**: Camera emulator support
- **PoseAnalyzer**: Exercise form analysis

### Security Features

- API keys never stored in source code
- Firebase Remote Config for production deployments
- Local properties fallback for development
- Secure HTTP client configuration

## Usage

### Voice Chat

1. Navigate to "AI Companion"
2. Tap the microphone icon to start voice input
3. Speak your message clearly
4. The AI will respond both visually and audibly

### Exercise Coaching

1. Navigate to "Exercise Chatbot"
2. Select an exercise type
3. Use "Guided Camera Exercise" for real-time feedback
4. Follow AI-generated instructions

### Setting Reminders

1. From the home screen, tap "Pick Time"
2. Select your preferred reminder time
3. Grant notification permissions when prompted
4. Reminders will be delivered daily

## Development Notes

### Camera Testing

- **Physical Device**: Full camera functionality with pose detection
- **Emulator**: Test frames and simulated analysis
- **Permissions**: Camera and microphone permissions required

### API Key Management

- Development: Use `local.properties`
- Production: Use Firebase Remote Config
- Fallback: Graceful degradation if services unavailable

### Voice Recognition

- Requires `RECORD_AUDIO` permission
- Supports multiple languages
- Automatic error handling and retry logic

## Troubleshooting

### Common Issues

#### Camera Not Working

- Check camera permissions
- Verify device has camera hardware
- In emulator, use "Test Frame" button

#### Voice Recognition Fails

- Ensure microphone permission granted
- Check device microphone functionality
- Try speaking more clearly

#### API Errors

- Verify API key configuration
- Check internet connectivity
- Review Firebase configuration (if using)

### Debug Mode

Enable debug logging by setting:

```kotlin
Log.d("MainActivity", "Debug message")
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support and questions:

- Create an issue in the repository
- Check the troubleshooting section
- Review the Firebase documentation

## Roadmap

- [ ] Enhanced pose detection accuracy
- [ ] Multi-language support
- [ ] Offline mode capabilities
- [ ] Integration with health monitoring devices
- [ ] Advanced exercise routines
- [ ] Social features for family members
