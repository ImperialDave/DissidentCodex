package com.codex.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codex.app.R
import com.codex.app.databinding.BottomSheetGifPickerBinding
import com.codex.app.databinding.ItemGifThumbBinding
import com.codex.app.models.MediaType
import com.codex.app.utils.GiphyApi
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GifPickerBottomSheet : BottomSheetDialogFragment() {

    sealed class Selection {
        data class Local(val uri: Uri, val mediaType: String) : Selection()
        data class Remote(val url: String, val mediaType: String) : Selection()
    }

    interface Listener {
        fun onGifSelected(selection: Selection)
    }

    private var _binding: BottomSheetGifPickerBinding? = null
    private val binding get() = _binding!!
    private var searchJob: Job? = null
    private var showingGallery = true

    private val pickGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                (parentFragment as? Listener ?: activity as? Listener)?.onGifSelected(
                    Selection.Local(uri, MediaType.GIF)
                )
                dismiss()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetGifPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.gifRecycler.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.tabGallery.setOnClickListener { showGalleryTab() }
        binding.tabGiphy.setOnClickListener { showGiphyTab() }
        binding.pickFromGalleryBtn.setOnClickListener {
            pickGallery.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" })
        }
        binding.gifSearchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!showingGallery) scheduleSearch(s?.toString().orEmpty())
            }
        })
        showGalleryTab()
    }

    private fun showGalleryTab() {
        showingGallery = true
        binding.galleryPanel.visibility = View.VISIBLE
        binding.giphyPanel.visibility = View.GONE
        binding.tabGallery.isChecked = true
        binding.tabGiphy.isChecked = false
    }

    private fun showGiphyTab() {
        showingGallery = false
        binding.galleryPanel.visibility = View.GONE
        binding.giphyPanel.visibility = View.VISIBLE
        binding.tabGallery.isChecked = false
        binding.tabGiphy.isChecked = true
        if (!GiphyApi.isConfigured()) {
            binding.giphyEmptyText.visibility = View.VISIBLE
            binding.giphyEmptyText.text = getString(R.string.giphy_not_configured)
            binding.gifRecycler.adapter = null
            return
        }
        scheduleSearch(binding.gifSearchInput.text?.toString().orEmpty())
    }

    private fun scheduleSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(350)
            loadGiphy(query)
        }
    }

    private fun loadGiphy(query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.giphyProgress.visibility = View.VISIBLE
            binding.giphyEmptyText.visibility = View.GONE
            val result = GiphyApi.search(query)
            binding.giphyProgress.visibility = View.GONE
            if (!isAdded) return@launch

            when (result) {
                is GiphyApi.SearchResult.NotConfigured -> {
                    binding.giphyEmptyText.visibility = View.VISIBLE
                    binding.giphyEmptyText.text = getString(R.string.giphy_not_configured)
                    binding.gifRecycler.adapter = null
                }
                is GiphyApi.SearchResult.Error -> {
                    binding.giphyEmptyText.visibility = View.VISIBLE
                    binding.giphyEmptyText.text = result.message.ifBlank {
                        getString(R.string.giphy_load_error)
                    }
                    binding.gifRecycler.adapter = null
                }
                is GiphyApi.SearchResult.Success -> {
                    if (result.items.isEmpty()) {
                        binding.giphyEmptyText.visibility = View.VISIBLE
                        binding.giphyEmptyText.text = getString(R.string.giphy_no_results)
                        binding.gifRecycler.adapter = null
                    } else {
                        binding.giphyEmptyText.visibility = View.GONE
                        binding.gifRecycler.adapter = GifAdapter(result.items) { item ->
                            (parentFragment as? Listener ?: activity as? Listener)?.onGifSelected(
                                Selection.Remote(item.fullUrl, MediaType.GIF)
                            )
                            dismiss()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    private class GifAdapter(
        private val items: List<GiphyApi.GifItem>,
        private val onClick: (GiphyApi.GifItem) -> Unit
    ) : RecyclerView.Adapter<GifAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemGifThumbBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        override fun getItemCount() = items.size

        inner class VH(private val b: ItemGifThumbBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: GiphyApi.GifItem) {
                Glide.with(b.gifThumb).asGif().load(item.previewUrl).centerCrop().into(b.gifThumb)
                b.root.setOnClickListener { onClick(item) }
            }
        }
    }

    companion object {
        const val TAG = "GifPickerBottomSheet"

        fun show(host: androidx.fragment.app.FragmentActivity) {
            GifPickerBottomSheet().show(host.supportFragmentManager, TAG)
        }
    }
}