package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.example.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ChatRepository(private val chatDao: ChatDao) {

    // Local DB accessors
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()
    val userProfile: Flow<UserProfile?> = chatDao.getUserProfile()

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun createNewSession(title: String = "New Chat", academicLevel: String = "High School", studyMode: String = "Standard"): Long {
        val newSession = ChatSession(
            title = title,
            academicLevel = academicLevel,
            studyMode = studyMode
        )
        return chatDao.insertSession(newSession)
    }

    suspend fun updateSessionTitle(id: Long, title: String) {
        chatDao.updateSessionTitle(id, title)
    }

    suspend fun updateSessionPinned(id: Long, isPinned: Boolean) {
        chatDao.updateSessionPinned(id, isPinned)
    }

    suspend fun updateSessionStudyMode(id: Long, studyMode: String) {
        chatDao.updateSessionStudyMode(id, studyMode)
    }

    suspend fun deleteSession(id: Long) {
        chatDao.deleteSession(id)
        chatDao.deleteMessagesForSession(id)
    }

    suspend fun insertMessage(message: ChatMessage): Long {
        return chatDao.insertMessage(message)
    }

    suspend fun ensureProfileExists() {
        val currentProfile = chatDao.getUserProfile().first()
        if (currentProfile == null) {
            chatDao.insertUserProfile(
                UserProfile(
                    fullName = "Ash Ketchum",
                    email = "ash@pallettown.com",
                    academicLevel = "High School",
                    streakDays = 5,
                    totalQuestionsAsked = 15,
                    studyMode = "Standard"
                )
            )
        }
    }

    suspend fun updateUserProfile(profile: UserProfile) {
        chatDao.insertUserProfile(profile)
    }

    suspend fun incrementQuestionsAsked() {
        val current = chatDao.getUserProfile().first() ?: return
        chatDao.insertUserProfile(current.copy(totalQuestionsAsked = current.totalQuestionsAsked + 1))
    }

    // Dynamic system guidelines builder
    private fun getSystemPromptFor(studyMode: String, academicLevel: String): String {
        val basePrompt = """
            You are Pikachu AI, a friendly, professional, and expert full-stack educational assistant.
            Your tagline is: "Learn Smarter with Pikachu AI".
            
            Follow these rules strictly:
            1. You are intended ONLY for educational and learning purposes. If the user asks for non-educational, harmful, illegal, cybercrime-related, hate speech, or inappropriate adult content, you MUST politely but firmly refuse using Pikachu-style encouragement but remaining highly professional. (e.g. "Pika-Pika! I can only assist you with studies or learning queries, let's learn something energizing instead!").
            2. Explain concepts in a step-by-step, comprehensive manner.
            3. Adjust your explanations and vocabulary to match a student at the '$academicLevel' academic level.
            4. Be friendly! Use playful, Pikachu-themed phrases and encouragement (e.g., "Pika-pika! Let's crack this together!", "⚡ Electric progress!", "Volt-Tackle this problem!") while delivering high-value academic help.
            5. Always provide concrete examples with clear Markdown formatting or code tags where appropriate.
            
            Always include this exact disclaimer warning in a small clean footnote at the end of every answer:
            "Pikachu AI is intended for educational purposes. Always verify important information from trusted sources."
        """.trimIndent()

        val modeExtra = when (studyMode) {
            "Teacher Mode" -> """
                
                ACTIVE MODE: Teacher Mode (Interactive Tutoring)
                Guidelines:
                - Do NOT provide the raw answer or full resolution immediately!
                - Instead, explain the concepts, teach the reasoning and formulas step-by-step.
                - End your response by asking the student a simple, supportive leading question to help them figure out the next step.
                - Work with them interactively until they complete compiling the solution!
            """.trimIndent()
            "Math" -> """
                
                ACTIVE FOCUS: Math & Problem Solving
                Guidelines:
                - Break down math equations line-by-line.
                - State the specific algebraic, geometric, or calculus theorems applied.
                - Use clear, spaced mathematical formatting so it is easy to read.
            """.trimIndent()
            "Code" -> """
                
                ACTIVE FOCUS: Programming & Coding logic
                Guidelines:
                - Provide fully commented, readable code blocks with proper syntax definitions (e.g., ```python, ```kotlin, ```java).
                - Explain what each loop, parameter, or module does.
                - Add debugging and run tips.
            """.trimIndent()
            "Science" -> """
                
                ACTIVE FOCUS: Science Explanations
                Guidelines:
                - Use clear analogies, diagrams, and historical discoveries to explain physics, biology, and chemistry (e.g., DNA replication, mitochondria, cell systems).
            """.trimIndent()
            "History" -> """
                
                ACTIVE FOCUS: History & Timeline Learning
                Guidelines:
                - Outline historical contexts, triggers, major events, figures, and consequences.
                - Use structured bulleted timelines to map progression.
            """.trimIndent()
            "Language" -> """
                
                ACTIVE FOCUS: Language & Multilingual Assistance
                Guidelines:
                - Help with translations, grammar structures, vocabulary, and paragraph drafting.
                - Fully supports and utilizes English, Hindi (scripts or phonetic roman), and Urdu. Provide bilingual explanations if requested!
            """.trimIndent()
            else -> """
                
                ACTIVE FOCUS: Standard Study Mode
                Guidelines:
                - Generate clear summaries, flashcards, mock quiz questions, or structured diagrams.
            """.trimIndent()
        }

        return basePrompt + modeExtra
    }

    // Call Gemini API and persist response
    suspend fun getAIResponse(sessionId: Long, userPrompt: String, attachedFileContent: String?): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: API Key is not configured correctly in AI Studio Secrets. Please read setup instructions."
        }

        // Get past messages for context (Max last 12 messages for performance and context limits)
        val historyList = chatDao.getMessagesForSession(sessionId).first().takeLast(12)
        val session = chatDao.getAllSessions().first().firstOrNull { it.id == sessionId } ?: return@withContext "Error: Session not found"

        // Map past messages to Gemini Content elements
        val mappedHistory = mutableListOf<Content>()
        historyList.forEach { msg ->
            mappedHistory.add(
                Content(
                    role = if (msg.role == "user") "user" else "model",
                    parts = listOf(Part(text = msg.content))
                )
            )
        }

        // Prepare the new query parts (including file content if uploaded)
        val newQueryParts = mutableListOf<Part>()
        if (!attachedFileContent.isNullOrBlank()) {
            newQueryParts.add(Part(text = "Document context attached by student:\n---\n$attachedFileContent\n---\n"))
        }
        newQueryParts.add(Part(text = userPrompt))

        mappedHistory.add(
            Content(
                role = "user",
                parts = newQueryParts
            )
        )

        val systemPromptContent = Content(
            parts = listOf(Part(text = getSystemPromptFor(session.studyMode, session.academicLevel)))
        )

        val request = GenerateContentRequest(
            contents = mappedHistory,
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                maxOutputTokens = 1500
            ),
            systemInstruction = systemPromptContent
        )

        try {
            // gemini-3.5-flash is selected as the optimal default for basic education/Q&A
            val response = RetrofitClient.service.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )

            val aiAnswerText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Pika? I couldn't generate an answer. Please try rephrasing your academic query!"

            // Persist the response to Room
            val aiMessage = ChatMessage(
                sessionId = sessionId,
                role = "assistant",
                content = aiAnswerText
            )
            chatDao.insertMessage(aiMessage)
            incrementQuestionsAsked()

            return@withContext aiAnswerText
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error getting response from Gemini", e)
            val errorMessage = "Pika-Pika Error: ${e.localizedMessage ?: "Unresolved network connection. Please check your internet or API key."}"
            
            // Persist the error as an assistant reply for transparency
            chatDao.insertMessage(
                ChatMessage(
                    sessionId = sessionId,
                    role = "assistant",
                    content = errorMessage
                )
            )
            return@withContext errorMessage
        }
    }
}
