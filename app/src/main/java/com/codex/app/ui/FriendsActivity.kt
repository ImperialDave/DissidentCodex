package com.codex.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codex.app.R
import com.codex.app.adapters.FriendAdapter
import com.codex.app.adapters.FriendRequestAdapter
import com.codex.app.databinding.ActivityFriendsBinding
import com.codex.app.databinding.FragmentFriendsListBinding
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.WindowInsetsHelper
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class FriendsActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityFriendsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        WindowInsetsHelper.applyTopSafeArea(binding.toolbar)

        val pagerAdapter = FriendsPagerAdapter(this)
        binding.friendsPager.adapter = pagerAdapter
        TabLayoutMediator(binding.friendsTabLayout, binding.friendsPager) { tab, pos ->
            tab.text = when (pos) {
                0 -> getString(R.string.friends)
                else -> getString(R.string.friend_requests)
            }
        }.attach()
    }

    class FriendsListFragment : androidx.fragment.app.Fragment(R.layout.fragment_friends_list) {
        private var _binding: FragmentFriendsListBinding? = null
        private lateinit var adapter: FriendAdapter

        override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            _binding = FragmentFriendsListBinding.bind(view)
            adapter = FriendAdapter { friend ->
                startActivity(UserProfileActivity.intent(requireContext(), friend.uid))
            }
            _binding?.friendsRecycler?.layoutManager = LinearLayoutManager(requireContext())
            _binding?.friendsRecycler?.adapter = adapter
            loadFriends()
        }

        private fun loadFriends() {
            val uid = FirebaseHelper.getCurrentFirebaseUser()?.uid ?: return
            viewLifecycleOwner.lifecycleScope.launch {
                val friends = FirebaseHelper.getFriends(uid)
                adapter.submitList(friends)
                _binding?.emptyText?.visibility = if (friends.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        override fun onResume() {
            super.onResume()
            if (_binding != null) loadFriends()
        }

        override fun onDestroyView() {
            _binding = null
            super.onDestroyView()
        }
    }

    class FriendRequestsFragment : androidx.fragment.app.Fragment(R.layout.fragment_friends_list) {
        private var _binding: FragmentFriendsListBinding? = null
        private lateinit var adapter: FriendRequestAdapter

        override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            _binding = FragmentFriendsListBinding.bind(view)
            _binding?.emptyText?.text = getString(R.string.no_friend_requests)
            adapter = FriendRequestAdapter(
                onAccept = { req -> respond(req, true) },
                onDecline = { req -> respond(req, false) }
            )
            _binding?.friendsRecycler?.layoutManager = LinearLayoutManager(requireContext())
            _binding?.friendsRecycler?.adapter = adapter
            loadRequests()
        }

        private fun loadRequests() {
            viewLifecycleOwner.lifecycleScope.launch {
                val requests = FirebaseHelper.getIncomingFriendRequests()
                val rows = requests.map { req ->
                    FriendRequestAdapter.RequestRow(req, FirebaseHelper.fetchUser(req.fromUid))
                }
                adapter.submitList(rows)
                _binding?.emptyText?.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        private fun respond(request: com.codex.app.models.FriendRequest, accept: Boolean) {
            viewLifecycleOwner.lifecycleScope.launch {
                val res = FirebaseHelper.respondToFriendRequest(request.id, accept)
                if (res.isSuccess) {
                    Toast.makeText(
                        requireContext(),
                        if (accept) R.string.friend_added else R.string.request_declined,
                        Toast.LENGTH_SHORT
                    ).show()
                    loadRequests()
                } else {
                    Toast.makeText(
                        requireContext(),
                        res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        override fun onResume() {
            super.onResume()
            if (_binding != null) loadRequests()
        }

        override fun onDestroyView() {
            _binding = null
            super.onDestroyView()
        }
    }

    private class FriendsPagerAdapter(activity: FriendsActivity) :
        androidx.viewpager2.adapter.FragmentStateAdapter(activity) {
        override fun getItemCount() = 2
        override fun createFragment(position: Int) = when (position) {
            0 -> FriendsListFragment()
            else -> FriendRequestsFragment()
        }
    }
}