package com.cotea.service.rag;

import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "cotea.rag", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpRagRetrievalService implements RagRetrievalService {

    @Override
    public List<RagChunk> retrieve(List<String> tags, int hintLevel, String question) {
        return Collections.emptyList();
    }
}
