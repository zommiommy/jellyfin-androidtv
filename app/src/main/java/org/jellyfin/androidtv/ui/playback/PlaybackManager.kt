package org.jellyfin.androidtv.ui.playback

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.jellyfin.androidtv.data.compat.PlaybackException
import org.jellyfin.androidtv.data.compat.StreamInfo
import org.jellyfin.androidtv.data.compat.VideoOptions
import org.jellyfin.androidtv.util.apiclient.Response
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.post
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import java.util.UUID

private fun createStreamInfo(
	api: ApiClient,
	options: VideoOptions,
	response: PlaybackInfoResponse,
): StreamInfo = StreamInfo().apply {
	val source = response.mediaSources.firstOrNull {
		options.mediaSourceId != null && it.id == options.mediaSourceId
	} ?: response.mediaSources.firstOrNull()

	itemId = options.itemId
	mediaSource = source
	runTimeTicks = source?.runTimeTicks
	playSessionId = response.playSessionId

	if (source == null) return@apply

	if (options.enableDirectPlay && source.supportsDirectPlay) {
		playMethod = PlayMethod.DIRECT_PLAY
		container = source.container
		mediaUrl = when {
			source.isRemote && source.path != null -> source.path
			else -> api.videosApi.getVideoStreamUrl(
				itemId = itemId,
				container = container,
				mediaSourceId = source.id,
				static = true,
				tag = source.eTag,
				liveStreamId = source.liveStreamId,
			)
		}
	} else if (options.enableDirectStream && source.supportsDirectStream) {
		playMethod = PlayMethod.DIRECT_STREAM
		container = source.transcodingContainer
		mediaUrl = api.createUrl(requireNotNull(source.transcodingUrl), ignorePathParameters = true)
	} else if (source.supportsTranscoding) {
		playMethod = PlayMethod.TRANSCODE
		container = source.transcodingContainer
		mediaUrl = api.createUrl(requireNotNull(source.transcodingUrl), ignorePathParameters = true)
	}
}

class PlaybackManager(
	private val api: ApiClient
) {
	fun getVideoStreamInfo(
		lifecycleOwner: LifecycleOwner,
		options: VideoOptions,
		startTimeTicks: Long,
		callback: Response<StreamInfo>,
	) = lifecycleOwner.lifecycleScope.launch {
		getVideoStreamInfoInternal(options, startTimeTicks).fold(
			onSuccess = { callback.onResponse(it) },
			onFailure = { callback.onError(Exception(it)) },
		)
	}

	fun changeVideoStream(
		lifecycleOwner: LifecycleOwner,
		stream: StreamInfo,
		options: VideoOptions,
		startTimeTicks: Long,
		callback: Response<StreamInfo>
	) = lifecycleOwner.lifecycleScope.launch {
		if (stream.playSessionId != null && stream.playMethod != PlayMethod.DIRECT_PLAY) {
			withContext(Dispatchers.IO) {
				api.hlsSegmentApi.stopEncodingProcess(api.deviceInfo.id, stream.playSessionId)
			}
		}

		getVideoStreamInfoInternal(options, startTimeTicks).fold(
			onSuccess = { callback.onResponse(it) },
			onFailure = { callback.onError(Exception(it)) },
		)
	}

	private suspend fun getVideoStreamInfoInternal(
		options: VideoOptions,
		startTimeTicks: Long
	) = runCatching {
		val itemId = requireNotNull(options.itemId) { "Item id cannot be null" }
		val data = PlaybackInfoDto(
			mediaSourceId = options.mediaSourceId,
			startTimeTicks = startTimeTicks,
			deviceProfile = options.profile,
			enableDirectStream = options.enableDirectStream,
			enableDirectPlay = options.enableDirectPlay,
			maxAudioChannels = options.maxAudioChannels,
			audioStreamIndex = options.audioStreamIndex.takeIf { it != null && it >= 0 },
			subtitleStreamIndex = options.subtitleStreamIndex,
			allowVideoStreamCopy = true,
			allowAudioStreamCopy = true,
			autoOpenLiveStream = true,
			alwaysBurnInSubtitleWhenTranscoding = options.alwaysBurnInSubtitleWhenTranscoding,
		)
		val response = withContext(Dispatchers.IO) {
			getPostedPlaybackInfo(itemId, data, options)
		}

		if (response.errorCode != null) {
			throw PlaybackException().apply {
				errorCode = response.errorCode!!
			}
		}

		createStreamInfo(api, options, response)
	}

	/**
	 * The SDK's [PlaybackInfoDto] lacks the server's upscaling fields, so an upscaling request posts the
	 * same body as JSON with those fields added.
	 */
	private suspend fun getPostedPlaybackInfo(
		itemId: UUID,
		data: PlaybackInfoDto,
		options: VideoOptions,
	): PlaybackInfoResponse {
		val upscalingMode = options.videoUpscalingMode.serverValue
			?: return api.mediaInfoApi.getPostedPlaybackInfo(itemId, data).content

		val body = buildJsonObject {
			ApiSerializer.json.encodeToJsonElement(PlaybackInfoDto.serializer(), data).jsonObject
				.forEach { (key, value) -> put(key, value) }
			put("VideoUpscalingMode", upscalingMode)
			put("VideoUpscalingWidth", options.videoUpscalingWidth)
			put("VideoUpscalingHeight", options.videoUpscalingHeight)
		}

		return api.post<PlaybackInfoResponse>(
			"/Items/{itemId}/PlaybackInfo",
			pathParameters = mapOf("itemId" to itemId),
			requestBody = body,
		).content
	}
}
