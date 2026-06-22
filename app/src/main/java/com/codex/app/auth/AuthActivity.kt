package com.codex.app.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.codex.app.ui.BaseThemedActivity
import androidx.lifecycle.lifecycleScope
import com.codex.app.MainActivity
import com.codex.app.R
import com.codex.app.databinding.ActivityAuthBinding
import com.codex.app.utils.FirebaseHelper
import kotlinx.coroutines.launch

class AuthActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityAuthBinding
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If already signed in, validate ban/role freshness (handles remote ban or role change since last login)
        if (FirebaseHelper.auth.currentUser != null) {
            lifecycleScope.launch {
                val res = FirebaseHelper.loadCurrentUserAndCheckBan()
                if (res.isSuccess) {
                    startMain()
                } else {
                    showError(res.exceptionOrNull()?.message ?: getString(R.string.error_generic))
                    // stay on auth screen (user was signed out inside helper if banned)
                }
            }
            return
        }

        setupListeners()
        updateMode()
    }

    private fun setupListeners() {
        binding.primaryButton.setOnClickListener {
            if (isLoginMode) performLogin() else performRegister()
        }

        binding.switchModeText.setOnClickListener {
            isLoginMode = !isLoginMode
            updateMode()
        }
    }

    private fun updateMode() {
        if (isLoginMode) {
            binding.titleText.text = getString(R.string.login_title)
            binding.primaryButton.text = getString(R.string.login)
            binding.switchModeText.text = getString(R.string.dont_have_account)
            binding.displayNameLayout.visibility = View.GONE
        } else {
            binding.titleText.text = getString(R.string.register_title)
            binding.primaryButton.text = getString(R.string.register)
            binding.switchModeText.text = getString(R.string.already_have_account)
            binding.displayNameLayout.visibility = View.VISIBLE
        }
        binding.statusText.visibility = View.GONE
    }

    private fun performLogin() {
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val pass = binding.passwordInput.text?.toString()?.trim().orEmpty()

        if (email.isEmpty() || pass.isEmpty()) {
            showError("Please enter email and password")
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = FirebaseHelper.loginUser(email, pass)
            setLoading(false)
            result.onSuccess {
                startMain()
            }.onFailure {
                showError(it.message ?: getString(R.string.error_generic))
            }
        }
    }

    private fun performRegister() {
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val pass = binding.passwordInput.text?.toString()?.trim().orEmpty()
        val name = binding.displayNameInput.text?.toString()?.trim().orEmpty()

        if (email.isEmpty() || pass.length < 6) {
            showError("Email and password (min 6 chars) required")
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = FirebaseHelper.registerUser(email, pass, name)
            setLoading(false)
            result.onSuccess {
                Toast.makeText(this@AuthActivity, "Account created. Welcome to Codex!", Toast.LENGTH_SHORT).show()
                startMain()
            }.onFailure {
                showError(it.message ?: getString(R.string.error_generic))
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.primaryButton.isEnabled = !loading
        binding.primaryButton.text = if (loading) getString(R.string.loading) else if (isLoginMode) getString(R.string.login) else getString(R.string.register)
    }

    private fun showError(msg: String) {
        binding.statusText.text = msg
        binding.statusText.visibility = View.VISIBLE
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}