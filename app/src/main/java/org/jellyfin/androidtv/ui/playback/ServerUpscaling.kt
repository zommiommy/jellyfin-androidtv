package org.jellyfin.androidtv.ui.playback

import androidx.media3.common.MimeTypes
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.compat.VideoOptions
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.VideoUpscalingMode
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.androidtv.util.profile.MediaCodecCapabilitiesTest
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Requests the saved server upscaling mode. The target is the display's current mode, capped to what the H.264
 * decoder handles so the upscaled transcode stays playable; the server keeps the source aspect ratio inside it.
 */
fun PlaybackController.applyVideoUpscaling(options: VideoOptions) {
	val userPreferences by fragment.inject<UserPreferences>()
	val mode = userPreferences[UserPreferences.videoUpscalingMode]
	if (mode == VideoUpscalingMode.NONE) return

	@Suppress("DEPRECATION")
	val displayMode = fragment.requireActivity().windowManager.defaultDisplay.mode
	val decoderMax = MediaCodecCapabilitiesTest(userPreferences[UserPreferences.softwareCodecsEnabled])
		.getMaxResolution(MimeTypes.VIDEO_H264)

	options.videoUpscalingMode = mode
	options.videoUpscalingWidth = maxOf(displayMode.physicalWidth, displayMode.physicalHeight).coerceAtMost(decoderMax.width)
	options.videoUpscalingHeight = minOf(displayMode.physicalWidth, displayMode.physicalHeight).coerceAtMost(decoderMax.height)
}

/**
 * Turns server upscaling off when the failed stream used it and restarts playback without it, like jellyfin-web.
 * @return whether the error was handled.
 */
fun PlaybackController.disableFailedVideoUpscaling(): Boolean {
	val url = currentStreamInfo?.mediaUrl ?: return false
	if (!url.contains("videoUpscalingMode=", ignoreCase = true)) return false

	Timber.w("Stream with server upscaling failed, turning upscaling off")
	val userPreferences by fragment.inject<UserPreferences>()
	userPreferences[UserPreferences.videoUpscalingMode] = VideoUpscalingMode.NONE
	Utils.showToast(fragment.requireContext(), R.string.msg_server_upscaling_failed)
	refreshStream()
	return true
}
