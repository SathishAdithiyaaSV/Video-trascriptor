from flask import Flask, request, jsonify
from transformers import pipeline
import whisper
import os
import re

app = Flask(__name__)

# Load Whisper model once globally
model = whisper.load_model("base")

# Load Summarizer model once
summarizer = pipeline("summarization", model="facebook/bart-large-cnn")

def split_text_into_chunks(text, max_words=500):
    sentences = re.split(r'(?<=[.!?]) +', text)
    chunks = []
    current_chunk = []

    current_length = 0
    for sentence in sentences:
        words_in_sentence = len(sentence.split())
        if current_length + words_in_sentence <= max_words:
            current_chunk.append(sentence)
            current_length += words_in_sentence
        else:
            chunks.append(' '.join(current_chunk))
            current_chunk = [sentence]
            current_length = words_in_sentence
    if current_chunk:
        chunks.append(' '.join(current_chunk))
    return chunks

@app.route("/transcribe", methods=["POST"])
def transcribe():
    if 'file' not in request.files:
        return jsonify({"error": "No file part"}), 400

    file = request.files['file']
    if file.filename == '':
        return jsonify({"error": "No selected file"}), 400

    temp_path = os.path.join("temp_audio.wav")
    file.save(temp_path)

    try:
        result = model.transcribe(temp_path)
        print(result["text"])
        return jsonify({"text": result["text"]})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        os.remove(temp_path)

@app.route('/summarize', methods=['POST'])
def summarize():
    data = request.get_json()
    if not data or 'text' not in data:
        return jsonify({'error': 'Please provide text to summarize'}), 400

    input_text = data['text']

    try:
        chunks = split_text_into_chunks(input_text, max_words=500)
        summaries = []
        for chunk in chunks:
            summary = summarizer(chunk, max_length=100, min_length=30, do_sample=False)[0]['summary_text']
            summaries.append(summary)
        # Optionally: summarize the concatenated summaries again
        final_summary = ' '.join(summaries)
        return jsonify({'summary': final_summary})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == "__main__":
    app.run(debug=True)
