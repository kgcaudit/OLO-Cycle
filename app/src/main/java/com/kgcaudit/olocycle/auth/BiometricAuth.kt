package com.kgcaudit.olocycle.auth

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 프로필 잠금 인증. 생체인증(약함 이상) 또는 기기 화면 잠금(PIN/패턴/비밀번호)을 쓴다.
 * 기기 잠금 조합은 API 30+ 에서만 허용되므로 그 이하에선 생체인증만 쓰고 취소 버튼을 둔다.
 */
object BiometricAuth {

    private fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    /** 이 기기에서 잠금 해제 수단(생체/기기잠금)을 쓸 수 있는지. 없으면 잠금을 켜지 못하게 막는다. */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(code: Int, message: CharSequence) = onError(message.toString())
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title).setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators())
        // 취소 버튼은 기기잠금을 함께 허용할 때는 넣을 수 없다(시스템 취소가 대신함).
        if (authenticators() == BIOMETRIC_WEAK) builder.setNegativeButtonText("취소")
        prompt.authenticate(builder.build())
    }
}
