package com.codex.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import com.codex.app.databinding.DialogMediaLightboxBinding

class MediaLightboxDialog : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogMediaLightboxBinding.inflate(inflater, container, false)
        val url = requireArguments().getString(ARG_URL).orEmpty()
        val isGif = requireArguments().getBoolean(ARG_IS_GIF)
        if (isGif) {
            Glide.with(this).asGif().load(url).fitCenter().into(binding.lightboxImage)
        } else {
            Glide.with(this).load(url).fitCenter().into(binding.lightboxImage)
        }
        binding.lightboxClose.setOnClickListener { dismiss() }
        binding.lightboxRoot.setOnClickListener { dismiss() }
        binding.lightboxImage.setOnClickListener { }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    companion object {
        private const val TAG = "MediaLightboxDialog"
        private const val ARG_URL = "url"
        private const val ARG_IS_GIF = "isGif"

        fun show(manager: FragmentManager, url: String, isGif: Boolean) {
            if (manager.findFragmentByTag(TAG) != null) return
            MediaLightboxDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                    putBoolean(ARG_IS_GIF, isGif)
                }
            }.show(manager, TAG)
        }
    }
}