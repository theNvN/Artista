package `in`.thenvn.artista.utils

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder


annotation class ImageUtils {

    companion object {
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
         * Decode a bitmap from a file and apply the transformations described in its EXIF data
         *
         * @param file - The image file to be read using [BitmapFactory.decodeFile]
         */
        fun decodeBitmap(file: File): Bitmap {
            val exif = ExifInterface(file.absolutePath)
            val transformation =
                decodeExifOrientation(
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90
                    )
                )

            val bitmap = BitmapFactory.decodeFile(file.absolutePath)

            return Bitmap.createBitmap(
                BitmapFactory.decodeFile(file.absolutePath), 0, 0,
                bitmap.width, bitmap.height,
                transformation,
                true
            )
        }

        fun scaleBitmapAndKeepRatio(
            targetBitmap: Bitmap,
            reqWidthInPixels: Int,
            reqHeightInPixels: Int
        ): Bitmap {
            if (targetBitmap.height == reqHeightInPixels
                && targetBitmap.width == reqWidthInPixels
            ) return targetBitmap

            val matrix = Matrix()
            matrix.setRectToRect(
                RectF(0F, 0F, targetBitmap.width.toFloat(), targetBitmap.width.toFloat()),
                RectF(0F, 0F, reqWidthInPixels.toFloat(), reqHeightInPixels.toFloat()),
                Matrix.ScaleToFit.FILL
            )

            return Bitmap.createBitmap(
                targetBitmap, 0, 0,
                targetBitmap.width, targetBitmap.width,
                matrix, true
            )
        }

        fun bitmapToByteBuffer(
            bitmapIn: Bitmap,
            width: Int,
            height: Int,
            mean: Float = 0.0F,
            std: Float = 255.0F
        ): ByteBuffer {
            val bitmap =
                scaleBitmapAndKeepRatio(
                    bitmapIn,
                    width,
                    height
                )
            val inputImage = ByteBuffer.allocateDirect(1 * width * height * 3 * 4)
            inputImage.order(ByteOrder.nativeOrder())
            inputImage.rewind()

            val intValues = IntArray(width * height)
            bitmap.getPixels(intValues, 0, width, 0, 0, width, height)
            var pixel = 0

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val value = intValues[pixel++]

                    // Normalize channel values to [-1.0, 1.0]. This requirement varies by
                    // model. For example, some models might require values to be normalized
                    // to the range [0.0, 1.0] instead.
                    inputImage.putFloat(((value shr 16 and 0xFF) - mean) / std)
                    inputImage.putFloat(((value shr 8 and 0xFF) - mean) / std)
                    inputImage.putFloat(((value and 0xFF) - mean) / std)
                }
            }

            inputImage.rewind()
            return inputImage
        }

        fun loadBitmapFromResource(context: Context, path: String): Bitmap {
            val inputStream = context.assets.open(path)
            return BitmapFactory.decodeStream(inputStream)
        }

        fun convertArrayToBitmap(
            imageArray: Array<Array<Array<FloatArray>>>,
            imageWidth: Int,
            imageHeight: Int
        ): Bitmap {
            val conf = Bitmap.Config.ARGB_8888
            val bitmap = Bitmap.createBitmap(imageWidth, imageHeight, conf)

            for (x in imageArray[0].indices) {
                for (y in imageArray[0][0].indices) {
                    val color = Color.rgb(
                        ((imageArray[0][x][y][0] * 255).toInt()),
                        ((imageArray[0][x][y][1] * 255).toInt()),
                        (imageArray[0][x][y][2] * 255).toInt()
                    )

                    bitmap.setPixel(y, x, color)
                }
            }
            return bitmap
        }

        fun createEmptyBitmap(width: Int, height: Int, color: Int = 0): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            if (color != 0) bitmap.eraseColor(color)
            return bitmap
        }
    }
}