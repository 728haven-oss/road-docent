package kr.roaddocent

import androidx.test.core.app.ApplicationProvider
import kr.roaddocent.auth.AuthStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthLoginTest {
    @Test fun testAccountWith1234SignsIn() {
        val a = AuthStore(ApplicationProvider.getApplicationContext())
        assertTrue(a.signInLocal("test", "1234").isSuccess)
        assertEquals("test", a.user?.id)
        assertTrue(a.signInLocal("test", "12345").isFailure)
        assertTrue("case/space tolerant id", a.signInLocal(" TEST ", "1234").isSuccess)
    }
}
