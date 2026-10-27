package kr.roaddocent.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the launcher icon layers (full bleed + monochrome) so the icon can be checked before release. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxxhdpi")
class IconShotTest {
    @Test fun icon() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val icon = ctx.getDrawable(kr.roaddocent.R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val dir = File(System.getProperty("user.dir"), "build/shots/icon").apply { mkdirs() }
        fun save(name: String, d: android.graphics.drawable.Drawable?) {
            d ?: return
            val b = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
            val c = Canvas(b); d.setBounds(0, 0, 432, 432); d.draw(c)
            File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val full = android.graphics.drawable.LayerDrawable(arrayOf(icon.background, icon.foreground))
        save("layers", full)
        save("mono", icon.monochrome)
    }
}
