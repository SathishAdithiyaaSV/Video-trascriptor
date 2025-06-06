package com.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class App {
    private static final String FFMPEG_PATH = "ffmpeg"; // Assumes ffmpeg is in PATH

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

        // Optional: capture FFmpeg output for debugging
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[FFMPEG] " + line);
            }
        }

        if (process.waitFor() != 0) {
            throw new IOException("FFmpeg failed to extract audio.");
        }

        return audioFile;
    }

    public static String transcribeViaFlask(File wavFile) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)  // Time to establish connection
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)    // Time to send data
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)    // Time to wait for response (increase this)
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
            if (!response.isSuccessful()) throw new IOException("Flask API failed");

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
        String safeTranscript = transcript.replace("\"", "\\\"");
    
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
    

    public static void main(String[] args) {
        try {
            File videoFile = new File("/home/sathish/Documents/HCL/video-transcriber/java_lecture.mp4"); // 👈 change to your video path
            File audioFile = extractAudio(videoFile);
            String transcript = transcribeViaFlask(audioFile);
            System.out.println("Transcript:\n" + transcript);
            String summary = summarize(transcript);
            System.err.println("Summary:\n" + summary);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
