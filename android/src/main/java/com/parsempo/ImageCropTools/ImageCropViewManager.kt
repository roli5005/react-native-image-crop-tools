package com.roli5005.ImageCropTools

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.canhub.cropper.CropImageView
import com.facebook.react.uimanager.events.RCTEventEmitter
import java.io.File
import java.util.*

class ImageCropViewManager: SimpleViewManager<CropImageView>() {
    
    companion object {
        const val REACT_CLASS = "CropView"
        const val ON_IMAGE_SAVED = "onImageSaved"
        const val SOURCE_URL_PROP = "sourceUrl"
        const val KEEP_ASPECT_RATIO_PROP = "keepAspectRatio"
        const val ASPECT_RATIO_PROP = "cropAspectRatio"
        
        const val SAVE_IMAGE_COMMAND = 1
        const val ROTATE_IMAGE_COMMAND = 2
        const val FLIP_HORIZONTAL_COMMAND = 3
        const val FLIP_VERTICAL_COMMAND = 4
        const val RESET_IMAGE_COMMAND = 5
        
        const val SAVE_IMAGE_COMMAND_NAME = "saveImage"
        const val ROTATE_IMAGE_COMMAND_NAME = "rotateImage"
        const val FLIP_HORIZONTAL_COMMAND_NAME = "flipImageHorizontally"
        const val FLIP_VERTICAL_COMMAND_NAME = "flipImageVertically"
        const val RESET_IMAGE_COMMAND_NAME = "resetImage"
        
        private const val TAG = "ImageCropViewManager"
    }

    // Store image state for transformation preservation
    private data class ViewState(
        var originalImageUri: Uri? = null,
        var currentTransformedUri: Uri? = null,
        var lastSourceUrl: String? = null,
        var rotationDegrees: Int = 0,
        var hasValidSavedState: Boolean = false
    )
    
    // Map to store state for each view instance
    private val viewStateMap = mutableMapOf<Int, ViewState>()

    override fun createViewInstance(reactContext: ThemedReactContext): CropImageView {
        Log.d(TAG, "Creating CropImageView instance")
        val view = CropImageView(reactContext)
        
        // Initialize state for this view
        viewStateMap[view.id] = ViewState()
        
        // Configure the crop view with sensible defaults
        view.apply {
            // Set guidelines to be visible when cropping
            guidelines = CropImageView.Guidelines.ON_TOUCH
            
            // Ensure the view is visible
            visibility = android.view.View.VISIBLE
            
            // Set scale type for proper image display
            scaleType = CropImageView.ScaleType.FIT_CENTER
            
            // Disable auto-zoom (can cause display issues)
            isAutoZoomEnabled = false
            
            // Set aspect ratio to free by default
            setFixedAspectRatio(false)
            
            // Enable showing the crop overlay
            isShowCropOverlay = true
            
            // Set crop shape to rectangle by default
            cropShape = CropImageView.CropShape.RECTANGLE
        }
        
        view.setOnCropImageCompleteListener { _, result ->
            Log.d(TAG, "Crop complete - Success: ${result.isSuccessful}")
            if (result.isSuccessful) {
                val map = Arguments.createMap()
                map.putString("uri", result.getUriFilePath(reactContext, true).toString())
                result.cropRect?.let {
                    map.putInt("width", it.width())
                    map.putInt("height", it.height())
                }
                reactContext.getJSModule(RCTEventEmitter::class.java)?.receiveEvent(
                    view.id,
                    ON_IMAGE_SAVED,
                    map
                )
            } else {
                Log.e(TAG, "Crop failed: ${result.error?.message}")
            }
        }
        
        // Add listener to detect when image is set
        view.setOnSetImageUriCompleteListener { _, uri, error ->
            if (error == null) {
                Log.d(TAG, "Image loaded successfully from URI: $uri")
            } else {
                Log.e(TAG, "Failed to load image from URI: $uri", error)
            }
        }
        
        return view
    }

