const functions = require("firebase-functions");
const axios = require("axios");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { setGlobalOptions } = require("firebase-functions/v2");
const { defineSecret } = require("firebase-functions/params");

// Set global options for cost control (v2)
setGlobalOptions({ maxInstances: 10 });

// Safe JSON stringify to avoid 'Converting circular structure to JSON' errors when logging
function safeStringify(obj, spaces = 2) {
  const cache = new Set();
  return JSON.stringify(obj, function (key, value) {
    if (typeof value === 'object' && value !== null) {
      if (cache.has(value)) return '[Circular]';
      cache.add(value);
    }
    return value;
  }, spaces);
}

// Secret for Gemini API key (Firebase Secret Manager)
const GEMINI_API_KEY = defineSecret("GEMINI_API_KEY");

// Get API key from Secret Manager, environment, or legacy config (in that priority)
const getApiKey = () => {
  try {
    // Try to get the secret value
    let secretValue = null;
    try {
      secretValue = GEMINI_API_KEY.value();
    } catch (secretError) {
      console.log('Could not access secret:', secretError.message);
    }
    
    return (
      secretValue ||
      process.env.GEMINI_API_KEY ||
      (functions.config && functions.config().gemini ? functions.config().gemini.key : undefined)
    );
  } catch (error) {
    console.log('Error in getApiKey:', error.message);
    return process.env.GEMINI_API_KEY || (functions.config && functions.config().gemini ? functions.config().gemini.key : undefined);
  }
};

