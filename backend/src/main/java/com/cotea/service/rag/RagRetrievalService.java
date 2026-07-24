package com.cotea.service.rag;

import java.util.List;

public interface RagRetrievalService {

    List<RagChunk> retrieve(List<String> tags, List<String> subcategories, int hintLevel, String question);
}
