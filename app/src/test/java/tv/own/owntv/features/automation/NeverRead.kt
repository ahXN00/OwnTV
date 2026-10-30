package tv.own.owntv.features.automation

import java.io.InputStream

/** A request body that fails the test if anything reads it: it stands for bytes a caller has not sent yet. */
object NeverRead : InputStream() {
    override fun read(): Int = throw AssertionError("the body was read")
}