// Main chat function with conversation context (v2 onCall)
exports.chatWithAI = onCall({ secrets: [GEMINI_API_KEY] }, async (request) => {
  const data = request?.data;
  const context = request; // preserve naming used below
  try {
    // Enhanced request logging
    console.log('=== Incoming Request ===');
    try {
      console.log('Full request data:', safeStringify(data, 2));
    } catch (e) {
      console.log('Full request data: [unserializable]');
    }
    console.log('Request data type:', typeof data);
    try {
      console.log('Request data keys:', data ? Object.keys(data) : 'null');
    } catch (e) {
      console.log('Request data keys: [unavailable]');
    }
    
    // Validate required fields
    if (!data) {
      console.error('No data provided in request');
      throw new HttpsError('invalid-argument', 'No data provided');
    }
  
    // Normalize payload in case some clients wrap under { data: ... }
    const incoming = (data && data.data && typeof data.data === 'object') ? data.data : data;
    try {
      console.log('Normalized incoming keys:', Object.keys(incoming));
    } catch (e) {}

    // Ensure required fields exist (support both direct and wrapped payloads)
    if (!incoming.messages) {
      console.error('No messages in request data (after normalization)');
      console.error('Incoming object:', safeStringify(incoming, 2));
      throw new HttpsError('invalid-argument', 'No messages provided');
    }
    
    const { messages, context: userContext, conversationId } = incoming;
    
    // Enhanced validation with better error messages
    if (!messages) {
      console.error('No messages array found in request data');
      console.error('Available keys in data:', Object.keys(data));
      throw new Error("No messages array found in request data");
    }
    
    if (!Array.isArray(messages)) {
      console.error('Messages is not an array:', {
        messagesType: typeof messages,
        messagesValue: messages,
        fullData: safeStringify(data, 2)
      });
      throw new Error("Invalid messages format: Expected an array of messages");
    }
    
    // Log detailed message structure
    console.log(`=== Processing ${messages.length} messages ===`);
    messages.forEach((msg, index) => {
      console.log(`Message ${index + 1}:`, {
        type: typeof msg,
        keys: Object.keys(msg),
        hasRole: 'role' in msg,
        roleType: typeof msg.role,
        roleValue: msg.role,
        hasContent: 'content' in msg,
        contentType: typeof msg.content,
        hasParts: 'parts' in msg,
        partsType: Array.isArray(msg.parts) ? 'array' : typeof msg.parts,
        timestamp: 'timestamp' in msg ? msg.timestamp : 'not present'
      });
    });
    
    // More flexible message format validation
    const invalidMessages = [];
    const validMessages = [];
    
    for (const [index, msg] of messages.entries()) {
      const isValid = (
        msg && 
        typeof msg === 'object' && 
        msg.role && 
        typeof msg.role === 'string' &&
        (msg.content || msg.parts)
      );
      
      if (!isValid) {
        invalidMessages.push({ index, message: msg });
      } else {
        validMessages.push(msg);
      }
    }

    if (invalidMessages.length > 0) {
      console.error(`Found ${invalidMessages.length} invalid messages out of ${messages.length}`, invalidMessages);
      throw new Error(`Invalid message format: ${invalidMessages.length} invalid message(s) found`);
    }

    const apiKey = getApiKey();
    console.log('API Key status:', apiKey ? 'Found' : 'Not found');
    console.log('API Key length:', apiKey ? apiKey.length : 0);
    if (!apiKey) {
      throw new Error("API key not configured");
    }

    // Log the received messages for debugging
    try {
      console.log('Received messages:', safeStringify(messages, 2));
    } catch (e) {
      console.log('Received messages: [unserializable]');
    }
    
    // Process messages for the Gemini API
    const contents = messages.map((msg, index) => {
      try {
        // Ensure we have a valid message object
        if (!msg || typeof msg !== 'object') {
          throw new Error(`Message at index ${index} is not an object`);
        }
        
        // Get role with validation
        const role = msg.role === 'user' ? 'user' : 'model';
        
        // Extract content, handling different formats
        let content = '';
        if (msg.content && typeof msg.content === 'string') {
          content = msg.content;
        } else if (msg.parts && Array.isArray(msg.parts) && msg.parts[0]?.text) {
          content = msg.parts[0].text;
        } else if (msg.parts && Array.isArray(msg.parts) && msg.parts[0]?.parts?.[0]?.text) {
          content = msg.parts[0].parts[0].text;
        } else {
          try {
            console.warn(`No valid content found in message at index ${index}`, safeStringify(msg));
          } catch (e) {
            console.warn(`No valid content found in message at index ${index}`);
          }
        }
        
        return {
          role,
          parts: [{ text: content }]
        };
      } catch (error) {
        console.error(`Error processing message at index ${index}:`, error);
        throw new Error(`Error processing message at index ${index}: ${error.message}`);
      }
    });
    
    try {
      console.log('Formatted contents:', safeStringify(contents, 2));
    } catch (e) {
      console.log('Formatted contents: [unserializable]');
    }

    // Add the system context/instruction if provided
    if (userContext) {
      contents.unshift({ 
        role: 'user', 
        parts: [{ text: `System Instruction: ${userContext}` }] 
      });
      contents.push({ 
        role: 'model', 
        parts: [{ text: "Understood. I will follow the instructions." }] 
      });
    }

    console.log(`Processing chat request for conversation ${conversationId} with ${contents.length} content blocks`);

    let response;
    try {
      response = await axios.post(
        `https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=${apiKey}`,
        {
          contents: contents,
          generationConfig: {
            temperature: 0.7,
            maxOutputTokens: 2000,
          },
        },
        { headers: { "Content-Type": "application/json" } }
      );
    } catch (err) {
      const status = err?.response?.status;
      const errmsg = err?.response?.data?.error?.message || err?.message || 'Unknown error';
      try { console.error('Gemini API error payload:', safeStringify(err?.response?.data, 2)); } catch(e) {}
      throw new Error(`Gemini API request failed${status ? ` (status ${status})` : ''}: ${errmsg}`);
    }

    // Check for valid response with text content
    const candidate = response?.data?.candidates?.[0];
    if (candidate?.content?.parts?.[0]?.text) {
      const aiResponse = candidate.content.parts[0].text;
      console.log(`AI response generated for conversation ${conversationId}`);
      return aiResponse;
    } else if (candidate?.finishReason === "MAX_TOKENS") {
      // Handle case where response was truncated due to token limit
      console.log(`AI response truncated due to token limit for conversation ${conversationId}`);
      return "I'm sorry, my response was too long. Could you please ask a shorter question?";
    } else if (candidate?.finishReason === "SAFETY") {
      // Handle safety filter
      console.log(`AI response blocked by safety filter for conversation ${conversationId}`);
      return "I'm sorry, I can't provide a response to that question for safety reasons.";
    } else if (candidate?.finishReason === "STOP") {
      // Handle case where response was stopped but no text content
      console.log(`AI response stopped without content for conversation ${conversationId}`);
      return "I'm sorry, I couldn't generate a response to that. Could you please try rephrasing your question?";
    } else {
      try { console.error('Gemini unexpected response:', safeStringify(response?.data, 2)); } catch(e) {}
      throw new Error("Invalid response format from AI service");
    }

  } catch (error) {
    // Avoid serializing the entire error object which can contain circular refs
    console.error("Error in chatWithAI: message=", error && error.message ? error.message : String(error));
    if (error && error.stack) console.error(error.stack);
    throw new HttpsError(
      "internal",
      `Chat service error: ${error && error.message ? error.message : 'Unknown error'}`
    );
  }
});

