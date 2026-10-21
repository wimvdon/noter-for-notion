package be.wimvandonck.noter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import be.wimvandonck.noter.logic.SignIns

/**
 * Receives the link back from "Connect to Notion" (`<app id>://oauth`, from the sign-in helper)
 * and brings the app back where the person was, with Settings showing the result. It shows
 * nothing itself.
 */
class SignInActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent?.data
        if (savedInstanceState == null && link != null && link.host == "oauth") {
            (application as NoterApp).signIn.finish(SignIns.returned(link.getQueryParameter("result"), link.getQueryParameter("error")))
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
