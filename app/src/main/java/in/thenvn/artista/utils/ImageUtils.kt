package `in`.thenvn.artista.utils

import `in`.thenvn.artista.BuildConfig
import `in`.thenvn.artista.R
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


abstract class ImageUtils {

    companion object {

        private const val TAG = "ImageUtils"

        /**
         * Helper function used to convert an EXIF orientation enum into a transformation matrix
         * that can be applied to a bitmap.
         *
         * @param orientation - One of the constants from [ExifInterface]
         */
        private fun decodeExifOrientation(orientation: Int): Matrix {
            val matrix = Matrix()

            when (orientation) {
                ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> Unit
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90F)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180F)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270F)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1F, 1F)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1F, -1F)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postScale(-1F, 1F)
                    matrix.postRotate(270F)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postScale(-1F, 1F)
                    matrix.postRotate(90F)
                }

                else -> throw IllegalArgumentException("Invalid orientation $orientation")
            }

            return matrix
        }

        /**
         * Writes the given bitmap to byte buffer while also normalizing pixel values with given
         * mean & standard deviation before writing value to buffer.
         *
         * @param bitmap Bitmap to get pixel values from
         * @param buffer Byte buffer to write to
         * @param mean Mean for normalizing pixel value
         * @param std Standard deviation for normalizing pixel value
         */
        fun bitmapToByteBuffer(
            bitmap: Bitmap,
            buffer: ByteBuffer,
            mean: Float = 0.0F,
            std: Float = 255.0F
        ) {
            val width = bitmap.width
            val height = bitmap.height

            if ((width * height * 3 * 4) != buffer.capacity())
                throw java.lang.IllegalArgumentException("Capacity not same as bitmap size!")


            val intValues = IntArray(width * height)
            bitmap.getPixels(intValues, 0, width, 0, 0, width, height)

            buffer.clear()

            var pixel = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val value = intValues[pixel++]

                    // Normalize channel values to [-1.0, 1.0]. This requirement varies by
                    // model. For example, some models might require values to be normalized
                    // to the range [0.0, 1.0] instead.
                    buffer.putFloat(((value shr 16 and 0xFF) - mean) / std)
                    buffer.putFloat(((value shr 8 and 0xFF) - mean) / std)
                    buffer.putFloat(((value and 0xFF) - mean) / std)
                }
            }
        }