// Batched chat function for multiple messages (v2 onCall)
exports.chatWithAIBatch = onCall({ secrets: [GEMINI_API_KEY] }, async (request) => {
  const data = request?.data;
  const context = request;
  try {
    const { messages, context: userContext, conversationId, batchMode } = data;
    
    if (!messages || !Array.isArray(messages) || !batchMode) {
      throw new Error("Invalid batch request format");
    }

    const apiKey = getApiKey();
    if (!apiKey) {
      throw new Error("API key not configured");
    }

    // For batching, we'll process all messages together
    let batchPrompt = "";
    if (userContext) {
      batchPrompt += `Context: ${userContext}\n\n`;
    }
    
    batchPrompt += "Multiple user messages to respond to:\n";
    messages.forEach((msg, index) => {
      if (msg.role === "user") {
        batchPrompt += `Message ${index + 1}: ${msg.content}\n`;
      }
    });
    
    batchPrompt += "\nPlease provide a comprehensive response that addresses all the user messages while maintaining conversation flow.";

    console.log(`Processing batched chat request for conversation ${conversationId} with ${messages.length} messages`);

    const response = await axios.post(
      `https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=${apiKey}`,
      {
        contents: [
          {
            parts: [{ text: batchPrompt }],
          },
        ],
        generationConfig: {
          temperature: 0.7,
          maxOutputTokens: 2000,
        },
      },
      { headers: { "Content-Type": "application/json" } }
    );

    const candidate = response.data?.candidates?.[0];
    if (candidate?.content?.parts?.[0]?.text) {
      const aiResponse = candidate.content.parts[0].text;
      console.log(`Batched AI response generated for conversation ${conversationId}`);
      return aiResponse;
    } else if (candidate?.finishReason === "MAX_TOKENS") {
      console.log(`Batched AI response truncated due to token limit for conversation ${conversationId}`);
      return "I'm sorry, my response was too long. Could you please ask shorter questions?";
    } else if (candidate?.finishReason === "SAFETY") {
      console.log(`Batched AI response blocked by safety filter for conversation ${conversationId}`);
      return "I'm sorry, I can't provide a response to that question for safety reasons.";
    } else if (candidate?.finishReason === "STOP") {
      console.log(`Batched AI response stopped without content for conversation ${conversationId}`);
      return "I'm sorry, I couldn't generate a response to that. Could you please try rephrasing your questions?";
    } else {
      throw new Error("Invalid response format from AI service");
    }

  } catch (error) {
    console.error("Error in chatWithAIBatch: message=", error && error.message ? error.message : String(error));
    if (error && error.stack) console.error(error.stack);
    throw new HttpsError(
      "internal",
      `Batched chat service error: ${error && error.message ? error.message : 'Unknown error'}`
    );
  }
});

// Test connection function (v2 onCall)
exports.testConnection = onCall(async (request) => {
  try {
    const apiKey = getApiKey();
    if (!apiKey) {
      return "Error: API key not configured";
    }

    // Test the API connection
    const response = await axios.post(
      `https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=${apiKey}`,
      {
        contents: [
          {
            parts: [{ text: "Hello, this is a test message." }],
          },
        ],
        generationConfig: {
          maxOutputTokens: 50,
        },
      },
      { headers: { "Content-Type": "application/json" } }
    );

    if (response.status === 200) {
      return "Firebase Cloud Functions connection successful! AI service is responding.";
    } else {
      return `Connection test failed with status: ${response.status}`;
    }

  } catch (error) {
    console.error("Error in testConnection: message=", error && error.message ? error.message : String(error));
    if (error && error.stack) console.error(error.stack);
    return `Connection test error: ${error && error.message ? error.message : 'Unknown error'}`;
  }
});

// Legacy function for backward compatibility
exports.geminiChat = functions.https.onRequest(async (req, res) => {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") {
    res.status(204).send("");
    return;
  }

  const prompt = req.body.prompt;
  const apiKey = getApiKey();

  if (!apiKey) {
    res.status(500).json({error: "API key not configured"});
    return;
  }

  try {
    const response = await axios.post(
        `https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=${apiKey}`,
        {
          contents: [
            {
              parts: [{text: prompt}],
            },
          ],
        },
        {headers: {"Content-Type": "application/json"}},
    );
    res.json(response.data);
  } catch (err) {
    res.status(500).json({error: err.toString()});
  }
});
