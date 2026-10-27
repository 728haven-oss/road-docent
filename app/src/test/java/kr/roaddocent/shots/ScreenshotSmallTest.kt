package kr.roaddocent.shots

import org.junit.Before
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Same screens on a narrow phone (360 dp, e.g. Galaxy A series) with the system font enlarged to 130 %. */
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xhdpi")
class ScreenshotSmallTest : ScreenshotTest() {
    override val profile = "small360_font130"
    @Before fun bigFont() { RuntimeEnvironment.setFontScale(1.3f) }
}
