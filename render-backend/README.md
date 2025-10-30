# Elderly Care Backend

A simple Express.js backend that proxies requests to the Gemini API for the Elderly Care Android app.

## Setup

1. **Clone this repository**
2. **Set environment variables in Render:**
   - `GEMINI_API_KEY`: Your Gemini API key

## Deploy to Render

1. **Connect your GitHub repository to Render**
2. **Create a new Web Service**
3. **Set the following:**
   - **Build Command:** `npm install`
   - **Start Command:** `npm start`
   - **Environment Variable:** `GEMINI_API_KEY` = your actual API key

## API Endpoints

- `POST /chat` - Send a prompt to Gemini API

  - Body: `{ "prompt": "your message here" }`
  - Returns: Gemini API response

- `GET /health` - Health check endpoint

## Local Development

1. **Install dependencies:**

   ```bash
   npm install
   ```

2. **Set environment variable:**

   ```bash
   export GEMINI_API_KEY=your_api_key_here
   ```

3. **Run the server:**
   ```bash
   npm start
   ```

The server will be available at `http://localhost:3000`
