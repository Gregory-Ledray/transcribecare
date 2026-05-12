import cors from "cors";
import dotenv from "dotenv";
import express from "express";
import fs from "fs";
import os from "os";
import path from "path";
import { fileURLToPath } from "url";

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = parseInt(process.env.PORT || "8080", 10);

// Path to the model file in ~/Documents
const MODEL_FILE_PATH = path.join(
  os.homedir(),
  "Documents",
  "gemma-4-E2B-it.litertlm"
);

// Enable CORS for allowed origins
app.use(cors({
  origin: [
    "http://localhost:3000",
    "https://transcribecare.com",
    "https://treatcost.com",
    "http://localhost:3001",
  ],
}));

// --- Model file API endpoint ---
// Serves the Gemma model file with range request support for efficient streaming
app.get("/models/gemma-4-E2B-it.litertlm", (req, res) => {
  if (!fs.existsSync(MODEL_FILE_PATH)) {
    res.status(404).json({ error: "Model file not found in ~/Documents" });
    return;
  }

  const stat = fs.statSync(MODEL_FILE_PATH);
  const fileSize = stat.size;

  // Handle range requests (partial content)
  const range = req.headers.range;
  if (range) {
    const parts = range.replace(/bytes=/, "").split("-");
    const start = parseInt(parts[0], 10);
    const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;
    const chunkSize = end - start + 1;

    const stream = fs.createReadStream(MODEL_FILE_PATH, { start, end });
    res.writeHead(206, {
      "Content-Range": `bytes ${start}-${end}/${fileSize}`,
      "Accept-Ranges": "bytes",
      "Content-Length": chunkSize,
      "Content-Type": "application/octet-stream",
    });
    stream.pipe(res);
  } else {
    res.writeHead(200, {
      "Content-Length": fileSize,
      "Content-Type": "application/octet-stream",
      "Accept-Ranges": "bytes",
    });
    fs.createReadStream(MODEL_FILE_PATH).pipe(res);
  }
});

// Serve static files from the built dist directory
app.use(express.static(path.join(__dirname, "dist")));

// SPA fallback — serve index.html for all non-file routes
app.get("*", (_req, res) => {
  res.sendFile(path.join(__dirname, "dist", "index.html"));
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`TranscribeCare web server running on port ${PORT}`);
  console.log(`Model API: http://localhost:${PORT}/models/gemma-4-E2B-it.litertlm`);
});
