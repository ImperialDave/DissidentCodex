package com.codex.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.codex.app.MainActivity
import com.codex.app.R
import com.codex.app.databinding.FragmentCreatePostBinding
import com.codex.app.utils.FirebaseHelper
import com.google.android.material.chip.Chip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CreatePostFragment : Fragment(), GifPickerBottomSheet.Listener {

    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = _binding!!

    private var selectedImageUri: Uri? = null
    private var selectedRemoteUrl: String? = null
    private var selectedMediaType: String? = null
    private var categoryNames: List<String> = emptyList()

    private val prefs by lazy { requireContext().getSharedPreferences("codex_drafts", android.content.Context.MODE_PRIVATE) }
    private var draftKey: String = "current_draft"
    private var isRestoringDraft = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        draftKey = "draft_${FirebaseHelper.getCurrentFirebaseUser()?.uid ?: "anon"}"
        setupButtons()
        loadCategories()
        loadDraft()
        setupDraftAutoSave()
        setupPreviewWatchers()
    }

    private fun loadCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            categoryNames = FirebaseHelper.getCategoryNames()
            if (!isAdded) return@launch
            renderCategoryChips()
        }
    }

    private fun renderCategoryChips() {
        val b = _binding ?: return
        val ctx = context ?: return
        b.categorySuggestions.removeAllViews()
        if (categoryNames.isEmpty()) {
            val hint = android.widget.TextView(ctx).apply {
                text = getString(R.string.no_categories_yet)
                setTextColor(ctx.getColor(R.color.on_surface_variant))
                textSize = 12f
            }
            b.categorySuggestions.addView(hint)
            return
        }
        categoryNames.forEach { name ->
            val chip = Chip(ctx).apply {
                text = name
                isCheckable = false
                setOnClickListener { _binding?.categoryInput?.setText(name) }
            }
            b.categorySuggestions.addView(chip)
        }
    }

    private fun setupPreviewWatchers() {
        fun updatePreview() {
            val b = _binding ?: return
            val title = b.titleInput.text?.toString()?.trim().orEmpty()
            val body = b.bodyInput.text?.toString()?.trim().orEmpty()
            val cat = b.categoryInput.text?.toString()?.trim().orEmpty().ifBlank { "(category)" }
            b.postPreview.text = if (title.isNotBlank() || body.isNotBlank()) {
                "[$cat] $title\n${body.take(100)}${if (body.length > 100) "..." else ""}"
            } else getString(R.string.no_categories_yet)
        }
        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updatePreview() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        binding.titleInput.addTextChangedListener(watcher)
        binding.bodyInput.addTextChangedListener(watcher)
        binding.categoryInput.addTextChangedListener(watcher)
        updatePreview()
    }

    private fun setupButtons() {
        binding.addImageButton.setOnClickListener {
            GifPickerBottomSheet.show(requireActivity())
        }
        binding.removeImageButton.setOnClickListener { clearImageSelection() }
        binding.publishButton.setOnClickListener { publishPost() }
        binding.saveDraftButton.setOnClickListener {
            saveDraft()
            (activity as? MainActivity)?.showToast(getString(R.string.draft_saved))
        }
        binding.clearDraftButton.setOnClickListener {
            clearDraft()
            binding.titleInput.text = null
            binding.bodyInput.text = null
            binding.categoryInput.text = null
            (activity as? MainActivity)?.showToast(getString(R.string.draft_cleared))
        }
    }

    override fun onGifSelected(selection: GifPickerBottomSheet.Selection) {
        when (selection) {
            is GifPickerBottomSheet.Selection.Local -> {
                selectedImageUri = selection.uri
                selectedRemoteUrl = null
                selectedMediaType = selection.mediaType
                showImagePreview(selection.uri, selection.mediaType)
            }
            is GifPickerBottomSheet.Selection.Remote -> {
                selectedImageUri = null
                selectedRemoteUrl = selection.url
                selectedMediaType = selection.mediaType
                showRemotePreview(selection.url, selection.mediaType)
            }
        }
    }

    private fun showImagePreview(uri: Uri, mediaType: String) {
        binding.imagePreview.visibility = View.VISIBLE
        binding.imageNameText.visibility = View.VISIBLE
        binding.removeImageButton.visibility = View.VISIBLE
        binding.imageNameText.text = uri.lastPathSegment ?: if (mediaType == com.codex.app.models.MediaType.GIF) "selected.gif" else "selected_image.jpg"
        if (mediaType == com.codex.app.models.MediaType.GIF) {
            Glide.with(this).asGif().load(uri).fitCenter().into(binding.imagePreview)
        } else {
            Glide.with(this).load(uri).centerCrop().into(binding.imagePreview)
        }
    }

    private fun showRemotePreview(url: String, mediaType: String) {
        binding.imagePreview.visibility = View.VISIBLE
        binding.imageNameText.visibility = View.VISIBLE
        binding.removeImageButton.visibility = View.VISIBLE
        binding.imageNameText.text = "Giphy GIF"
        Glide.with(this).asGif().load(url).fitCenter().into(binding.imagePreview)
    }

    private fun clearImageSelection() {
        selectedImageUri = null
        selectedRemoteUrl = null
        selectedMediaType = null
        binding.imagePreview.visibility = View.GONE
        binding.imageNameText.visibility = View.GONE
        binding.removeImageButton.visibility = View.GONE
    }

    private fun publishPost() {
        val title = binding.titleInput.text?.toString()?.trim().orEmpty()
        val body = binding.bodyInput.text?.toString()?.trim().orEmpty()
        val category = binding.categoryInput.text?.toString()?.trim().orEmpty()

        if (title.isBlank() || body.isBlank()) {
            Toast.makeText(requireContext(), "Title and body are required", Toast.LENGTH_SHORT).show()
            return
        }
        if (category.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.category_required), Toast.LENGTH_SHORT).show()
            return
        }

        val main = activity as? MainActivity ?: return
        setLoading(true)
        clearDraft()
        main.publishNewPost(title, body, category, selectedImageUri, selectedRemoteUrl, selectedMediaType)
        // Activity handles toast + navigation; re-enable if still on this screen after a moment
        viewLifecycleOwner.lifecycleScope.launch {
            delay(1500)
            if (_binding != null) setLoading(false)
        }
    }

    private fun setLoading(loading: Boolean) {
        val b = _binding ?: return
        b.publishButton.isEnabled = !loading
        b.publishButton.text = if (loading) getString(R.string.uploading) else getString(R.string.publish)
        b.addImageButton.isEnabled = !loading
    }

    private fun saveDraft() {
        val b = _binding ?: return
        try {
            prefs.edit()
                .putString("${draftKey}_title", b.titleInput.text?.toString()?.trim().orEmpty())
                .putString("${draftKey}_body", b.bodyInput.text?.toString()?.trim().orEmpty())
                .putString("${draftKey}_cat", b.categoryInput.text?.toString()?.trim().orEmpty())
                .apply()
        } catch (_: Exception) {}
    }

    private fun loadDraft() {
        val title = prefs.getString("${draftKey}_title", "") ?: ""
        val body = prefs.getString("${draftKey}_body", "") ?: ""
        val cat = prefs.getString("${draftKey}_cat", "") ?: ""
        if (title.isBlank() && body.isBlank() && cat.isBlank()) return
        isRestoringDraft = true
        if (title.isNotBlank()) binding.titleInput.setText(title)
        if (body.isNotBlank()) binding.bodyInput.setText(body)
        if (cat.isNotBlank()) binding.categoryInput.setText(cat)
        isRestoringDraft = false
        (activity as? MainActivity)?.showToast(getString(R.string.draft_restored))
    }

    private fun clearDraft() {
        prefs.edit()
            .remove("${draftKey}_title")
            .remove("${draftKey}_body")
            .remove("${draftKey}_cat")
            .remove("${draftKey}_cat_idx")
            .apply()
    }

    private fun setupDraftAutoSave() {
        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { if (!isRestoringDraft) saveDraft() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        binding.titleInput.addTextChangedListener(watcher)
        binding.bodyInput.addTextChangedListener(watcher)
        binding.categoryInput.addTextChangedListener(watcher)
    }

    override fun onPause() {
        super.onPause()
        if (!isRestoringDraft) saveDraft()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}