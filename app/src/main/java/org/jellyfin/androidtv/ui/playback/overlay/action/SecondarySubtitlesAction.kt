package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.androidtv.ui.playback.secondarySubtitleStreams

class SecondarySubtitlesAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	private var popup: PopupMenu? = null

	init {
		initializeWithIcon(R.drawable.ic_subtitles)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		val secondarySubtitles = playbackController.secondarySubtitles ?: return
		val itemId = playbackController.currentlyPlayingItem?.id ?: return
		val mediaSource = playbackController.currentMediaSource ?: return

		videoPlayerAdapter.leanbackOverlayFragment.setFading(false)
		dismissPopup()
		popup = PopupMenu(context, view, Gravity.END).apply {
			with(menu) {
				var order = 0
				add(0, -1, order++, context.getString(R.string.lbl_none)).apply {
					isChecked = secondarySubtitles.activeIndex == -1
				}

				for (stream in secondarySubtitleStreams(mediaSource)) {
					add(0, stream.index, order++, stream.displayTitle).apply {
						isChecked = stream.index == secondarySubtitles.activeIndex
					}
				}

				setGroupCheckable(0, true, true)
			}
			setOnDismissListener {
				videoPlayerAdapter.leanbackOverlayFragment.setFading(true)
				popup = null
			}
			setOnMenuItemClickListener { item ->
				secondarySubtitles.select(itemId, mediaSource, item.itemId)
				true
			}
		}
		popup?.show()
	}

	fun dismissPopup() {
		popup?.dismiss()
	}
}
