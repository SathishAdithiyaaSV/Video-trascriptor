package com.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class App {
    private static final String FFMPEG_PATH = "ffmpeg";
    
    // Common video file extensions
    private static final List<String> VIDEO_EXTENSIONS = Arrays.asList(
        "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "3gp", "mpg", "mpeg"
    );

    public static File extractAudio(File videoFile) throws IOException, InterruptedException {
        String audioPath = videoFile.getAbsolutePath().replaceAll("\\.\\w+$", ".wav");
        File audioFile = new File(audioPath);

        ProcessBuilder pb = new ProcessBuilder(
            FFMPEG_PATH,
            "-i", videoFile.getAbsolutePath(),
            "-vn",
            "-acodec", "pcm_s16le",
            "-ar", "16000",
            "-ac", "1",
            audioPath
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[FFMPEG] " + line);
            }
        }

        if (process.waitFor() != 0) {
            throw new IOException("FFmpeg failed to extract audio for: " + videoFile.getName());
        }

        return audioFile;
    }

    public static String transcribeViaFlask(File wavFile) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .build();

        RequestBody requestBody = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", wavFile.getName(),
                RequestBody.create(wavFile, MediaType.parse("audio/wav")))
            .build();

        Request request = new Request.Builder()
            .url("http://localhost:5000/transcribe")
            .post(requestBody)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Flask API failed for: " + wavFile.getName());
            }

            JSONObject json = new JSONObject(response.body().string());
            return json.getString("text");
        }
    }

    public static String summarize(String transcript) {
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
        // Escape double quotes in the transcript
        String safeTranscript = transcript.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    
        String jsonPayload = "{\"text\": \"" + safeTranscript + "\"}";
    
        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
            .url("http://localhost:5000/summarize")
            .post(body)
            .build();
    
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
    
            String responseBody = response.body().string();
            JSONObject json = new JSONObject(responseBody);
            return json.getString("summary");
        } catch (Exception e) {
            e.printStackTrace();
            return "Summary generation failed: " + e.getMessage();
        }
    }

    public static boolean isVideoFile(File file) {
        if (!file.isFile()) return false;
        
        String fileName = file.getName().toLowerCase();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) return false;
        
        String extension = fileName.substring(lastDot + 1);
        return VIDEO_EXTENSIONS.contains(extension);
    }

    public static void processVideo(File videoFile, String outputDir) {
        System.out.println("\n=== Processing: " + videoFile.getAbsolutePath() + " ===");
        
        try {
            // Extract audio
            System.out.println("Extracting audio...");
            File audioFile = extractAudio(videoFile);
            
            // Transcribe
            System.out.println("Transcribing audio...");
            String transcript = transcribeViaFlask(audioFile);
            
            // Summarize
            System.out.println("Generating summary...");
            String summary = summarize(transcript);
            
            // Save results
            String baseFileName = videoFile.getName().replaceAll("\\.\\w+$", "");
            
            // Create output directory structure
            String relativePath = getRelativePath(videoFile);
            File outputSubDir = new File(outputDir, relativePath);
            outputSubDir.mkdirs();
            
            // Save transcript
            File transcriptFile = new File(outputSubDir, baseFileName + "_transcript.txt");
            try (FileWriter writer = new FileWriter(transcriptFile)) {
                writer.write("Video: " + videoFile.getName() + "\n");
                writer.write("Processed: " + java.time.LocalDateTime.now() + "\n\n");
                writer.write("=== TRANSCRIPT ===\n");
                writer.write(transcript);
            }
            
            // Save summary
            File summaryFile = new File(outputSubDir, baseFileName + "_summary.txt");
            try (FileWriter writer = new FileWriter(summaryFile)) {
                writer.write("Video: " + videoFile.getName() + "\n");
                writer.write("Processed: " + java.time.LocalDateTime.now() + "\n\n");
                writer.write("=== SUMMARY ===\n");
                writer.write(summary);
            }
            
            // Clean up audio file
            if (audioFile.exists()) {
                audioFile.delete();
            }
            
            System.out.println("✓ Completed: " + videoFile.getName());
            System.out.println("  Transcript saved: " + transcriptFile.getAbsolutePath());
            System.out.println("  Summary saved: " + summaryFile.getAbsolutePath());
            
        } catch (Exception e) {
            System.err.println("✗ Failed to process: " + videoFile.getName());
            System.err.println("  Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getRelativePath(File videoFile) {
        try {
            Path videoPath = videoFile.toPath().getParent();
            Path basePath = Paths.get(System.getProperty("user.dir"));
            return basePath.relativize(videoPath).toString();
        } catch (Exception e) {
            return ""; // fallback to root output directory
        }
    }

    public static void processDirectory(File directory, String outputDir) {
        if (!directory.exists() || !directory.isDirectory()) {
            System.err.println("Invalid directory: " + directory.getAbsolutePath());
            return;
        }

        System.out.println("Scanning directory: " + directory.getAbsolutePath());
        
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.filter(Files::isRegularFile)
                 .map(Path::toFile)
                 .filter(App::isVideoFile)
                 .forEach(videoFile -> processVideo(videoFile, outputDir));
        } catch (IOException e) {
            System.err.println("Error scanning directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Configuration
        String inputDirectory = "/home/sathish/Documents/HCL/video-transcriber/videos";
        String outputDirectory = "/home/sathish/Documents/HCL/video-transcriber/transcripts";
        
        // Create output directory if it doesn't exist
        new File(outputDirectory).mkdirs();
        
        System.out.println("Video Transcription Batch Processor");
        System.out.println("Input Directory: " + inputDirectory);
        System.out.println("Output Directory: " + outputDirectory);
        System.out.println("Supported formats: " + VIDEO_EXTENSIONS);
        
        File inputDir = new File(inputDirectory);
        
        if (args.length > 0) {
            // If command line argument provided, use it as input directory
            inputDir = new File(args[0]);
        }
        
        if (args.length > 1) {
            // If second argument provided, use it as output directory
            outputDirectory = args[1];
            new File(outputDirectory).mkdirs();
        }
        
        // Process all videos in the directory tree
        processDirectory(inputDir, outputDirectory);
        
        System.out.println("\n=== Processing Complete ===");
    }
}