package com.example.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONObject

enum class OperationType {
    CREATE,
    GET,
    LIST,
    UPDATE,
    DELETE,
    WRITE
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String) {
    val errorDetails = JSONObject().apply {
        put("operation", operationType.name)
        put("target_path", path)
        put("exception_class", exception.javaClass.simpleName)
        put("error_message", exception.message ?: "Unknown error")
        if (exception is FirebaseFirestoreException) {
            put("error_code", exception.code.name)
        }
    }
    Log.e("FirestoreError", errorDetails.toString(2), exception)
}
