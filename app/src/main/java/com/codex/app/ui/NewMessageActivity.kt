package com.codex.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codex.app.R
import com.codex.app.adapters.UserPickAdapter
import com.codex.app.databinding.ActivityNewMessageBinding
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.WindowInsetsHelper
import kotlinx.coroutines.launch

class NewMessageActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityNewMessageBinding
    private lateinit var adapter: UserPickAdapter
    private var allUsers: List<com.codex.app.models.User> = emptyList()
    private val myUid = FirebaseHelper.getCurrentFirebaseUser()?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewMessageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        WindowInsetsHelper.applyBottomSafeArea(binding.root)

        adapter = UserPickAdapter(
            onUserClick = { user ->
            lifecycleScope.launch {
                val res = FirebaseHelper.getOrCreateDmRoom(user.uid)
                if (res.isSuccess) {
                    val room = res.getOrThrow()
                    startActivity(
                        Intent(this@NewMessageActivity, ChatRoomActivity::class.java)
                            .putExtra(ChatRoomActivity.EXTRA_ROOM_ID, room.id)
                            .putExtra(ChatRoomActivity.EXTRA_ROOM_TITLE, room.title)
                    )
                    finish()
                } else {
                    Toast.makeText(
                        this@NewMessageActivity,
                        res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
            onUserLongClick = { user ->
                startActivity(UserProfileActivity.intent(this, user.uid))
            }
        )
        binding.usersRecycler.layoutManager = LinearLayoutManager(this)
        binding.usersRecycler.adapter = adapter

        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { applyFilter() }
        })

        loadUsers()
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            allUsers = FirebaseHelper.getUsersForMessaging(200).filter { it.uid != myUid }
            applyFilter()
        }
    }

    private fun applyFilter() {
        val q = binding.searchInput.text?.toString().orEmpty()
        val filtered = FirebaseHelper.filterUsers(allUsers, q)
        adapter.submitList(filtered)
        binding.emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}