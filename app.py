from flask import Flask, request, jsonify
from groq import Groq
import pdfplumber
import json
import os
from datetime import datetime
import traceback
from dotenv import load_dotenv
import mysql.connector
from werkzeug.security import generate_password_hash, check_password_hash

# Load environment variables
load_dotenv()

app = Flask(__name__)
app.secret_key = os.environ.get("SECRET_KEY", "dev-secret")

# ✅ GROQ API KEY (Loaded from .env)
client = Groq(api_key=os.environ.get("GROQ_API_KEY"))

# 💾 Database Setup
def get_db_connection(include_db=True):
    return mysql.connector.connect(
        host=os.environ.get("DB_HOST", "localhost"),
        user=os.environ.get("DB_USER", "root"),
        password=os.environ.get("DB_PASSWORD", ""),
        database=os.environ.get("DB_NAME", "daily_learn") if include_db else None
    )

def init_db():
    try:
        # Create DB if not exists
        conn = get_db_connection(include_db=False)
        cursor = conn.cursor()
        cursor.execute(f"CREATE DATABASE IF NOT EXISTS `{os.environ.get('DB_NAME', 'daily_learn')}`")
        conn.close()

        # Create Tables
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # Users table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                email VARCHAR(100),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        
        # Study Plans table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS study_plans (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id INT NOT NULL,
                plan_data LONGTEXT NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """)
        
        conn.commit()
        conn.close()
        print("[DB] Database initialized successfully")
    except Exception as e:
        print(f"[DB ERROR] Database error: {e}")
        print("Check if XAMPP MySQL is running and your .env credentials are correct.")

# Run DB Init (Called in main)

# ─── Auth Helper Functions ──────────────────────────────────────────
def get_user_plan(user_id):
    try:
        conn = get_db_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute("SELECT plan_data FROM study_plans WHERE user_id = %s", (user_id,))
        row = cursor.fetchone()
        conn.close()
        if row:
            return json.loads(row['plan_data'])
    except Exception as e:
        print(f"Error fetching plan: {e}")
    return None

def save_user_plan(user_id, plan_data):
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        # Check if plan exists
        cursor.execute("SELECT id FROM study_plans WHERE user_id = %s", (user_id,))
        row = cursor.fetchone()
        
        json_data = json.dumps(plan_data)
        if row:
            cursor.execute("UPDATE study_plans SET plan_data = %s WHERE user_id = %s", (json_data, user_id))
        else:
            cursor.execute("INSERT INTO study_plans (user_id, plan_data) VALUES (%s, %s)", (user_id, json_data))
        
        conn.commit()
        conn.close()
        return True
    except Exception as e:
        print(f"Error saving plan: {e}")
    return False

# ─── AUTH ROUTES ─────────────────────────────────────────────────────
@app.route('/register', methods=['POST'])
def register():
    data = request.json
    username = data.get('username')
    password = data.get('password')
    email = data.get('email', '')

    if not username or not password:
        return jsonify({"error": "Username and password required"}), 400

    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # Check if user exists
        cursor.execute("SELECT id FROM users WHERE username = %s", (username,))
        if cursor.fetchone():
            return jsonify({"error": "Username already exists"}), 409
            
        hashed_pw = generate_password_hash(password)
        cursor.execute("INSERT INTO users (username, password_hash, email) VALUES (%s, %s, %s)", 
                       (username, hashed_pw, email))
        conn.commit()
        user_id = cursor.lastrowid
        conn.close()
        
        return jsonify({"success": True, "user_id": user_id, "username": username})
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/login', methods=['POST'])
def login():
    data = request.json
    username = data.get('username')
    password = data.get('password')

    if not username or not password:
        return jsonify({"error": "Username and password required"}), 400

    try:
        conn = get_db_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute("SELECT * FROM users WHERE username = %s", (username,))
        user = cursor.fetchone()
        conn.close()

        if user and check_password_hash(user['password_hash'], password):
            return jsonify({
                "success": True, 
                "user_id": user['id'], 
                "username": user['username']
            })
        
        return jsonify({"error": "Invalid username or password"}), 401
    except Exception as e:
        return jsonify({"error": str(e)}), 500


# ─── ROUTE 0: Check if plan exists ───────────────────────────────
@app.route('/check-plan', methods=['GET'])
def check_plan():
    user_id = request.args.get('user_id')
    if not user_id:
        return jsonify({"error": "User ID required"}), 400
        
    plan = get_user_plan(user_id)
    if not plan:
        return jsonify({"has_plan": False})
        
    return jsonify({
        "has_plan": True,
        "total_topics": len(plan.get("topics", [])),
        "current_day": plan.get("current_day_index", 0)
    })


# ─── ROUTE 1: Upload PDF/Image and extract topics ────────────────
@app.route('/upload', methods=['POST'])
def upload():
    try:
        # 1. Get binary data
        filename = "temp_file"
        if request.files and 'pdf' in request.files:
            file = request.files['pdf']
            data = file.read()
            filename = file.filename
        else:
            data = request.data

        if not data:
            return jsonify({"error": "No data received"}), 400

        # Determine file type
        is_pdf = data.startswith(b"%PDF")
        is_image = data.startswith(b"\xff\xd8") or data.startswith(b"\x89PNG")
        
        save_path = "temp.pdf" if is_pdf else "temp.img"
        with open(save_path, "wb") as f:
            f.write(data)

        text = ""
        
        # 3. Extraction Logic
        if is_pdf:
            try:
                with pdfplumber.open(save_path) as pdf:
                    for page in pdf.pages:
                        extracted = page.extract_text()
                        if extracted:
                            text += extracted + "\n"
            except Exception as e:
                print(f"PDF Error: {e}")
        
        if not text and not is_image:
            # Fallback to plain text
            try:
                text = data.decode('utf-8', errors='ignore')
            except:
                pass

        import base64

        # 4. Use AI (Vision for images, Text for others)
        if is_image:
            # Encode image to base64
            base64_image = base64.b64encode(data).decode('utf-8')
            mime_type = "image/png" if data.startswith(b"\x89PNG") else "image/jpeg"
            
            # Use Groq Vision Model
            prompt = """Extract all study topics from this syllabus image.
