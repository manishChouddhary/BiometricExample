package com.biometricexample

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.biometricexample.databinding.FragmentFirstBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonFirst.setOnClickListener {
            showBioPrompt()
        }
    }

    private fun showBioPrompt() {
        BiometricHelperFragment.showPrompt(
            BiometricMode.TOUCH_ID,
            requireActivity().supportFragmentManager,
            negativeButtonText = getString(R.string.cancel),
            biometricCallBack = { state -> observeAuthState(state) }
        )
    }

    private fun observeAuthState(state: AuthStateFlow) {
        when (state) {
            AuthStateFlow.AUTH_SUCCESSFUL -> {
                findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
            }
            AuthStateFlow.UNSUPPORTED -> {
                showPrompt(getString(R.string.unsuported))
            }
            AuthStateFlow.HIDE_LOADING -> Unit
            AuthStateFlow.NEGATIVE_BUTTON -> {
                showPrompt(getString(R.string.fingerprint_error_user_canceled))
            }
            AuthStateFlow.CANCEL -> Unit
        }
    }

    private fun showPrompt(message: String?) {
        if (message.isNullOrEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                getString(R.string.alert_title)
            )
            .setMessage(message)
            .setNegativeButton(
                getString(R.string.cancel)
            ) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.ok_cta)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}