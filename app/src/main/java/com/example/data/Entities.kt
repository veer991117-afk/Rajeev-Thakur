package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "New Chat",
    val academicLevel: String = "High School",
    val studyMode: String = "Standard", // Standard, Math, Code, Science, History, Language, etc.
    val isPinned: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String, // "user" or "assistant"
    val content: String,
    val attachedFileName: String? = null,
    val attachedFileContent: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val fullName: String = "Ash Ketchum",
    val email: String = "ash@pallettown.com",
    val academicLevel: String = "High School",
    val streakDays: Int = 5,
    val totalQuestionsAsked: Int = 15,
    val studyMode: String = "Standard"
)
