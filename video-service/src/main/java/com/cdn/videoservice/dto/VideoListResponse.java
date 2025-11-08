package com.cdn.videoservice.dto;

import com.cdn.videoservice.model.Video;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class VideoListResponse {
    private List<Video> videos;
    private long total;
    private int page;
    private int size;
}
