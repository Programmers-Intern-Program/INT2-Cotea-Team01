package com.cotea.service.rag;

import java.util.List;

public interface RagRetrievalService {

    List<RagChunk> retrieve(List<String> tags, int hintLevel, String question);
}
