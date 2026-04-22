package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector

// ─────────────────────────────────────────────────────────────────
// Data classes
data class UserData(
    val userId: Int,
    val username: String
)

data class TopicData(
    val day: Int,
    val topic: String,
    val totalDays: Int,
    val daysLeft: Int,
    val progressPercent: Int,
    val summary: String,
    val keyPoints: List<String>,
    val formulas: List<String>,
    val examTip: String,
    val estimatedTime: String,
    val isComplete: Boolean,
    val chatHistory: List<ChatMessage>,
    val allTopics: List<String>
)

data class ProgressData(
    val totalTopics: Int,
    val completedTopics: Int,
    val progressPercent: Int,
    val remainingTopics: Int,
    val history: List<Int>
)

data class ChatMessage(
    val isUser: Boolean,
    val text: String
)

// ─────────────────────────────────────────────────────────────────
// API Calls
fun fetchTodaysTopic(ngrokUrl: String, userId: Int, day: Int?, callback: (TopicData?) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val urlString = if (day != null) "$ngrokUrl/todays-topic?user_id=$userId&day=$day" else "$ngrokUrl/todays-topic?user_id=$userId"
            val url = URL(urlString)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("ngrok-skip-browser-warning", "69420")
            val response = connection.inputStream.bufferedReader().readText().trim()
            
            if (!response.startsWith("{")) {
                println("API DEBUG: Received non-JSON response: $response")
                callback(null)
                return@launch
            }

            val json = JSONObject(response)

            val keyPoints = mutableListOf<String>()
            val kpArray = json.optJSONArray("key_points") ?: JSONArray()
            for (i in 0 until kpArray.length()) {
                keyPoints.add(kpArray.getString(i))
            }

            val formulas = mutableListOf<String>()
            val fArray = json.optJSONArray("formulas") ?: JSONArray()
            for (i in 0 until fArray.length()) {
                formulas.add(fArray.getString(i))
            }

            val historyArray = json.optJSONArray("chat_history") ?: JSONArray()
            val chatHistory = mutableListOf<ChatMessage>()
            for (i in 0 until historyArray.length()) {
                val item = historyArray.getJSONObject(i)
                chatHistory.add(ChatMessage(isUser = item.getBoolean("isUser"), text = item.getString("text")))
            }

            val allTopicsArray = json.optJSONArray("all_topics") ?: JSONArray()
            val allTopicsList = mutableListOf<String>()
            for (i in 0 until allTopicsArray.length()) {
                allTopicsList.add(allTopicsArray.getString(i))
            }

            val data = TopicData(
                day = json.getInt("day"),
                topic = json.getString("topic"),
                totalDays = json.getInt("total_days"),
                daysLeft = json.getInt("days_left"),
                progressPercent = json.getInt("progress_percent"),
                summary = json.optString("summary", ""),
                keyPoints = keyPoints,
                formulas = formulas,
                examTip = json.optString("exam_tip", ""),
                estimatedTime = json.optString("estimated_time", ""),
                isComplete = json.optBoolean("is_complete", false),
                chatHistory = chatHistory,
                allTopics = allTopicsList
            )

            callback(data)
        } catch (e: Exception) {
            println("Error: ${e.message}")
            callback(null)
        }
    }
}

fun markTopicDone(ngrokUrl: String, userId: Int, day: Int, callback: () -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = URL("$ngrokUrl/mark-done")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("ngrok-skip-browser-warning", "69420")
            
            val json = JSONObject()
            json.put("user_id", userId)
            json.put("day", day)
            connection.outputStream.write(json.toString().toByteArray())
            connection.outputStream.close()
            
            connection.inputStream.bufferedReader().readText()
            withContext(Dispatchers.Main) {
                callback()
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
}

fun fetchProgress(ngrokUrl: String, userId: Int, callback: (ProgressData?) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = URL("$ngrokUrl/progress?user_id=$userId")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("ngrok-skip-browser-warning", "69420")
            val response = connection.inputStream.bufferedReader().readText().trim()
            if (!response.startsWith("{")) {
                println("API DEBUG: Received non-JSON response: $response")
                callback(null)
                return@launch
            }
            val json = JSONObject(response)
            
            val history = mutableListOf<Int>()
            val hArray = json.optJSONArray("history") ?: JSONArray()
            for (i in 0 until hArray.length()) {
                val item = hArray.getJSONObject(i)
                history.add(item.getInt("day_index"))
            }

            val data = ProgressData(
                totalTopics = json.getInt("total_topics"),
                completedTopics = json.getInt("completed_topics"),
                progressPercent = json.getInt("progress_percent"),
                remainingTopics = json.getInt("remaining_topics"),
                history = history
            )

            callback(data)
        } catch (e: Exception) {
            println("Error: ${e.message}")
            callback(null)
        }
    }
}

