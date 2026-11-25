package org.jellyfin.androidtv.ui.playback

import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.annotation.OptIn
import androidx.core.text.HtmlCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import java.util.UUID

/**
 * Text subtitle streams of [mediaSource]. The server converts any of them to timed text events, so they can be
 * shown as a secondary subtitle regardless of how the primary subtitle is delivered.
 */
fun secondarySubtitleStreams(mediaSource: MediaSourceInfo?): List<MediaStream> =
	mediaSource?.mediaStreams.orEmpty().filter { it.type == MediaStreamType.SUBTITLE && it.isTextSubtitleStream }

/**
 * Shows a second subtitle track above the video, independent of the player's own subtitle rendering. The selected
 * track is kept across queue items: the same stream index when its language matches, otherwise the first stream
 * with the same language.
 */
@OptIn(UnstableApi::class)
class SecondarySubtitles(
	private val player: Player,
	private val view: SubtitleView,
	private val api: ApiClient,
	private val lifecycleOwner: LifecycleOwner,
) {
	private class TrackEvent(val startMs: Long, val endMs: Long, val text: Spanned)

	private val handler = Handler(Looper.getMainLooper())
	private val renderLoop = object : Runnable {
		override fun run() {
			render()
			handler.postDelayed(this, RENDER_INTERVAL_MS)
		}
	}

	private var preferredIndex = -1
	private var preferredLanguage: String? = null
	private var events: List<TrackEvent> = emptyList()
	private var shownEvents: List<TrackEvent> = emptyList()
	private var loadJob: Job? = null

	/** Index of the stream shown for the current item, or -1. */
	var activeIndex = -1
		private set

	/** Shows stream [index] of [mediaSource] and remembers it for the next items; -1 turns it off. */
	fun select(itemId: UUID, mediaSource: MediaSourceInfo, index: Int) {
		val stream = secondarySubtitleStreams(mediaSource).firstOrNull { it.index == index }
		preferredIndex = stream?.index ?: -1
		preferredLanguage = stream?.language
		load(itemId, mediaSource, stream)
	}

	/** Applies the remembered choice to a newly started item. */
	fun restore(itemId: UUID, mediaSource: MediaSourceInfo) {
		val streams = secondarySubtitleStreams(mediaSource)
		val stream = if (preferredIndex == -1) null else {
			streams.firstOrNull { it.index == preferredIndex && it.language == preferredLanguage }
				?: preferredLanguage?.let { language -> streams.firstOrNull { it.language == language } }
		}
		load(itemId, mediaSource, stream)
	}

	fun release() {
		loadJob?.cancel()
		handler.removeCallbacks(renderLoop)
	}

	private fun load(itemId: UUID, mediaSource: MediaSourceInfo, stream: MediaStream?) {
		release()
		activeIndex = stream?.index ?: -1
		events = emptyList()
		show(emptyList())

		val mediaSourceId = mediaSource.id
		if (stream == null || mediaSourceId == null) return

		loadJob = lifecycleOwner.lifecycleScope.launch {
			val loaded = try {
				withContext(Dispatchers.IO) { fetchEvents(itemId, mediaSourceId, stream.index) }
			} catch (err: CancellationException) {
				throw err
			} catch (err: Exception) {
				Timber.e(err, "Failed to load secondary subtitle stream ${stream.index}")
				return@launch
			}

			events = loaded
			handler.post(renderLoop)
		}
	}

	private suspend fun fetchEvents(itemId: UUID, mediaSourceId: String, index: Int): List<TrackEvent> {
		val trackInfo = api.get<JsonObject>(
			"/Videos/{itemId}/{mediaSourceId}/Subtitles/{index}/Stream.js",
			pathParameters = mapOf(
				"itemId" to itemId,
				"mediaSourceId" to mediaSourceId,
				"index" to index,
			),
		).content

		return trackInfo["TrackEvents"]?.jsonArray.orEmpty().mapNotNull { element ->
			val event = element.jsonObject
			val text = event["Text"]?.jsonPrimitive?.contentOrNull?.let(::formatEventText) ?: return@mapNotNull null
			TrackEvent(
				startMs = event.getValue("StartPositionTicks").jsonPrimitive.long / TICKS_PER_MS,
				endMs = event.getValue("EndPositionTicks").jsonPrimitive.long / TICKS_PER_MS,
				text = text,
			)
		}.sortedBy { it.startMs }
	}

	private fun render() {
		val position = player.currentPosition
		val active = events.filter { position >= it.startMs && position < it.endMs }
		if (active != shownEvents) show(active)
	}

	private fun show(active: List<TrackEvent>) {
		shownEvents = active
		if (active.isEmpty()) {
			view.setCues(emptyList())
			return
		}

		val text = SpannableStringBuilder()
		active.forEachIndexed { i, event ->
			if (i > 0) text.append('\n')
			text.append(event.text)
		}
		view.setCues(
			listOf(
				Cue.Builder()
					.setText(text)
					.setLine(TOP_LINE_FRACTION, Cue.LINE_TYPE_FRACTION)
					.setLineAnchor(Cue.ANCHOR_TYPE_START)
					.build()
			)
		)
	}

	private companion object {
		const val RENDER_INTERVAL_MS = 100L
		const val TICKS_PER_MS = 10_000L
		const val TOP_LINE_FRACTION = 0.05f

		val assOverrideTags = Regex("""\{\\[^}]*\}""")

		/** Mirrors jellyfin-web: ASS line breaks become newlines, ASS override tags are dropped, HTML tags apply. */
		fun formatEventText(raw: String): Spanned? {
			val text = assOverrideTags.replace(raw.replace("\\N", "\n").replace("\\n", "\n").replace("\r", ""), "").trim()
			if (text.isEmpty()) return null
			return HtmlCompat.fromHtml(text.replace("\n", "<br>"), HtmlCompat.FROM_HTML_MODE_LEGACY)
		}
	}
}
