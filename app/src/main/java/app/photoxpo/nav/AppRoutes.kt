package app.photoxpo.nav

import android.net.Uri

object AppRoutes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Photos = "photos"
    const val Photo = "photo/{hash}"

    fun photo(hash: String): String = "photo/${Uri.encode(hash)}"
}