    override fun getName(): String {
        return REACT_CLASS
    }

    override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> {
        return MapBuilder.of(
            ON_IMAGE_SAVED,
            MapBuilder.of("registrationName", ON_IMAGE_SAVED)
        )
    }

    override fun getCommandsMap(): MutableMap<String, Int> {
        return MapBuilder.of(
            SAVE_IMAGE_COMMAND_NAME, SAVE_IMAGE_COMMAND,
            ROTATE_IMAGE_COMMAND_NAME, ROTATE_IMAGE_COMMAND,
            FLIP_HORIZONTAL_COMMAND_NAME, FLIP_HORIZONTAL_COMMAND,
            FLIP_VERTICAL_COMMAND_NAME, FLIP_VERTICAL_COMMAND,
            RESET_IMAGE_COMMAND_NAME, RESET_IMAGE_COMMAND
        )
    }

    override fun receiveCommand(root: CropImageView, commandId: Int, args: ReadableArray?) {
        val viewState = viewStateMap[root.id]
        if (viewState == null) {
            Log.e(TAG, "No view state found for view ${root.id}")
            return
        }
        
        when (commandId) {
            SAVE_IMAGE_COMMAND -> {
                try {
                    val preserveTransparency = args?.getBoolean(0) ?: false
                    val quality = if (args != null && args.size() > 1) args.getInt(1) else 100

                    val croppedImage = root.croppedImage
                    if (croppedImage == null) {
                        Log.e(TAG, "No cropped image available")
                        return
                    }
                    
                    var extension = "jpg"
                    var format = Bitmap.CompressFormat.JPEG
                    if (preserveTransparency && croppedImage.hasAlpha()) {
                        extension = "png"
                        format = Bitmap.CompressFormat.PNG
                    }
                    
                    val path = File(root.context.cacheDir, "${UUID.randomUUID()}.$extension").toURI().toString()
                    root.croppedImageAsync(format, quality, customOutputUri = Uri.parse(path))
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving image", e)
                }
            }
            
            ROTATE_IMAGE_COMMAND -> {
                val clockwise = args?.getBoolean(0) ?: true
                rotateImage(root, clockwise)
            }
            
            FLIP_HORIZONTAL_COMMAND -> {
                flipImage(root, horizontal = true)
            }
            
            FLIP_VERTICAL_COMMAND -> {
                flipImage(root, horizontal = false)
            }
            
            RESET_IMAGE_COMMAND -> {
                resetImage(root)
            }
        }
    }

