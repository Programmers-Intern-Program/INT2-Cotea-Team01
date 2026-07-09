package com.cotea.service.rag;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RagChunk {

    private final String source;
    private final String chunkId;
    private final String content;
    private final double distance;
}