Return ONLY valid JSON, no extra text:
{
  "topics": ["Topic 1", "Topic 2", ...]
}"""
            response = client.chat.completions.create(
                model="llama-3.2-11b-vision-preview",
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": prompt},
                            {
                                "type": "image_url",
                                "image_url": {
                                    "url": f"data:{mime_type};base64,{base64_image}"
                                }
                            }
                        ]
                    }
                ]
            )
        else:
            if not text or len(text.strip()) < 5:
                return jsonify({"error": "Could not read file content. Please ensure it's a clear PDF or text file."}), 400

            prompt = f"""Extract all study topics from this syllabus.
Return ONLY valid JSON, no extra text:
{{
  "topics": ["Topic 1", "Topic 2", ...]
}}

Content:
{text[:5000]}"""

            response = client.chat.completions.create(
                model="llama-3.3-70b-versatile",
                messages=[{"role": "user", "content": prompt}]
            )
        
        raw = response.choices[0].message.content.strip()
        if "```" in raw:
            raw = raw.split("```")[1]
            if raw.startswith("json"): raw = raw[4:]

        return jsonify(json.loads(raw.strip()))
    
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


# ─── ROUTE 2: Explain a single topic ─────────────────────────────
@app.route('/explain-topic', methods=['GET'])
def explain_topic():
    topic = request.args.get('topic', '')
    prompt = f"""Explain this university topic for exam prep.
Return ONLY valid JSON:
{{
  "summary": "overview",
  "key_points": ["point1", "point2", ...],
  "exam_tip": "tip",
  "estimated_time": "X hours"
}}
Topic: {topic}"""

    response = client.chat.completions.create(
        model="llama-3.3-70b-versatile",
        messages=[{"role": "user", "content": prompt}]
    )
    raw = response.choices[0].message.content.strip()
    if "```" in raw:
        raw = raw.split("```")[1]
        if raw.startswith("json"): raw = raw[4:]
    return jsonify(json.loads(raw.strip()))


# ─── ROUTE 3: Initialize study plan ────────────────────────────────
@app.route('/init-study-plan', methods=['POST'])
def init_study_plan():
    data = request.json
    user_id = data.get('user_id')
    if not user_id:
        return jsonify({"error": "User ID required"}), 400
        
    new_plan = {
        "topics": data.get('topics', []),
        "start_date": datetime.now().isoformat(),
        "current_day_index": 0,
        "completed_history": [],
        "cached_explanations": {},
        "chat_history": {}
    }
    
    if save_user_plan(user_id, new_plan):
        return jsonify({"success": True, "total": len(new_plan["topics"])})
    return jsonify({"error": "Failed to save plan"}), 500


# ─── ROUTE 4: Get today's topic (or specific day) ───────────────────
@app.route('/todays-topic', methods=['GET'])
def todays_topic():
    user_id = request.args.get('user_id')
    if not user_id:
        return jsonify({"error": "User ID required"}), 400
        
    plan = get_user_plan(user_id)
    if not plan or not plan.get("topics"):
        return jsonify({"error": "No study plan found"}), 404
    
    # Check if a specific day is requested
    req_day = request.args.get('day')
    if req_day is not None:
        try:
            idx = int(req_day) - 1 # Convert 1-indexed to 0-indexed
        except:
            idx = plan.get("current_day_index", 0)
    else:
        idx = plan.get("current_day_index", 0)
        
    # Bounds checking
    idx = max(0, min(idx, len(plan["topics"]) - 1))
    
    topic = plan["topics"][idx]
    
    # Check if this day is already completed
    is_completed = any(h.get("day_index") == idx for h in plan.get("completed_history", []))
    
    # 1. Fast Path: Check Cache
    idx_str = str(idx)
    topic_chats = plan.get("chat_history", {}).get(idx_str, [])
    
    if idx_str in plan.get("cached_explanations", {}):
        content = plan["cached_explanations"][idx_str]
    else:
        # Get explanation
        prompt = f"""Explain for exam: {topic}. 
