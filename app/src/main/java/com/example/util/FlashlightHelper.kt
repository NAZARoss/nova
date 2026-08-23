package com.example.util

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log

class FlashlightHelper(private val context: Context) {
    private val cameraManager: CameraManager? = try {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    } catch (e: Exception) {
        null
    }

    private var cameraId: String? = null
    private var isTorchOn = false

    init {
        try {
            cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)
                hasFlash == true
            }
        } catch (e: Exception) {
            Log.e("FlashlightHelper", "Error initializing camera for flash: ${e.message}")
        }
    }

    fun setTorch(enable: Boolean) {
        val id = cameraId ?: return
        val manager = cameraManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setTorchMode(id, enable)
                isTorchOn = enable
            }
        } catch (e: CameraAccessException) {
            Log.e("FlashlightHelper", "CameraAccessException toggling torch: ${e.message}")
        } catch (e: Exception) {
            Log.e("FlashlightHelper", "Error toggling torch: ${e.message}")
        }
    }

    fun toggleTorch(): Boolean {
        setTorch(!isTorchOn)
        return isTorchOn
    }
}
