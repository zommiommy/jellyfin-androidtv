package org.jellyfin.androidtv.data.compat

import org.jellyfin.androidtv.preference.constant.VideoUpscalingMode

class VideoOptions : AudioOptions() {
	var audioStreamIndex: Int? = null
	var subtitleStreamIndex: Int? = null
	var alwaysBurnInSubtitleWhenTranscoding: Boolean = false
	var videoUpscalingMode: VideoUpscalingMode = VideoUpscalingMode.NONE
	var videoUpscalingWidth: Int? = null
	var videoUpscalingHeight: Int? = null
}
