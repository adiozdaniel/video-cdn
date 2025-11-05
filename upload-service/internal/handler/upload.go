package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"upload-service/internal/service"
)

type UploadHandler struct {
	service *service.UploadService
}

func NewUploadHandler(service *service.UploadService) *UploadHandler {
	return &UploadHandler{
		service: service,
	}
}

type InitiateRequest struct {
	Filename string `json:"filename" binding:"required"`
	Size     int64  `json:"size" binding:"required,min=1"`
}

type InitiateResponse struct {
	VideoID   string `json:"videoId"`
	UploadURL string `json:"uploadUrl"`
	ExpiresIn int    `json:"expiresIn"` // seconds
}

type StatusResponse struct {
	VideoID    string `json:"videoId"`
	Status     string `json:"status"`
	Filename   string `json:"filename"`
	Size       int64  `json:"size"`
	UploadedAt string `json:"uploadedAt,omitempty"`
}

// Initiate generates a presigned URL for direct upload to MinIO
// This is the critical path for throughput - must be < 2ms
func (h *UploadHandler) Initiate(c *gin.Context) {
	var req InitiateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	// Validate file size (max 10GB)
	if req.Size > 10*1024*1024*1024 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "file size exceeds 10GB limit"})
		return
	}

	resp, err := h.service.InitiateUpload(c.Request.Context(), req.Filename, req.Size)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to initiate upload"})
		return
	}

	c.JSON(http.StatusOK, InitiateResponse{
		VideoID:   resp.VideoID,
		UploadURL: resp.UploadURL,
		ExpiresIn: resp.ExpiresIn,
	})
}

// Complete marks upload as complete and queues processing job
func (h *UploadHandler) Complete(c *gin.Context) {
	videoID := c.Param("id")
	if videoID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "video ID is required"})
		return
	}

	if err := h.service.CompleteUpload(c.Request.Context(), videoID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to complete upload"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"status":  "queued",
		"videoId": videoID,
		"message": "video queued for processing",
	})
}

// Cancel cancels an ongoing upload
func (h *UploadHandler) Cancel(c *gin.Context) {
	videoID := c.Param("id")
	if videoID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "video ID is required"})
		return
	}

	if err := h.service.CancelUpload(c.Request.Context(), videoID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to cancel upload"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"status":  "cancelled",
		"videoId": videoID,
	})
}

// Status returns the current upload status
func (h *UploadHandler) Status(c *gin.Context) {
	videoID := c.Param("id")
	if videoID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "video ID is required"})
		return
	}

	status, err := h.service.GetUploadStatus(c.Request.Context(), videoID)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "video not found"})
		return
	}

	c.JSON(http.StatusOK, status)
}
