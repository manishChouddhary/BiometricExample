package com.biometricexample

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.biometricexample.BiometricMode.DEVICE_PASSCODE
import com.biometricexample.BiometricMode.TOUCH_ID
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Reusable biometric prompt fragment to use in different scenario
 */
class BiometricHelperFragment : BottomSheetDialogFragment() {
    private var biometricMode: BiometricMode = DEVICE_PASSCODE
    private lateinit var addBiometricContract: ActivityResultLauncher<Intent>
    var negativeButtonText: String? = null
    lateinit var biometricCallBack: (state: AuthStateFlow) -> Unit

    companion object {
        private fun getInstance(
            biometricMode: BiometricMode,
            negativeButtonText: String? = null,
            biometricCallBack: (state: AuthStateFlow) -> Unit,
        ): BiometricHelperFragment {
            return BiometricHelperFragment().apply {
                this.biometricMode = biometricMode
                this.negativeButtonText = negativeButtonText
                this.biometricCallBack = biometricCallBack
            }
        }

        fun showPrompt(
            biometricMode: BiometricMode,
            fragmentManager: FragmentManager,
            negativeButtonText: String,
            biometricCallBack: (state: AuthStateFlow) -> Unit,
        ) {
            val fragment = getInstance(biometricMode, negativeButtonText, biometricCallBack)
            if (!fragment.isAdded || !fragment.isVisible)
                fragment.show(fragmentManager, BiometricHelperFragment::class.java.canonicalName)
        }
    }

    private lateinit var sp: SharedPreferences

    private lateinit var biometricPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addBiometricContract = registerForActivityResult(AddBiometricContract()) {
            dismissPrompt()
        }
        when (biometricMode) {
            DEVICE_PASSCODE -> {
                openDeviceCredentialPrompt()
            }
            TOUCH_ID -> {
                openTouchIdPrompt()
            }
        }
    }

    fun dismissPrompt() {
        this@BiometricHelperFragment.dismissAllowingStateLoss()
    }

    private fun openTouchIdPrompt() {
        val biometricManager = BiometricManager.from(requireContext())
        when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val negativeText = negativeButtonText ?: getString(R.string.cancel)
                biometricPrompt = createBiometricPrompt()
                val biometricInfo = createPromptInfo()
                    .setConfirmationRequired(false)
                    .setNegativeButtonText(negativeText)
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .build()
                biometricPrompt.authenticate(biometricInfo)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                showPrompt(getString(R.string.device_credential_not_allowed))
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                showPrompt(getString(R.string.device_credential_not_working))
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // Prompts the user to create credentials that your app accepts.
                val enrollIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        putExtra(
                            Settings.ACTION_BIOMETRIC_ENROLL,
                            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                        )
                    }
                }
                addBiometricContract.launch(enrollIntent)
            }
            else -> {
                updateFlow(AuthStateFlow.UNSUPPORTED, true)
            }
        }
    }

    private fun createPromptInfo(): BiometricPrompt.PromptInfo.Builder {
        return BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.prompt_info_title))
            .setSubtitle(getString(R.string.prompt_info_subtitle))
            .setConfirmationRequired(false)
    }

    private fun createBiometricPrompt(): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(requireContext())
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.d("TAG", "$errorCode :: $errString")
                //updateFlow(HIDE_LOADING, false)
                if (errorCode == BiometricPrompt.ERROR_CANCELED) {
                    Log.d("TAG", "Authentication canceled")
                    updateFlow(AuthStateFlow.CANCEL, true)
                    this@BiometricHelperFragment.dismiss()
                }
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    // Because the negative button allows the user to enter an account password.
                    // for the users hows device not support device credential for other app
                    updateFlow(AuthStateFlow.NEGATIVE_BUTTON, true)
                }
                if (errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                    lockOutPrompt()
                    dismissPrompt()
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d("TAG", "Authentication was successful")
                updateFlow(AuthStateFlow.AUTH_SUCCESSFUL, true/*, result.cryptoObject?.cipher*/)
            }
        }
        return BiometricPrompt(this, executor, callback)
    }

    private fun updateFlow(
        state: AuthStateFlow,
        isDismiss: Boolean = true
    ) {
        biometricCallBack.invoke(state)
        if (isDismiss && !this.isDetached)
            this@BiometricHelperFragment.dismiss()
    }

    private fun openDeviceCredentialPrompt() {
        val biometricManager = BiometricManager.from(requireContext())
        when (biometricManager.canAuthenticate(DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                biometricPrompt = createBiometricPrompt()
                biometricPrompt.authenticate(
                    createPromptInfo()
                        .setAllowedAuthenticators(DEVICE_CREDENTIAL)
                        .build()
                )
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                showPrompt(getString(R.string.no_fingerprint_message))
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                showPrompt(getString(R.string.no_fingerprint_not_working))
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // Prompts the user to create credentials that your app accepts.
                val enrollIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        putExtra(
                            Settings.ACTION_BIOMETRIC_ENROLL,
                            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                        )
                    }
                }
                addBiometricContract.launch(enrollIntent)
            }
            else -> {
                updateFlow(AuthStateFlow.UNSUPPORTED, true)
            }
        }
    }

    private fun showPrompt(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                getString(R.string.sorry)
            )
            .setMessage(message)
            .setNegativeButton(getString(R.string.ok_cta)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun lockOutPrompt() {
        biometricPrompt.cancelAuthentication()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                getString(R.string.sorry)
            )
            .setMessage(getString(R.string.maximum_attempts))
            .setNegativeButton(getString(R.string.ok_cta)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    inner class AddBiometricContract : ActivityResultContract<Intent, Intent?>() {
        override fun createIntent(context: Context, input: Intent): Intent {
            return input
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Intent? {
            return if (resultCode != Activity.RESULT_OK || intent == null) null else intent
        }
    }
}
