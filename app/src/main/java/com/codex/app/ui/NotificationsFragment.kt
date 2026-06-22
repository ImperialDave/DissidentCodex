package com.codex.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codex.app.databinding.FragmentNotificationsBinding
import com.codex.app.adapters.NotificationAdapter
import com.codex.app.models.AppNotification
import com.codex.app.utils.NotificationHelper
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NotificationAdapter
    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = NotificationAdapter { notif -> openNotification(notif) }
        binding.notificationsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.notificationsRecycler.adapter = adapter

        binding.markAllReadBtn.setOnClickListener {
            lifecycleScope.launch {
                val res = NotificationHelper.markAllRead()
                if (res.isFailure) {
                    Toast.makeText(
                        requireContext(),
                        res.exceptionOrNull()?.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.loadingBar.visibility = View.VISIBLE
        listener = NotificationHelper.listenNotifications(
            onUpdate = { list ->
                if (_binding == null) return@listenNotifications
                binding.loadingBar.visibility = View.GONE
                adapter.submitList(list)
                binding.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            },
            onError = { err ->
                if (_binding == null) return@listenNotifications
                binding.loadingBar.visibility = View.GONE
                Toast.makeText(requireContext(), err.message, Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onStop() {
        listener?.remove()
        listener = null
        super.onStop()
    }

    private fun openNotification(notif: AppNotification) {
        lifecycleScope.launch {
            if (!notif.read) {
                NotificationHelper.markRead(notif.id)
            }
            when (notif.type) {
                AppNotification.TYPE_CHESS_TURN -> {
                    val gameId = notif.targetId ?: return@launch
                    startActivity(ChessGameActivity.intent(requireContext(), gameId))
                }
                AppNotification.TYPE_POST_LIKE,
                AppNotification.TYPE_POST_COMMENT,
                AppNotification.TYPE_COMMENT_REPLY -> {
                    val postId = notif.targetId ?: return@launch
                    startActivity(
                        Intent(requireContext(), PostDetailActivity::class.java)
                            .putExtra("postId", postId)
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}