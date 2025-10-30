# Firebase Chat Service Setup Guide

This guide explains how to set up and use the Firebase-based chat service in your Elderly Care App.

## What Changed

The app now uses **Firebase Cloud Functions** instead of calling Google's Gemini API directly from the Android app. This provides:

- ✅ **Better cost control** - API calls go through Firebase
- ✅ **Conversation context** - AI remembers previous messages
- ✅ **Message batching** - Multiple messages processed together
- ✅ **Offline support** - Messages stored in Firestore
- ✅ **Rate limiting** - Controlled through Firebase
- ✅ **Fallback support** - Falls back to direct API if Firebase fails

## Setup Steps

### 1. Enable Firebase Services

Make sure these Firebase services are enabled in your Firebase Console:

- **Cloud Functions** - For handling AI chat requests
- **Firestore** - For storing conversation history
- **Authentication** (optional) - For user management

### 2. Deploy Cloud Functions

Navigate to the `functions/` directory and deploy:

```bash
cd functions
npm install
firebase deploy --only functions
```

### 3. Set Environment Variables

Set your Gemini API key in Firebase Functions:

```bash
firebase functions:config:set gemini.key="YOUR_GEMINI_API_KEY"
```

Or set it as an environment variable:

```bash
firebase functions:config:set gemini.key="$(echo $GEMINI_API_KEY)"
```

### 4. Update local.properties

Add your Firebase configuration to `local.properties`:

```properties
GEMINI_API_KEY=your_actual_gemini_api_key_here
```

## How It Works

### Before (Direct API Calls)

```
User Message → Android App → Gemini API → Response
(Each message = 1 API call)
```

### After (Firebase)

```
User Message → Android App → Firebase Function → Gemini API → Response
(Conversation context maintained, batching possible)
```

## Features

### 1. Conversation Context

- Maintains last 10 messages in memory
- Sends full conversation history to AI
- AI responds with context awareness

### 2. Message Batching

- Multiple messages can be sent together
- Reduces API calls and costs
- Better conversation flow

### 3. Persistent Storage

- Conversations saved to Firestore
- Survives app restarts
- Can be loaded later

### 4. Fallback Support

- If Firebase fails, falls back to direct Gemini API
- Ensures app always works
- Graceful degradation

## API Functions

### `chatWithAI`

- Single message processing
- Maintains conversation context
- Returns AI response

### `chatWithAIBatch`

- Multiple message processing
- Batch mode for efficiency
- Comprehensive responses

### `testConnection`

- Tests Firebase connectivity
- Verifies API key configuration
- Debugging tool

## Cost Benefits

### Before

- Each message = 1 API call
- No conversation context
- Higher costs for long conversations

### After

- Context maintained between calls
- Batching possible
- Better cost control through Firebase
- Rate limiting and monitoring

## Monitoring

Check Firebase Console for:

- Function execution logs
- Performance metrics
- Error rates
- Cost analysis

## Troubleshooting

### Common Issues

1. **"Service not initialized"**

   - Check Firebase configuration
   - Verify google-services.json is present

2. **"API key not configured"**

   - Set GEMINI_API_KEY in Firebase config
   - Check environment variables

3. **Function deployment fails**
   - Ensure you're in the functions directory
   - Check Node.js version compatibility

### Debug Commands

```bash
# Test Firebase connection
firebase functions:log

# Check configuration
firebase functions:config:get

# Test specific function
firebase functions:shell
```

## Migration Notes

- **Backward compatible** - Old code still works
- **Gradual rollout** - Can switch between services
- **No data loss** - Existing conversations preserved
- **Performance improved** - Better response times with context

## Next Steps

1. Deploy the Cloud Functions
2. Test the Firebase connection
3. Monitor costs and performance
4. Enable additional Firebase features as needed

## Support

If you encounter issues:

1. Check Firebase Console logs
2. Verify API key configuration
3. Test with the "Test Firebase" button in the app
4. Check function deployment status