fun checkStudyPlan(ngrokUrl: String, userId: Int, callback: (Boolean) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = URL("$ngrokUrl/check-plan?user_id=$userId")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("ngrok-skip-browser-warning", "69420")
            val response = connection.inputStream.bufferedReader().readText().trim()
            if (!response.startsWith("{")) {
                println("API DEBUG (CheckPlan): Received non-JSON response: $response")
                withContext(Dispatchers.Main) { callback(false) }
                return@launch
            }
            val json = JSONObject(response)
            withContext(Dispatchers.Main) {
                callback(json.optBoolean("has_plan", false))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                callback(false)
            }
        }
    }
}

fun sendChatMessage(ngrokUrl: String, userId: Int, topic: String, day: Int, message: String, callback: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = URL("$ngrokUrl/chat")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("ngrok-skip-browser-warning", "69420")
            
            val json = JSONObject()
            json.put("user_id", userId)
            json.put("topic", topic)
            json.put("message", message)
            json.put("day", day)
            
            connection.outputStream.write(json.toString().toByteArray())
            connection.outputStream.close()
            
            val response = connection.inputStream.bufferedReader().readText()
            val replyJson = JSONObject(response)
            withContext(Dispatchers.Main) {
                callback(replyJson.optString("reply", "Error formatting reply."))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                callback("Error: ${e.message}")
            }
        }
    }
}

fun loginUser(ngrokUrl: String, data: JSONObject, callback: (UserData?, String?) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = URL("$ngrokUrl/login")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("ngrok-skip-browser-warning", "69420")
            
            connection.outputStream.write(data.toString().toByteArray())
            connection.outputStream.close()
            
            val responseCode = connection.responseCode
            val responseText = (if (responseCode == 200) connection.inputStream.bufferedReader().readText() 
                               else connection.errorStream.bufferedReader().readText()).trim()
            
            if (!responseText.startsWith("{")) {
                println("API DEBUG: Received non-JSON response: $responseText")
                val displayError = if (responseText.length > 100) responseText.take(100) + "..." else responseText
                withContext(Dispatchers.Main) { callback(null, "Server error: $displayError") }
                return@launch
            }
            val json = JSONObject(responseText)
            if (responseCode == 200) {
                val user = UserData(json.getInt("user_id"), json.getString("username"))
                withContext(Dispatchers.Main) { callback(user, null) }
            } else {
                withContext(Dispatchers.Main) { callback(null, json.optString("error", "Login failed")) }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { callback(null, e.message) }
        }
    }
}

