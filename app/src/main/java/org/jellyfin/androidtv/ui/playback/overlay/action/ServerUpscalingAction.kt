package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.VideoUpscalingMode
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter

class ServerUpscalingAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
	private val userPreferences: UserPreferences,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	private var popup: PopupMenu? = null

	init {
		initializeWithIcon(R.drawable.ic_upscaling)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		val currentMode = userPreferences[UserPreferences.videoUpscalingMode]

		videoPlayerAdapter.leanbackOverlayFragment.setFading(false)
		dismissPopup()
		popup = PopupMenu(context, view, Gravity.END).apply {
			VideoUpscalingMode.entries.forEach { mode ->
				menu.add(0, mode.ordinal, mode.ordinal, mode.nameRes).apply {
					isChecked = mode == currentMode
				}
			}
			menu.setGroupCheckable(0, true, true)

			setOnDismissListener {
				videoPlayerAdapter.leanbackOverlayFragment.setFading(true)
				popup = null
			}
			setOnMenuItemClickListener { menuItem ->
				val mode = VideoUpscalingMode.entries[menuItem.itemId]
				if (mode != currentMode) {
					userPreferences[UserPreferences.videoUpscalingMode] = mode
					playbackController.refreshStream()
				}
				dismiss()
				true
			}
		}
		popup?.show()
	}

	fun dismissPopup() {
		popup?.dismiss()
	}
}
