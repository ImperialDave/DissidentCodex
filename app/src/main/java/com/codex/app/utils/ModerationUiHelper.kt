package com.codex.app.utils

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.codex.app.R
import com.codex.app.models.Role
import com.codex.app.models.User
import kotlinx.coroutines.launch

object ModerationUiHelper {

    private fun effectiveActor(actorRole: Role): Role =
        if (FirebaseHelper.isFounderAccount()) Role.FOUNDER else actorRole

    fun showRolePicker(
        context: Context,
        scope: LifecycleCoroutineScope,
        target: User,
        actorRole: Role,
        includeFounder: Boolean = false,
        onSuccess: (() -> Unit)? = null
    ) {
        if (!canModifyTarget(context, target, effectiveActor(actorRole))) return

        val actor = effectiveActor(actorRole)
        val options = mutableListOf(
            context.getString(R.string.role_user),
            context.getString(R.string.role_mod),
            context.getString(R.string.role_suspended),
            context.getString(R.string.role_banned)
        )
        if (actor == Role.ADMIN || actor.isFounder()) {
            options.add(0, context.getString(R.string.role_admin))
        }
        if (includeFounder && actor.isFounder()) {
            options.add(context.getString(R.string.role_founder))
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.set_role_for, target.displayName))
            .setItems(options.toTypedArray()) { _, idx ->
                val chosen = roleFromLabel(context, options[idx])
                scope.launch {
                    val res = FirebaseHelper.updateUserRole(target.uid, chosen, actor)
                    if (res.isSuccess) {
                        Toast.makeText(context, context.getString(R.string.action_success), Toast.LENGTH_SHORT).show()
                        onSuccess?.invoke()
                    } else {
                        Toast.makeText(
                            context,
                            res.exceptionOrNull()?.message ?: context.getString(R.string.error_generic),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .show()
    }

    fun showQuickActions(
        context: Context,
        scope: LifecycleCoroutineScope,
        target: User,
        actorRole: Role,
        includeFounder: Boolean = false,
        onSuccess: (() -> Unit)? = null
    ) {
        if (!canModifyTarget(context, target, effectiveActor(actorRole))) return

        val actor = effectiveActor(actorRole)
        val actions = mutableListOf(context.getString(R.string.action_change_role))
        if (!FirebaseHelper.isFounderEmail(target.email)) {
            actions.add(context.getString(R.string.action_suspend))
            actions.add(context.getString(R.string.action_ban))
            actions.add(context.getString(R.string.action_restore_member))
        }

        AlertDialog.Builder(context)
            .setTitle("${target.displayName}\n${target.email}")
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    context.getString(R.string.action_change_role) ->
                        showRolePicker(context, scope, target, actor, includeFounder, onSuccess)
                    context.getString(R.string.action_suspend) ->
                        applyRole(context, scope, target, Role.SUSPENDED, actor, onSuccess)
                    context.getString(R.string.action_ban) ->
                        applyRole(context, scope, target, Role.BANNED, actor, onSuccess)
                    context.getString(R.string.action_restore_member) ->
                        applyRole(context, scope, target, Role.USER, actor, onSuccess)
                }
            }
            .show()
    }

    private fun canModifyTarget(context: Context, target: User, actor: Role): Boolean {
        val currentUid = FirebaseHelper.getCurrentFirebaseUser()?.uid
        if (target.uid == currentUid && !actor.isFounder()) {
            Toast.makeText(context, context.getString(R.string.cannot_modify_self), Toast.LENGTH_SHORT).show()
            return false
        }
        if (FirebaseHelper.isFounderEmail(target.email) && !actor.isFounder()) {
            Toast.makeText(context, "The founder account cannot be modified.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun applyRole(
        context: Context,
        scope: LifecycleCoroutineScope,
        target: User,
        role: Role,
        actorRole: Role,
        onSuccess: (() -> Unit)?
    ) {
        scope.launch {
            val res = FirebaseHelper.updateUserRole(target.uid, role, actorRole)
            if (res.isSuccess) {
                Toast.makeText(context, context.getString(R.string.action_success), Toast.LENGTH_SHORT).show()
                onSuccess?.invoke()
            } else {
                Toast.makeText(
                    context,
                    res.exceptionOrNull()?.message ?: context.getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun roleFromLabel(context: Context, label: String): Role = when (label) {
        context.getString(R.string.role_founder) -> Role.FOUNDER
        context.getString(R.string.role_admin) -> Role.ADMIN
        context.getString(R.string.role_mod) -> Role.MOD
        context.getString(R.string.role_suspended) -> Role.SUSPENDED
        context.getString(R.string.role_banned) -> Role.BANNED
        else -> Role.USER
    }
}