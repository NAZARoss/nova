package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.net.URLEncoder

object AppLaunchHelper {

    private const val TAG = "AppLaunchHelper"

    /**
     * Opens the camera app directly targeting the front-facing (selfie) camera.
     */
    fun openFrontCamera(context: Context) {
        val intentsToTry = listOf(
            // Standard STILL_IMAGE_CAMERA with front facing camera flags
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                putExtra("android.intent.extras.CAMERA_FACING", 1)
                putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                putExtra("android.intent.extra.CAMERA_FACING", 1)
                putExtra("com.google.assistant.extra.CAMERA_FACING", "front")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            // Fallback: IMAGE_CAPTURE with front facing camera flags
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra("android.intent.extras.CAMERA_FACING", 1)
                putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                putExtra("android.intent.extra.CAMERA_FACING", 1)
                putExtra("com.google.assistant.extra.CAMERA_FACING", "front")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )

        for (intent in intentsToTry) {
            try {
                context.startActivity(intent)
                Log.d(TAG, "Successfully launched camera app with front camera intent")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed intent variant: ${e.message}")
            }
        }

        // Generic fallback to main camera intent
        try {
            val genericIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(genericIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open camera app: ${e.message}")
        }
    }

    /**
     * Opens browser (preferring Google Chrome) with the specified search query or URL.
     */
    fun openBrowser(context: Context, queryOrUrl: String) {
        val trimmed = queryOrUrl.trim()
        val targetUri = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            Uri.parse(trimmed)
        } else {
            val encoded = try {
                URLEncoder.encode(trimmed, "UTF-8")
            } catch (e: Exception) {
                trimmed
            }
            Uri.parse("https://www.google.com/search?q=$encoded")
        }

        // 1. Try launching Google Chrome specifically
        val chromeIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
            setPackage("com.android.chrome")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(chromeIntent)
            Log.d(TAG, "Successfully opened Chrome with: $targetUri")
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Chrome not found, falling back to default browser handler: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Error launching Chrome: ${e.message}")
        }

        // 2. Fallback to any default browser
        try {
            val defaultBrowserIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(defaultBrowserIntent)
            Log.d(TAG, "Opened default browser with: $targetUri")
        } catch (e: Exception) {
            Log.e(TAG, "No browser application available: ${e.message}")
        }
    }
}
