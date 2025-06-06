from flask import Flask, request, jsonify
from transformers import pipeline
import whisper
import os

app = Flask(__name__)

# Load model once globally
model = whisper.load_model("base")  # or "tiny", "small", "medium", "large"

# Load the summarization model once
summarizer = pipeline("summarization", model="facebook/bart-large-cnn")

@app.route("/transcribe", methods=["POST"])
def transcribe():
    if 'file' not in request.files:
        return jsonify({"error": "No file part"}), 400

    file = request.files['file']
    if file.filename == '':
        return jsonify({"error": "No selected file"}), 400

    # Save to a temporary location
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

    # Generate summary
    try:
        summary = summarizer(
            input_text,
            max_length=100,
            min_length=30,
            do_sample=False
        )[0]["summary_text"]
        return jsonify({'summary': summary})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == "__main__":
    app.run(debug=True)