fun registerUser(ngrokUrl: String, data: JSONObject, callback: (UserData?, String?) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = URL("$ngrokUrl/register")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("ngrok-skip-browser-warning", "69420")
            
            connection.outputStream.write(data.toString().toByteArray())
            connection.outputStream.close()
            
            val responseCode = connection.responseCode
            val responseText = (if (responseCode == 200 || responseCode == 201) connection.inputStream.bufferedReader().readText() 
                               else connection.errorStream.bufferedReader().readText()).trim()
            
            if (!responseText.startsWith("{")) {
                println("API DEBUG (Login/Register): Received non-JSON response: $responseText")
                withContext(Dispatchers.Main) { callback(null, "Server error: Invalid response format") }
                return@launch
            }
            val json = JSONObject(responseText)
            if (responseCode == 200 || responseCode == 201) {
                val user = UserData(json.getInt("user_id"), json.getString("username"))
                withContext(Dispatchers.Main) { callback(user, null) }
            } else {
                withContext(Dispatchers.Main) { callback(null, json.optString("error", "Registration failed")) }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { callback(null, e.message) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    lateinit var filePickerLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private val ngrokUrl = "https://shrubbery-consensus-supplier.ngrok-free.dev"
    
    var onUploadSuccess: (() -> Unit)? = null
    var onUploadError: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                uploadFile(it)
            }
        }

        setContent {
            StudyAppUI(
                onPickFile = { pickFile() },
                ngrokUrl = ngrokUrl
            )
        }
    }

    fun pickFile() {
        filePickerLauncher.launch("*/*")
    }

    fun uploadFile(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream!!.readBytes()

                val url = URL("$ngrokUrl/upload")
                val connection = url.openConnection() as java.net.HttpURLConnection

                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.connectTimeout = 60000
                connection.readTimeout = 60000

                val outputStream = connection.outputStream
                outputStream.write(bytes)
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                val responseStream = if (responseCode == 200) connection.inputStream else connection.errorStream
                val response = responseStream.bufferedReader().readText()
                
                if (responseCode != 200) {
                    withContext(Dispatchers.Main) {
                        onUploadError?.invoke("Server error ($responseCode): $response")
                    }
                    return@launch
                }

                val json = JSONObject(response)
                val topicsArray = json.getJSONArray("topics")
                
                val topics = mutableListOf<String>()
                for (i in 0 until topicsArray.length()) {
                    topics.add(topicsArray.getString(i))
                }

                if (topics.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onUploadError?.invoke("No topics extracted from file")
                    }
                    return@launch
                }

                // Initialize study plan
                val sharedPref = getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
                val userId = sharedPref.getInt("user_id", -1)
                if (userId != -1) {
                    initStudyPlan(topics, userId)
                } else {
                    withContext(Dispatchers.Main) {
                        onUploadError?.invoke("User session expired. Please log in again.")
                    }
                }

            } catch (e: Exception) {
                println("Upload Error: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onUploadError?.invoke("Upload failed: ${e.message}")
                }
            }
        }
    }

    private fun initStudyPlan(topics: List<String>, userId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject()
                val arr = JSONArray()
                topics.forEach { arr.put(it) }
                json.put("topics", arr)
                json.put("user_id", userId)

                val url = URL("$ngrokUrl/init-study-plan")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val outputStream = connection.outputStream
                outputStream.write(json.toString().toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                val responseStream = if (responseCode == 200) connection.inputStream else connection.errorStream
                val response = responseStream.bufferedReader().readText()
                
                if (responseCode == 200) {
                    withContext(Dispatchers.Main) {
                        onUploadSuccess?.invoke()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onUploadError?.invoke("Failed to create study plan ($responseCode): $response")
                    }
                }

            } catch (e: Exception) {
                println("Init Error: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onUploadError?.invoke("Failed to create study plan: ${e.message}")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// UI COMPONENTS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyAppUI(onPickFile: () -> Unit, ngrokUrl: String) {
    var tabIndex by remember { mutableStateOf(0) }
    var studyPlanInitialized by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf("") }
    var isCheckingPlan by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("auth", android.content.Context.MODE_PRIVATE) }
    var currentUser by remember { 
        mutableStateOf(
            if (sharedPref.contains("user_id")) {
                UserData(sharedPref.getInt("user_id", -1), sharedPref.getString("username", "") ?: "")
            } else null
        ) 
    }
    var authMode by remember { mutableStateOf("login") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activity = context as? MainActivity

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            isCheckingPlan = true
            checkStudyPlan(ngrokUrl, currentUser!!.userId) { hasPlan ->
                isCheckingPlan = false
                studyPlanInitialized = hasPlan
                tabIndex = if (hasPlan) 0 else 1
            }
        } else {
            isCheckingPlan = false
            studyPlanInitialized = false
        }
        
        activity?.onUploadSuccess = {
            studyPlanInitialized = true
            uploadStatus = "✅ Study plan created! Open the menu and go to Learn tab to start."
            tabIndex = 0
        }
        activity?.onUploadError = { error ->
            uploadStatus = "❌ Upload failed: $error"
        }
    }

    if (currentUser == null) {
        if (authMode == "login") {
            LoginScreen(ngrokUrl, onLogin = { user ->
                sharedPref.edit().putInt("user_id", user.userId).putString("username", user.username).apply()
                currentUser = user
            }, onGoToSignup = { authMode = "signup" })
        } else {
            SignupScreen(ngrokUrl, onSignup = { user ->
                sharedPref.edit().putInt("user_id", user.userId).putString("username", user.username).apply()
                currentUser = user
            }, onGoToLogin = { authMode = "login" })
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Spacer(Modifier.height(48.dp))
                Text(
                    "Daily Learn", 
                    modifier = Modifier.padding(16.dp), 
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Welcome, ${currentUser?.username}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Divider()
                Spacer(Modifier.height(16.dp))
                NavigationDrawerItem(
                    label = { Text("📚 Learn") },
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("📤 Upload Syllabus") },
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("📊 Progress") },
                    selected = tabIndex == 2,
                    onClick = { tabIndex = 2; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                Divider()
                NavigationDrawerItem(
                    label = { Text("🚪 Logout") },
                    selected = false,
                    onClick = { 
                        sharedPref.edit().clear().apply()
                        currentUser = null
                        scope.launch { drawerState.close() } 
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            when (tabIndex) {
                                0 -> "Learn"
                                1 -> "Upload"
                                else -> "Progress"
                            }
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (uploadStatus.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Text(uploadStatus, modifier = Modifier.padding(12.dp))
                        }
                    }

                    when {
                        isCheckingPlan -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        tabIndex == 0 -> LearnTabScreen(ngrokUrl, currentUser!!.userId, studyPlanInitialized)
                        tabIndex == 1 -> UploadTabScreen(onPickFile)
                        tabIndex == 2 -> ProgressTabScreen(ngrokUrl, currentUser!!.userId, studyPlanInitialized)
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(ngrokUrl: String, onLogin: (UserData) -> Unit, onGoToSignup: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Daily Learn", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Login to your account", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "👁️" else "🕶️")
                }
            }
        )
        
        if (error.isNotEmpty()) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                isLoading = true
                error = ""
                val json = JSONObject()
                json.put("username", username)
                json.put("password", password)
                loginUser(ngrokUrl, json) { user, errMsg ->
                    isLoading = false
                    if (user != null) onLogin(user)
                    else error = errMsg ?: "Login failed"
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            else Text("Login")
        }
        
        TextButton(onClick = onGoToSignup) {
            Text("Don't have an account? Sign up")
        }
    }
}

