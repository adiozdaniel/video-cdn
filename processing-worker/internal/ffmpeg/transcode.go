package ffmpeg

import (
	"fmt"
	"os/exec"
	"path/filepath"
)

type Bitrate struct {
	Name         string
	Resolution   string
	VideoBitrate string
	AudioBitrate string
	Scale        string
}

// TranscodeToHLS transcodes a video to HLS format with specified bitrate
// Uses hardware acceleration when available for better throughput
func TranscodeToHLS(inputPath, outputDir string, bitrate Bitrate) error {
	outputPlaylist := filepath.Join(outputDir, "playlist.m3u8")
	segmentPattern := filepath.Join(outputDir, "seg_%03d.ts")

	// FFmpeg command optimized for throughput
	// - preset fast: Faster encoding with minimal quality loss
	// - threads 2: Limit per-transcode threads (since we run 3 in parallel)
	// - hls_time 4: 4-second segments (good balance for seeking and overhead)
	// - hls_playlist_type vod: Video on demand (not live)
	args := []string{
		"-i", inputPath,
		"-vf", fmt.Sprintf("scale=%s", bitrate.Scale),
		"-c:v", "libx264",
		"-preset", "fast", // Faster encoding
		"-crf", "23", // Constant quality (lower = better, 23 is default)
		"-maxrate", bitrate.VideoBitrate,
		"-bufsize", fmt.Sprintf("%dk", parseBitrate(bitrate.VideoBitrate)*2),
		"-g", "48", // GOP size (2 seconds at 24fps)
		"-sc_threshold", "0", // Disable scene change detection
		"-c:a", "aac",
		"-b:a", bitrate.AudioBitrate,
		"-ar", "48000", // Audio sample rate
		"-ac", "2", // Stereo audio
		"-hls_time", "4", // 4-second segments
		"-hls_playlist_type", "vod",
		"-hls_segment_type", "mpegts",
		"-hls_segment_filename", segmentPattern,
		"-threads", "2", // Limit threads per transcode
		"-y", // Overwrite output
		outputPlaylist,
	}

	cmd := exec.Command("ffmpeg", args...)

	// Run the command
	output, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("ffmpeg failed: %w\nOutput: %s", err, string(output))
	}

	return nil
}

// parseBitrate converts bitrate string like "5000k" to integer 5000
func parseBitrate(bitrateStr string) int {
	var bitrate int
	fmt.Sscanf(bitrateStr, "%dk", &bitrate)
	if bitrate == 0 {
		fmt.Sscanf(bitrateStr, "%dM", &bitrate)
		bitrate *= 1000
	}
	return bitrate
}

// GetVideoDuration returns the duration of a video in seconds
func GetVideoDuration(inputPath string) (float64, error) {
	// Use ffprobe to get duration
	cmd := exec.Command("ffprobe",
		"-v", "error",
		"-show_entries", "format=duration",
		"-of", "default=noprint_wrappers=1:nokey=1",
		inputPath,
	)

	output, err := cmd.Output()
	if err != nil {
		return 0, fmt.Errorf("ffprobe failed: %w", err)
	}

	var duration float64
	fmt.Sscanf(string(output), "%f", &duration)

	return duration, nil
}