    @ReactProp(name = SOURCE_URL_PROP)
    fun setSourceUrl(view: CropImageView, url: String?) {
        Log.d(TAG, "setSourceUrl called with: $url")
        
        if (url.isNullOrEmpty()) {
            Log.w(TAG, "Source URL is null or empty")
            return
        }
        
        val viewState = viewStateMap[view.id]
        if (viewState == null) {
            Log.e(TAG, "No view state found for view ${view.id}, creating new one")
            viewStateMap[view.id] = ViewState()
        }
        
        try {
            val uri = when {
                // Handle different URI schemes
                url.startsWith("file://") -> Uri.parse(url)
                url.startsWith("content://") -> Uri.parse(url)
                url.startsWith("http://") || url.startsWith("https://") -> Uri.parse(url)
                url.startsWith("/") -> Uri.fromFile(File(url))
                else -> {
                    // Try to parse as-is, might be a resource URI
                    Uri.parse(url)
                }
            }
            
            Log.d(TAG, "Parsed URI: $uri")
            
            viewStateMap[view.id]?.let { state ->
                val isNewSource = url != state.lastSourceUrl
                
                if (isNewSource) {
                    // New image - reset all state
                    state.originalImageUri = uri
                    state.currentTransformedUri = uri
                    state.lastSourceUrl = url
                    state.rotationDegrees = 0
                    state.hasValidSavedState = false
                    Log.d(TAG, "New source detected, resetting state")
                }
                
                // Set the image asynchronously
                view.post {
                    try {
                        view.setImageUriAsync(uri)
                        Log.d(TAG, "Image URI set successfully")
                        // Force layout refresh
                        view.requestLayout()
                        view.invalidate()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting image URI", e)
                        // Try setting bitmap directly as fallback
                        try {
                            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                                view.context.contentResolver, 
                                uri
                            )
                            view.setImageBitmap(bitmap)
                            Log.d(TAG, "Set image using bitmap fallback")
                            // Force layout refresh
                            view.requestLayout()
                            view.invalidate()
                        } catch (e2: Exception) {
                            Log.e(TAG, "Bitmap fallback also failed", e2)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing or setting source URL: $url", e)
        }
    }

    @ReactProp(name = KEEP_ASPECT_RATIO_PROP)
    fun setFixedAspectRatio(view: CropImageView, fixed: Boolean) {
        Log.d(TAG, "setFixedAspectRatio: $fixed")
        view.setFixedAspectRatio(fixed)
    }

    @ReactProp(name = ASPECT_RATIO_PROP)
    fun setAspectRatio(view: CropImageView, aspectRatio: ReadableMap?) {
        if (aspectRatio != null && aspectRatio.hasKey("width") && aspectRatio.hasKey("height")) {
            val width = aspectRatio.getInt("width")
            val height = aspectRatio.getInt("height")
            Log.d(TAG, "Setting aspect ratio: $width:$height")
            view.setAspectRatio(width, height)
        } else {
            Log.d(TAG, "Clearing aspect ratio")
            view.clearAspectRatio()
        }
    }
    
    private fun rotateImage(view: CropImageView, clockwise: Boolean) {
        try {
            val rotationAngle = if (clockwise) 90 else -90
            Log.d(TAG, "Rotating image by $rotationAngle degrees")
            view.rotateImage(rotationAngle)
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating image", e)
        }
    }
    
    private fun flipImage(view: CropImageView, horizontal: Boolean) {
        try {
            Log.d(TAG, "Flipping image ${if (horizontal) "horizontally" else "vertically"}")
            
            val currentBitmap = view.croppedImage
            if (currentBitmap == null) {
                Log.e(TAG, "No bitmap available for flipping")
                return
            }
            
            val flippedBitmap = flipBitmap(currentBitmap, horizontal)
            
            // Save the flipped bitmap to a temporary file
            val tempFile = File(view.context.cacheDir, "temp_flipped_${UUID.randomUUID()}.png")
            tempFile.outputStream().use { out ->
                flippedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            // Set the flipped image back to the crop view
            view.setImageUriAsync(Uri.fromFile(tempFile))
            
            // Clean up the flipped bitmap if it's different from the original
            if (flippedBitmap != currentBitmap) {
                flippedBitmap.recycle()
            }
            
            Log.d(TAG, "Image flipped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error flipping image", e)
        }
    }
    
    private fun resetImage(view: CropImageView) {
        val viewState = viewStateMap[view.id]
        if (viewState == null) {
            Log.e(TAG, "No view state found for reset")
            return
        }
        
        viewState.originalImageUri?.let { uri ->
            try {
                Log.d(TAG, "Resetting image to original: $uri")
                view.setImageUriAsync(uri)
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting image", e)
            }
        } ?: Log.w(TAG, "No original URI found for reset")
    }

    private fun flipBitmap(bitmap: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix()
        
        if (horizontal) {
            matrix.preScale(-1.0f, 1.0f)
        } else {
            matrix.preScale(1.0f, -1.0f)
        }
        
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            false
        )
    }
    
    // Clean up view state when view is destroyed
    override fun onDropViewInstance(view: CropImageView) {
        Log.d(TAG, "Dropping view instance ${view.id}")
        viewStateMap.remove(view.id)
        super.onDropViewInstance(view)
    }
}
