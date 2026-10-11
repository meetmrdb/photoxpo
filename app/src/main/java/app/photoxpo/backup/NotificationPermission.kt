package app.photoxpo.backup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

object NotificationPermission {
    fun isGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestContract(): ActivityResultContract<Unit, Boolean> = Request()

    private class Request : ActivityResultContract<Unit, Boolean>() {
        private val delegate = ActivityResultContracts.RequestPermission()

        override fun createIntent(context: Context, input: Unit): Intent {
            return delegate.createIntent(context, Manifest.permission.POST_NOTIFICATIONS)
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
            return delegate.parseResult(resultCode, intent)
        }

        override fun getSynchronousResult(
            context: Context,
            input: Unit,
        ): SynchronousResult<Boolean>? {
            return delegate.getSynchronousResult(context, Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
