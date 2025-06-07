# Video Transcription Batch Processor

A Java-based application that automatically processes video files to extract audio, generate transcripts using OpenAI's Whisper model, and create summaries using AI models. The system processes entire directory trees and maintains the original folder structure in the output.

## Features

- **Batch Processing**: Process entire directories of videos recursively
- **Multiple Format Support**: Handles common video formats (MP4, AVI, MKV, MOV, WMV, FLV, WebM, M4V, 3GP, MPG, MPEG)
- **Audio Extraction**: Uses FFmpeg to extract high-quality audio from videos
- **AI Transcription**: Leverages OpenAI's Whisper model for accurate speech-to-text
- **Text Summarization**: Generates concise summaries using Facebook's BART model
- **Directory Structure Preservation**: Maintains original folder hierarchy in output
- **Error Handling**: Robust error handling with detailed logging

## Architecture

The system consists of two main components:

1. **Java Application** (`App.java`): Main batch processor that handles file operations, audio extraction, and API communication
2. **Flask API Server** (`server.py`): Provides transcription and summarization endpoints using AI models

## Prerequisites

### System Requirements
- Java 11 or higher
- Python 3.7 or higher
- FFmpeg installed and accessible in PATH

### Java Dependencies
- OkHttp3 (HTTP client)
- org.json (JSON processing)

### Python Dependencies
```bash
pip install flask transformers whisper-openai torch
```

## Installation

### 1. Install FFmpeg
```bash
# Ubuntu/Debian
sudo apt update && sudo apt install ffmpeg

# macOS
brew install ffmpeg

# Windows
# Download from https://ffmpeg.org/download.html
```

### 2. Install Python Dependencies
```bash
pip install flask transformers whisper-openai torch
```

### 3. Set up Java Dependencies
Add these dependencies to your `pom.xml`

**Maven:**
```xml
<dependencies>
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>
        <version>4.12.0</version>
    </dependency>
    <dependency>
        <groupId>org.json</groupId>
        <artifactId>json</artifactId>
        <version>20231013</version>
    </dependency>
</dependencies>
```


## Usage

### 1. Start the Flask API Server
```bash
python server.py
```
The server will start on `http://localhost:5000`

### 2. Configure and Run the Java Application

#### Modify the source code
Edit the paths in `App.java`:
```java
String inputDirectory = "/path/to/your/videos";
String outputDirectory = "/path/to/your/transcripts";
```
#### Run using maeven
```bash
mvn clean compile
mvn exec:java
```


### 3. Processing Results

The application will create the following structure:
```
output_directory/
├── subfolder/
│   ├── video1_transcript.txt
│   └── video1_summary.txt
├── subfolder/
│   ├── video2_transcript.txt
│   └── video2_summary.txt
└── ...
```


## API Endpoints

### POST /transcribe
Transcribes audio files to text using Whisper.

**Request:**
- Content-Type: `multipart/form-data`
- File: Audio file (WAV format recommended)

**Response:**
```json
{
    "text": "Transcribed text content..."
}
```

### POST /summarize
Generates summaries from text using BART.

**Request:**
- Content-Type: `application/json`
- Body: `{"text": "Text to summarize..."}`

**Response:**
```json
{
    "summary": "Generated summary..."
}
```

## Configuration

### Whisper Model Selection
In `server.py`, you can change the Whisper model:
```python
model = whisper.load_model("base")  # Options: tiny, base, small, medium, large
```

Model trade-offs:
- **tiny**: Fastest, least accurate
- **base**: Good balance of speed and accuracy
- **small**: Better accuracy, slower
- **medium**: High accuracy, much slower
- **large**: Best accuracy, very slow

### FFmpeg Audio Settings
The application extracts audio with these settings:
- Format: WAV (PCM 16-bit)
- Sample Rate: 16kHz
- Channels: Mono
- Codec: `pcm_s16le`

Modify these in the `extractAudio` method if needed.

### Timeout Configuration
HTTP timeouts can be adjusted in the Java code:
```java
OkHttpClient client = new OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .readTimeout(300, TimeUnit.SECONDS)  // 5 minutes for transcription
    .build();
```





## Supported Video Formats

- MP4
- AVI
- MKV
- MOV
- WMV
- FLV
- WebM
- M4V
- 3GP
- MPG
- MPEG

## Output Format

### Transcript Files
```
Video: example_video.mp4
Processed: 2025-06-07T10:30:45.123

=== TRANSCRIPT ===
[Full transcribed text content...]
```

### Summary Files
```
Video: example_video.mp4
Processed: 2025-06-07T10:30:45.123

=== SUMMARY ===
[AI-generated summary...]
```

