import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import java.nio.ByteBuffer

object BitmapUtils {
    fun getBitmap(image: Image): Bitmap? {
        return try {
            val plane = image.planes[0]
            val buffer: ByteBuffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}