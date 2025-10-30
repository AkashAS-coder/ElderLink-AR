# Firebase Setup Guide

This guide will help you set up Firebase for secure API key management in production.

## Prerequisites

- Google account
- Android project with Google Services plugin
- Gemini API key

## Step 1: Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Create a project" or "Add project"
3. Enter a project name (e.g., "ElderlyCareApp")
4. Choose whether to enable Google Analytics (recommended)
5. Click "Create project"

## Step 2: Add Android App

1. In your Firebase project, click the Android icon
2. Enter your package name: `com.example.elderlycareapp`
3. Enter app nickname (optional)
4. Click "Register app"
5. Download `google-services.json`
6. Place `google-services.json` in your `app/` directory

## Step 3: Configure Remote Config

1. In Firebase Console, go to "Remote Config" in the left sidebar
2. Click "Get started" if you haven't used Remote Config before
3. Click "Add your first parameter"
4. Set parameter key: `gemini_api_key`
5. Set parameter value: Your actual Gemini API key
6. Set description: "Gemini AI API key for elderly care app"
7. Click "Save"

## Step 4: Set Up Security Rules

1. Go to "Project settings" (gear icon)
2. Click "Service accounts" tab
3. Click "Firebase Admin SDK"
4. Generate new private key if needed
5. Download the JSON file (keep it secure)

## Step 5: Test Configuration

1. Build and run your app
2. Check logs for "Firebase Remote Config initialized successfully"
3. Verify API calls work with Firebase-provided key
4. Test in both debug and release builds

## Security Best Practices

### API Key Rotation

- Change your Gemini API key regularly
- Update Firebase Remote Config immediately
- Monitor API usage for anomalies

### Access Control

- Limit Firebase project access to necessary team members
- Use Firebase App Check for additional security
- Monitor authentication logs

### Environment Separation

- Use different Firebase projects for dev/staging/prod
- Configure different API keys per environment
- Test configuration changes in staging first

## Troubleshooting

### Common Issues

#### App Crashes on Startup

- Verify `google-services.json` is in correct location
- Check package name matches exactly
- Ensure Google Services plugin is applied

#### Remote Config Not Loading

- Check internet connectivity
- Verify Firebase project is active
- Check parameter key spelling

#### API Key Not Working

- Verify parameter value in Firebase Console
- Check parameter key matches code exactly
- Ensure parameter is published (not draft)

### Debug Steps

1. Enable Firebase debug logging:

```kotlin
FirebaseRemoteConfig.getInstance().setConfigSettingsAsync(
    FirebaseRemoteConfigSettings.Builder()
        .setMinimumFetchIntervalInSeconds(0) // For testing only
        .build()
)
```

2. Check Firebase initialization in logs:

```bash
adb logcat | grep "Firebase"
```

3. Verify Remote Config values:

```kotlin
val config = FirebaseRemoteConfig.getInstance()
Log.d("Firebase", "API Key: ${config.getString("gemini_api_key")}")
```

## Production Checklist

- [ ] Firebase project created and configured
- [ ] `google-services.json` added to app directory
- [ ] Remote Config parameter set with production API key
- [ ] Security rules configured
- [ ] App tested with Firebase configuration
- [ ] Team access permissions set appropriately
- [ ] Monitoring and alerting configured
- [ ] Backup API key management plan in place

## Support Resources

- [Firebase Documentation](https://firebase.google.com/docs)
- [Remote Config Guide](https://firebase.google.com/docs/remote-config)
- [Android Setup](https://firebase.google.com/docs/android/setup)
- [Firebase Console](https://console.firebase.google.com/)

## Next Steps

After setting up Firebase:

1. Configure additional Remote Config parameters as needed
2. Set up Firebase Analytics for usage insights
3. Implement Firebase Crashlytics for error monitoring
4. Consider Firebase Performance Monitoring
5. Set up automated deployment pipelines