Return JSON: {{"summary":"", "key_points":[], "formulas":[], "exam_tip":"", "estimated_time":""}}"""
        
        try:
            res = client.chat.completions.create(
                model="llama-3.3-70b-versatile",
                messages=[{"role": "user", "content": prompt}]
            )
            raw = res.choices[0].message.content.strip()
            if "```" in raw:
                raw = raw.split("```")[1]
                if raw.startswith("json"): raw = raw[4:]
            
            content = json.loads(raw)
            # Store in cache
            plan.setdefault("cached_explanations", {})[idx_str] = content
            save_user_plan(user_id, plan)
        except Exception as e:
            print(f"Explanation error: {e}")
            content = {"summary": "Error generating content. Please try again."}
        
    return jsonify({
        "day": idx + 1,
        "topic": topic,
        "total_days": len(plan["topics"]),
        "days_left": len(plan["topics"]) - (idx + 1),
        "progress_percent": int(((idx + 1) / len(plan["topics"])) * 100),
        "summary": content.get("summary", ""),
        "key_points": content.get("key_points", []),
        "formulas": content.get("formulas", []),
        "exam_tip": content.get("exam_tip", ""),
        "estimated_time": content.get("estimated_time", ""),
        "is_complete": is_completed,
        "chat_history": topic_chats,
        "all_topics": plan.get("topics", [])
    })


# ─── ROUTE 5: Mark day as done ───────────────────────────────────
@app.route('/mark-done', methods=['POST'])
def mark_done():
    data = request.json or {}
    user_id = data.get("user_id")
    if not user_id:
        return jsonify({"error": "User ID required"}), 400
        
    plan = get_user_plan(user_id)
    if not plan:
        return jsonify({"error": "Plan not found"}), 404
    
    # If UI sends a specific day (1-indexed), mark that. Otherwise, mark current_day.
    day_idx = data.get("day")
    if day_idx is not None:
        idx_to_mark = int(day_idx) - 1
    else:
        idx_to_mark = plan.get("current_day_index", 0)
        
    today_str = datetime.now().strftime("%Y-%m-%d")
    
    # Prevent duplicate history entries for the same day
    if not any(h.get("day_index") == idx_to_mark for h in plan.get("completed_history", [])):
        plan.setdefault("completed_history", []).append({
            "day_index": idx_to_mark,
            "date": today_str
        })
    
    # Advance current_day_index if they just finished the current one
    if idx_to_mark == plan.get("current_day_index", 0):
        plan["current_day_index"] = min(idx_to_mark + 1, len(plan.get("topics", [])) - 1)
        
    save_user_plan(user_id, plan)
    return jsonify({"success": True})


# ─── ROUTE 6: Get progress ────────────────────────────────────────
@app.route('/progress', methods=['GET'])
def get_progress():
    user_id = request.args.get('user_id')
    if not user_id:
        return jsonify({"error": "User ID required"}), 400
        
    plan = get_user_plan(user_id)
    if not plan or not plan.get("topics"): 
        return jsonify({"error": "No plan found"}), 404
        
    total = len(plan["topics"])
    comp = len(plan.get("completed_history", []))
    history = plan.get("completed_history", [])
    
    return jsonify({
        "total_topics": total,
        "completed_topics": comp,
        "progress_percent": int((comp / max(1, total)) * 100),
        "remaining_topics": total - comp,
        "history": history
    })

# ─── ROUTE 7: AI Chat Tutor ──────────────────────────────────────
@app.route('/chat', methods=['POST'])
def chat():
    data = request.json
    user_id = data.get("user_id")
    if not user_id:
        return jsonify({"error": "User ID required"}), 400
        
    plan = get_user_plan(user_id)
    if not plan:
        return jsonify({"error": "Plan not found"}), 404
        
    topic = data.get("topic", "")
    user_message = data.get("message", "")
    day_idx = data.get("day", 1) - 1 # Convert to 0-indexed
    
    if not user_message:
        return jsonify({"error": "Message is required"}), 400
        
    idx_str = str(day_idx)
    history_list = plan.setdefault("chat_history", {}).setdefault(idx_str, [])
    
    # Save user message immediately
    history_list.append({"isUser": True, "text": user_message})
    save_user_plan(user_id, plan)
        
    prompt = f"""You are a helpful study tutor helping a university student.
The student is currently studying: {topic}

Student asks: {user_message}

Please provide a clear, concise, and helpful answer. Keep it relatively short and conversational. Do not use markdown headers."""

    try:
        res = client.chat.completions.create(
            model="llama-3.3-70b-versatile",
            messages=[{"role": "user", "content": prompt}]
        )
        reply = res.choices[0].message.content.strip()
        
        # Save AI reply
        history_list.append({"isUser": False, "text": reply})
        save_user_plan(user_id, plan)
        
        return jsonify({"reply": reply})
    except Exception as e:
        print(f"Chat error: {e}")
        return jsonify({"error": str(e)}), 500


# ─── Run server ───────────────────────────────────────────────────
if __name__ == '__main__':
    init_db()
    app.run(debug=True, host='0.0.0.0', port=5000)