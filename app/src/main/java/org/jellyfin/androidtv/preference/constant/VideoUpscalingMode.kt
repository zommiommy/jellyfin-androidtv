package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

/**
 * Video upscaling applied by the server while transcoding. [serverValue] is the server's
 * VideoUpscalingMode name; NONE sends nothing.
 */
enum class VideoUpscalingMode(
	val serverValue: String?,
	override val nameRes: Int,
) : PreferenceEnum {
	NONE(null, R.string.lbl_none),
	FSR("Fsr", R.string.upscaling_fsr),
	ANIME4K_A("Anime4KModeA", R.string.upscaling_anime4k_a),
	ANIME4K_B("Anime4KModeB", R.string.upscaling_anime4k_b),
	ANIME4K_C("Anime4KModeC", R.string.upscaling_anime4k_c),
	ANIME4K_A_A("Anime4KModeAPlusA", R.string.upscaling_anime4k_a_a),
	ANIME4K_B_B("Anime4KModeBPlusB", R.string.upscaling_anime4k_b_b),
	ANIME4K_C_A("Anime4KModeCPlusA", R.string.upscaling_anime4k_c_a),
	NVIDIA_VSR_LOW("NvidiaVsrLow", R.string.upscaling_nvidia_vsr_low),
	NVIDIA_VSR_MEDIUM("NvidiaVsrMedium", R.string.upscaling_nvidia_vsr_medium),
	NVIDIA_VSR_HIGH("NvidiaVsrHigh", R.string.upscaling_nvidia_vsr_high),
	NVIDIA_VSR_ULTRA("NvidiaVsrUltra", R.string.upscaling_nvidia_vsr_ultra),
	NVIDIA_VSR_Q_LOW("NvidiaVsrQLow", R.string.upscaling_nvidia_vsr_q_low),
	NVIDIA_VSR_Q_MEDIUM("NvidiaVsrQMedium", R.string.upscaling_nvidia_vsr_q_medium),
	NVIDIA_VSR_Q_HIGH("NvidiaVsrQHigh", R.string.upscaling_nvidia_vsr_q_high),
	NVIDIA_VSR_Q_ULTRA("NvidiaVsrQUltra", R.string.upscaling_nvidia_vsr_q_ultra),
}
