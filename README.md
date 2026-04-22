# Daily Learn 📚
An AI-powered personalized study planner that helps students organize their syllabus and learn faster with the help of LLMs.

## 🚀 Features
- **Syllabus Parsing**: Upload a PDF or image of your syllabus to generate a day-by-day study plan.
- **AI Tutor**: Ask doubts about specific topics directly within the app.
- **Progress Tracking**: Track your completion status with a visual heat map.
- **Secure Auth**: Multi-user support with hashed password storage.
- **Modern UI**: Built with Jetpack Compose for a premium, responsive experience.

## 🛠️ Tech Stack
- **Android**: Kotlin, Jetpack Compose, Material 3, Coroutines.
- **Backend**: Flask (Python), MySQL, SQLAlchemy (Connector).
- **AI**: Groq API (Llama 3.3 70B).
- **Tunneling**: ngrok (for local development).

## 📦 Setup Instructions

### Backend (Flask + MySQL)
1. Ensure you have **XAMPP** installed and **MySQL** started.
2. Create a `.env` file based on `.env.example`.
3. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
4. Run the server:
   ```bash
   python app.py
   ```
   *Note: The database and tables will be created automatically on the first run.*

### Android App
1. Open the project in **Android Studio**.
2. Update the `ngrokUrl` in `MainActivity.kt` with your active tunnel address.
3. Build and Run on your emulator or physical device.

## 🔒 Security
Sensitive information like API keys and database credentials are excluded from this repository via `.gitignore`. Please use your own `GROQ_API_KEY` in the `.env` file.

## 📄 License
MIT License
