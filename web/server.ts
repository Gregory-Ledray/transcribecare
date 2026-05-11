import cors from "cors";
import dotenv from "dotenv";
import express from "express";
import path from "path";
import { fileURLToPath } from "url";

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = parseInt(process.env.PORT || "8080", 10);

// Enable CORS for allowed origins
app.use(cors({
  origin: [
    "http://localhost:3000",
    "https://transcribecare.com",
    "https://treatcost.com",
  ],
}));

// Serve static files from the built dist directory
app.use(express.static(path.join(__dirname, "dist")));

// SPA fallback — serve index.html for all non-file routes
app.get("*", (_req, res) => {
  res.sendFile(path.join(__dirname, "dist", "index.html"));
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`TranscribeCare web server running on port ${PORT}`);
});
