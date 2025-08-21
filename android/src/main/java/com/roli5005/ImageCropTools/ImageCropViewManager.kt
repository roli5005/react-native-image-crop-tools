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
import java.io.FileOutputStream
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
        var originalBitmap: Bitmap? = null,
        var currentTransformedBitmap: Bitmap? = null,
        var lastSourceUrl: String? = null,
        var totalRotation: Int = 0, // Track total rotation in degrees
        var isFlippedHorizontally: Boolean = false,
        var isFlippedVertically: Boolean = false,
        var cropRect: android.graphics.Rect? = null
    )

    // Map to store state for each view instance
    private val viewStateMap = mutableMapOf<Int, ViewState>()

    override fun createViewInstance(reactContext: ThemedReactContext): CropImageView {
        Log.d(TAG, "Creating CropImageView instance")
        val view = CropImageView(reactContext)

        // Initialize state for this view
        viewStateMap[view.id] = ViewState()

        // Configure the crop view with sensible defaults
        setupCropView(view)

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
                url.startsWith("file://") -> Uri.parse(url)
                url.startsWith("content://") -> Uri.parse(url)
                url.startsWith("http://") || url.startsWith("https://") -> Uri.parse(url)
                url.startsWith("/") -> Uri.fromFile(File(url))
                else -> Uri.parse(url)
            }

            Log.d(TAG, "Parsed URI: $uri")

            viewStateMap[view.id]?.let { state ->
                val isNewSource = url != state.lastSourceUrl

                if (isNewSource) {
                    // New image - reset all state
                    state.originalImageUri = uri
                    state.lastSourceUrl = url
                    state.totalRotation = 0
                    state.isFlippedHorizontally = false
                    state.isFlippedVertically = false
                    state.cropRect = null

                    // Load and store the original bitmap
                    try {
                        val bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                            view.context.contentResolver,
                            uri
                        )
                        state.originalBitmap = bitmap
                        state.currentTransformedBitmap = bitmap.copy(bitmap.config, true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading bitmap", e)
                    }

                    Log.d(TAG, "New source detected, resetting state")
                }

                // Set the image
                view.post {
                    try {
                        if (state.currentTransformedBitmap != null) {
                            view.setImageBitmap(state.currentTransformedBitmap)
                        } else {
                            view.setImageUriAsync(uri)
                        }
                        Log.d(TAG, "Image set successfully")
                        view.requestLayout()
                        view.invalidate()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting image", e)
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

            // Validate aspect ratio values
            if (width > 0 && height > 0) {
                Log.d(TAG, "Setting aspect ratio: $width:$height")
                view.setAspectRatio(width, height)
            } else {
                Log.w(TAG, "Invalid aspect ratio values: $width:$height - clearing aspect ratio")
                view.clearAspectRatio()
            }
        } else {
            Log.d(TAG, "Clearing aspect ratio")
            view.clearAspectRatio()
        }
    }

    private fun rotateImage(view: CropImageView, clockwise: Boolean) {
        val viewState = viewStateMap[view.id] ?: return

        try {
            // Save current crop rectangle
            viewState.cropRect = view.cropRect

            val rotationAngle = if (clockwise) 90 else -90
            viewState.totalRotation = (viewState.totalRotation + rotationAngle) % 360
            if (viewState.totalRotation < 0) viewState.totalRotation += 360

            Log.d(TAG, "Rotating image by $rotationAngle degrees. Total rotation: ${viewState.totalRotation}")

            // Apply rotation to the current transformed bitmap
            viewState.currentTransformedBitmap?.let { currentBitmap ->
                val rotatedBitmap = rotateBitmap(currentBitmap, rotationAngle.toFloat())

                // Update the transformed bitmap
                if (viewState.currentTransformedBitmap != viewState.originalBitmap) {
                    viewState.currentTransformedBitmap?.recycle()
                }
                viewState.currentTransformedBitmap = rotatedBitmap

                // Set the rotated bitmap to the view
                view.setImageBitmap(rotatedBitmap)

                // Try to restore crop rectangle with adjusted dimensions
                restoreCropRect(view, viewState, rotationAngle != 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating image", e)
        }
    }

    private fun flipImage(view: CropImageView, horizontal: Boolean) {
        val viewState = viewStateMap[view.id] ?: return

        try {
            // Save current crop rectangle
            viewState.cropRect = view.cropRect

            Log.d(TAG, "Flipping image ${if (horizontal) "horizontally" else "vertically"}")

            viewState.currentTransformedBitmap?.let { currentBitmap ->
                val flippedBitmap = flipBitmap(currentBitmap, horizontal)

                // Update flip state
                if (horizontal) {
                    viewState.isFlippedHorizontally = !viewState.isFlippedHorizontally
                } else {
                    viewState.isFlippedVertically = !viewState.isFlippedVertically
                }

                // Update the transformed bitmap
                if (viewState.currentTransformedBitmap != viewState.originalBitmap) {
                    viewState.currentTransformedBitmap?.recycle()
                }
                viewState.currentTransformedBitmap = flippedBitmap

                // Set the flipped bitmap to the view
                view.setImageBitmap(flippedBitmap)

                // Restore crop rectangle
                restoreCropRect(view, viewState, false)
            }

            Log.d(TAG, "Image flipped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error flipping image", e)
        }
    }

    private fun resetImage(view: CropImageView) {
        val viewState = viewStateMap[view.id] ?: return

        viewState.originalBitmap?.let { originalBitmap ->
            try {
                Log.d(TAG, "Resetting image to original")

                // Clean up current transformed bitmap if it's different from original
                if (viewState.currentTransformedBitmap != null &&
                    viewState.currentTransformedBitmap != viewState.originalBitmap) {
                    viewState.currentTransformedBitmap?.recycle()
                }

                // Reset state
                viewState.currentTransformedBitmap = originalBitmap.copy(originalBitmap.config, true)
                viewState.totalRotation = 0
                viewState.isFlippedHorizontally = false
                viewState.isFlippedVertically = false
                viewState.cropRect = null

                // Set the original bitmap
                view.setImageBitmap(viewState.currentTransformedBitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting image", e)
            }
        } ?: Log.w(TAG, "No original bitmap found for reset")
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
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
            true
        )
    }

    private fun restoreCropRect(view: CropImageView, viewState: ViewState, dimensionsSwapped: Boolean) {
        // After transformations, reset to center
        // The CropImageView library doesn't allow direct crop rect setting after bitmap changes
        view.post {
            try {
                // Reset crop window to center
                view.resetCropRect()

                // The aspect ratio settings are already applied to the view itself
                // through the ReactProp setters, so we don't need to reapply them here
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring crop rect", e)
            }
        }
    }

    // Helper method to setup crop view properties
    private fun setupCropView(view: CropImageView) {
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
    }

    // Clean up view state when view is destroyed
    override fun onDropViewInstance(view: CropImageView) {
        Log.d(TAG, "Dropping view instance ${view.id}")

        // Clean up bitmaps
        viewStateMap[view.id]?.let { state ->
            if (state.currentTransformedBitmap != null &&
                state.currentTransformedBitmap != state.originalBitmap) {
                state.currentTransformedBitmap?.recycle()
            }
            state.originalBitmap?.recycle()
        }

        viewStateMap.remove(view.id)
        super.onDropViewInstance(view)
    }
}