//        fun blendBitmaps(
//            bitmap1: Bitmap, bitmap2: Bitmap,
//            mean: Float = 0.0F, std: Float = 255.0F,
//            multiplier: Float = 0.5F
//        ): ByteBuffer {
//            val width = bitmap1.width
//            val height = bitmap1.height
//            if (width != bitmap2.width || height != bitmap2.height) {
//                throw IllegalArgumentException("Bitmaps dimensions mismatch!")
//            }
//
//            val inputImage = ByteBuffer.allocateDirect(1 * width * height * 3 * 4)
//            inputImage.order(ByteOrder.nativeOrder())
//            inputImage.rewind()
//
//            val intValues1 = IntArray(width * height)
//            val intValues2 = IntArray(width * height)
//            bitmap1.getPixels(intValues1, 0, width, 0, 0, width, height)
//            bitmap2.getPixels(intValues2, 0, width, 0, 0, width, height)
//
//            var pixel = 0
//            for (y in 0 until height) {
//                for (x in 0 until width) {
//                    val value = intValues1[pixel]
//
//                    inputImage.putFloat(((value shr 16 and 0xFF) - mean) / std)
//                    inputImage.putFloat(((value shr 8 and 0xFF) - mean) / std)
//                    inputImage.putFloat(((value and 0xFF) - mean) / std)
//
//                    pixel++
//                }
//            }
//
//            inputImage.rewind()
//            return inputImage
//        }

        fun loadBitmapFromAssets(context: Context, path: String): Bitmap {
            val inputStream = context.assets.open(path)
            return BitmapFactory.decodeStream(inputStream)
        }

        fun convertArrayToBitmap(
            imageArray: Array<Array<Array<FloatArray>>>,
            imageWidth: Int,
            imageHeight: Int,
            overlapOffset: Int,
            fadeLeft: Boolean,
            fadeTop: Boolean
        ): Bitmap {
            val dAlpha: Int = (255F / overlapOffset).roundToInt()
            val bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)

            var alphaL: Int = -dAlpha
            for (x in imageArray[0].indices) {
                if (fadeLeft) alphaL += dAlpha
                else alphaL = 255

                var alphaT: Int = -dAlpha
                for (y in imageArray[0][0].indices) {
                    if (fadeTop) alphaT += dAlpha
                    else alphaT = 255

                    val alpha: Int = if (x < overlapOffset && y < overlapOffset) min(alphaL, alphaT)
                    else if (x >= overlapOffset && y < overlapOffset) alphaT
                    else if (x < overlapOffset && y >= overlapOffset) alphaL
                    else 255

                    val color = Color.argb(
                        alpha,
                        ((imageArray[0][x][y][0] * 255).toInt()),
                        ((imageArray[0][x][y][1] * 255).toInt()),
                        (imageArray[0][x][y][2] * 255).toInt()
                    )

                    bitmap.setPixel(y, x, color)
                }
            }
            return bitmap
        }

        /**
         * Creates a dummy bitmap
         *
         * @param width Width of created bitmap
         * @param height Height of created bitmap
         * @param color Color of bitmap
         * @return Created bitmap
         */
        fun createEmptyBitmap(width: Int, height: Int = width, color: Int = 0): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            if (color != 0) bitmap.eraseColor(color)
            return bitmap
        }

        /**
         * Loads a bitmap efficiently from the given uri scaled by some given factor.
         *
         * @param context Context to access content resolver from
         * @param uri Content uri of the image on device
         * @param originalWidth Original width of the image
         * @param originalHeight Original height of the image
         * @param r Factor by which the original dimensions are scaled
         * @return Scaled bitmap
         */
        fun loadScaledBitmap(
            context: Context,
            uri: Uri,
            originalWidth: Int,
            originalHeight: Int,
            r: Float
        ): Bitmap? {
            val targetWidth: Int = ceil(originalWidth * r).toInt()
            val targetHeight: Int = ceil(originalHeight * r).toInt()

            val sampleSize: Int =
                max(originalWidth, originalHeight) / max(targetWidth, targetHeight)
            var inputStream = context.contentResolver.openInputStream(uri)

            val exif = ExifInterface(inputStream!!)
            val transformation =
                decodeExifOrientation(
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_ROTATE_90
                    )
                )
            inputStream.close()

            val opts = BitmapFactory.Options().apply {
                inScaled = true
                inSampleSize = sampleSize
                inDensity = max(originalWidth, originalHeight)
                inTargetDensity = max(targetWidth, targetHeight) * sampleSize
                inMutable = false
            }

            inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, opts)
            inputStream?.close()
            return Bitmap.createBitmap(
                bitmap!!,
                0,
                0,
                bitmap.width,
                bitmap.height,
                transformation,
                true
            )
        }

        /**
         * Helper method to fade top edge of bitmap [bitmapIn] linearly like gradient (alpha 0 to 1) up to [nRows]
         * number of rows from top edge of bitmap.
         *
         * @param bitmapIn Bitmap to fade top edge of
         * @param nRows No of rows up to which linear fading to be applied
         */
        fun fadeTopEdge(bitmapIn: Bitmap, nRows: Int) {
            val pixels = IntArray(bitmapIn.width * bitmapIn.height)
            bitmapIn.getPixels(pixels, 0, bitmapIn.width, 0, 0, bitmapIn.width, bitmapIn.height)
            val dAlpha: Float = round((1F / nRows) * 100) / 100

            var alphaM = 0F
            for (i in 0..(bitmapIn.width * nRows)) {
                val alpha: Int = (255 * alphaM).toInt()
                pixels[i] = ColorUtils.setAlphaComponent(pixels[i], alpha)

                if (i % bitmapIn.width == 0 && i >= bitmapIn.width) {
                    alphaM += dAlpha
                }
            }

            bitmapIn.setPixels(pixels, 0, bitmapIn.width, 0, 0, bitmapIn.width, bitmapIn.height)
        }

        /**
         * Helper method to fade left edge of bitmap [bitmapIn] linearly like gradient (alpha 0 to 1) up to [nCols]
         * number of columns from left edge of bitmap.
         *
         * @param bitmapIn Bitmap to fade top edge of
         * @param nCols No of columns up to which linear fading to be applied
         */
        fun fadeLeftEdge(bitmapIn: Bitmap, nCols: Int) {
            val pixels = IntArray(bitmapIn.width * bitmapIn.height)
            bitmapIn.getPixels(pixels, 0, bitmapIn.width, 0, 0, bitmapIn.width, bitmapIn.height)
            val dAlpha: Float = round((1F / nCols) * 100) / 100

            for (i in pixels.indices step bitmapIn.width) {
                var alphaM = 0F
                for (j in 0..nCols) {
                    val alpha = (255 * alphaM).toInt()
                    pixels[i + j] = ColorUtils.setAlphaComponent(pixels[i + j], alpha)
                    alphaM += dAlpha
                }
            }

            bitmapIn.setPixels(pixels, 0, bitmapIn.width, 0, 0, bitmapIn.width, bitmapIn.height)
        }

        /**
         * Create a temporary file to store a bitmap
         *
         * @param context Context to use to access directories
         * @return Uri of the created file
         */
        fun createTemporaryUri(context: Context): Uri? {
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            Log.i(TAG, "createTemporaryFile: ${storageDir.toString()}")

            return try {
                val file = File.createTempFile("tmp", suffix, storageDir)
                FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    file
                )
            } catch (ex: IOException) {
                null
            }
        }

        /**
         * Helper method to write the bitmap to the standard public Pictures directory
         * of the device.
         */
        @Suppress("DEPRECATION")
        fun savePicture(context: Context, bitmap: Bitmap) {
            val dirName = context.resources.getString(R.string.app_name)
            val fileName = "${dirName}_${System.currentTimeMillis()}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$dirName"
                    )
                }

                val uri: Uri? = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                uri?.let {
                    val out = context.contentResolver.openOutputStream(uri)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    out?.close()
                }
            } else {
                val dirPath =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        .toString() +
                            "${File.separator}${dirName}"
                val file = File(dirPath)
                if (!file.exists()) file.mkdir()
                val imageFile = File(dirPath, fileName)
                val out = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                out.close()
            }
        }

        /**
         * Query the dimensions of the image at given uri
         *
         * @param context Context to use to access content resolver
         * @param uri Uri of the media
         * @return Size object representing size of media
         */
        fun getImageSizeFromUri(context: Context, uri: Uri): Size {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            return Size(options.outWidth, options.outHeight)
        }
    }
}