@Composable
fun SignupScreen(ngrokUrl: String, onSignup: (UserData) -> Unit, onGoToLogin: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Join Daily Learn", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email (Optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "👁️" else "🕶️")
                }
            }
        )

        if (error.isNotEmpty()) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                isLoading = true
                error = ""
                val json = JSONObject()
                json.put("username", username)
                json.put("password", password)
                json.put("email", email)
                registerUser(ngrokUrl, json) { user, errMsg ->
                    isLoading = false
                    if (user != null) onSignup(user)
                    else error = errMsg ?: "Signup failed"
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            else Text("Create Account")
        }

        TextButton(onClick = onGoToLogin) {
            Text("Already have an account? Login")
        }
    }
}

@Composable
fun LearnTabScreen(ngrokUrl: String, userId: Int, studyPlanInitialized: Boolean) {
    var topicData by remember { mutableStateOf<TopicData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var currentDay by remember { mutableStateOf<Int?>(null) }
    var chatMessage by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isChatLoading by remember { mutableStateOf(false) }
    var showTopicList by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey, currentDay) {
        if (!studyPlanInitialized) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = false
        fetchTodaysTopic(ngrokUrl, userId, currentDay) { data ->
            topicData = data
            if (data != null) {
                if (currentDay == null) currentDay = data.day
                chatHistory = data.chatHistory
            }
            loading = false
            error = data == null
        }
    }

    if (showTopicList && topicData != null) {
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(onDismissRequest = { showTopicList = false }) {
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                item {
                    Text("All Topics", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                }
                items(topicData!!.allTopics.size) { index ->
                    TextButton(
                        onClick = { 
                            currentDay = index + 1
                            showTopicList = false 
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${index + 1}. ${topicData!!.allTopics[index]}", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        if (!studyPlanInitialized) {
            item {
                Spacer(Modifier.height(48.dp))
                Text("📚 No Study Plan Yet", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text("Open the menu and go to the Upload tab to get started!", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        } else if (loading) {
            item {
                Spacer(Modifier.height(48.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (error) {
            item {
                Spacer(Modifier.height(48.dp))
                Text("❌ Error loading topic", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Button(onClick = { refreshKey++ }, modifier = Modifier.fillMaxWidth()) { Text("🔄 Retry") }
            }
        } else if (topicData != null) {
            val topic = topicData!!
            item {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📅 Day ${topic.day} of ${topic.totalDays}", style = MaterialTheme.typography.labelMedium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { showTopicList = true }.padding(vertical = 4.dp)
                        ) {
                            Text(topic.topic, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                            Text("▼", style = MaterialTheme.typography.titleMedium)
                        }
                        LinearProgressIndicator(progress = topic.progressPercent / 100f, modifier = Modifier.fillMaxWidth().height(6.dp))
                        Text("⏳ ${topic.daysLeft} days left", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Text("📖 Summary", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(topic.summary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(24.dp))
                Text("🔑 Key Points", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }

            items(topic.keyPoints) { point ->
                Text("  • $point", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
            }

            if (topic.formulas.isNotEmpty() && topic.formulas[0].isNotBlank()) {
                item {
                    Text("📐 Formulas", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                items(topic.formulas) { formula ->
                    Text("  ∴ $formula", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("💡 Exam Tip", style = MaterialTheme.typography.titleMedium)
                        Text(topic.examTip, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("⏱ Estimated: ${topic.estimatedTime}", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { if (topic.day > 1) currentDay = topic.day - 1 }, enabled = topic.day > 1) { Text("⬅️ Prev") }
                    Button(onClick = { if (topic.day < topic.totalDays) currentDay = topic.day + 1 }, enabled = topic.day < topic.totalDays) { Text("Next ➡️") }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { markTopicDone(ngrokUrl, userId, topic.day) { refreshKey++ } },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (topic.isComplete) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (topic.isComplete) "✅ Completed" else "✅ Mark as Done")
                }
                Spacer(Modifier.height(32.dp))
                Divider()
                Text("💬 Ask a Doubt", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }

            items(chatHistory) { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(text = msg.text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            item {
                if (isChatLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = chatMessage, onValueChange = { chatMessage = it }, modifier = Modifier.weight(1f), placeholder = { Text("Ask about ${topic.topic}...") })
                    Button(onClick = {
                        if (chatMessage.isNotBlank()) {
                            val userMsg = chatMessage
                            chatHistory = chatHistory + ChatMessage(isUser = true, text = userMsg)
                            chatMessage = ""
                            isChatLoading = true
                            sendChatMessage(ngrokUrl, userId, topic.topic, topic.day, userMsg) { reply ->
                                chatHistory = chatHistory + ChatMessage(isUser = false, text = reply)
                                isChatLoading = false
                            }
                        }
                    }, enabled = !isChatLoading && chatMessage.isNotBlank()) { Text("Send") }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun UploadTabScreen(onPickFile: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📤", fontSize = 64.sp)
        Text("Upload Your Syllabus", style = MaterialTheme.typography.headlineMedium)
        Text("Upload PDF or image of your syllabus to create your study plan", textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("📁 Choose File") }
    }
}

@Composable
fun ProgressTabScreen(ngrokUrl: String, userId: Int, studyPlanInitialized: Boolean) {
    var progress by remember { mutableStateOf<ProgressData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        if (!studyPlanInitialized) { loading = false; return@LaunchedEffect }
        loading = true
        fetchProgress(ngrokUrl, userId) { data -> progress = data; loading = false }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!studyPlanInitialized) {
            Text("📊 No Study Plan Yet", style = MaterialTheme.typography.headlineMedium)
        } else if (loading) {
            CircularProgressIndicator()
        } else if (progress != null) {
            val p = progress!!
            Text("📊 Your Progress", style = MaterialTheme.typography.headlineSmall)
            Box(modifier = Modifier.size(150.dp).padding(16.dp)) {
                CircularProgressIndicator(progress = p.progressPercent / 100f, modifier = Modifier.size(150.dp), strokeWidth = 8.dp)
                Column(modifier = Modifier.matchParentSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${p.progressPercent}%", style = MaterialTheme.typography.headlineSmall)
                    Text("Complete", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Completed Topics:")
                        Text("${p.completedTopics} / ${p.totalTopics}")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Remaining Topics:")
                        Text("${p.remainingTopics}")
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("Completion Heat Map", style = MaterialTheme.typography.titleLarge)
            LazyVerticalGrid(columns = GridCells.Adaptive(40.dp), modifier = Modifier.fillMaxWidth().height(200.dp)) {
                items(p.totalTopics) { index ->
                    val isComplete = p.history.contains(index)
                    Box(modifier = Modifier.aspectRatio(1f).background(color = if (isComplete) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", style = MaterialTheme.typography.bodySmall, color = if (isComplete) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Button(onClick = { refreshKey++ }, modifier = Modifier.fillMaxWidth()) { Text("Refresh Progress") }
        }
    }
}
