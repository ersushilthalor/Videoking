package com.example.recorder

import android.media.MediaFormat
import android.util.Size

enum class VideoResolution(val label: String, val width: Int, val height: Int, val defaultBitrate: Int) {
    RES_720P("720p", 720, 1280, 8_000_000),
    RES_1080P("1080p", 1080, 1920, 18_000_000),
    RES_2K("2K", 1440, 2560, 30_000_000),
    RES_4K("4K", 2160, 3840, 50_000_000);

    val size: Size
        get() = Size(width, height)
}

enum class VideoCodec(val label: String, val mimeType: String) {
    H264("H.264 (AVC)", MediaFormat.MIMETYPE_VIDEO_AVC),
    HEVC("H.265 (HEVC)", MediaFormat.MIMETYPE_VIDEO_HEVC)
}

data class EncoderConfig(
    val resolution: VideoResolution = VideoResolution.RES_1080P,
    val fps: Int = 30,
    val codec: VideoCodec = VideoCodec.H264,
    val bitrate: Int = VideoResolution.RES_1080P.defaultBitrate,
    val audioSampleRate: Int = 48000,
    val audioChannels: Int = 2,
    val audioBitrate: Int = 192000
)
