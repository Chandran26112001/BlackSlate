package com.example.blackslate

import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** A deliberately minimal assistant: it opens Black Slate and never listens or reads screen context. */
class BlackSlateVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setDisabledShowContext(VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)
        }
    }
    override fun onLaunchVoiceAssistFromKeyguard() {
        super.onLaunchVoiceAssistFromKeyguard()
        removeLegacyAssistantTasks(this)
        startActivity(Intent(this, LockedLogActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
}

class BlackSlateVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = BlackSlateVoiceSession(this)
}

private class BlackSlateVoiceSession(context: Context) : VoiceInteractionSession(context) {
    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setUiEnabled(false)
    }
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val target = if (keyguard?.isKeyguardLocked == true) LockedLogActivity::class.java else MainActivity::class.java
        removeLegacyAssistantTasks(context)
        val openBlackSlate = Intent(context, target).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("blackslate://open")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(openBlackSlate)
        finish()
    }
}

/** Remove cards created by older versions that launched MainActivity as an assistant task. */
internal fun removeLegacyAssistantTasks(context: Context, currentTaskId: Int? = null) {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
    manager.appTasks.forEach { task ->
        val info = runCatching { task.taskInfo }.getOrNull() ?: return@forEach
        val base = info.baseIntent
        if (info.taskId != currentTaskId && base?.component?.className == MainActivity::class.java.name &&
            base.action == Intent.ACTION_VIEW && base.data?.scheme == "blackslate" && base.data?.host == "open") {
            runCatching { task.finishAndRemoveTask() }
        }
    }
}

/** Required by Android's assistant contract on older releases; Black Slate does not record audio. */
class BlackSlateRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }
    override fun onCancel(listener: Callback?) = Unit
    override fun onStopListening(listener: Callback?) = Unit
}

fun isBlackSlateAssistant(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
    context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
