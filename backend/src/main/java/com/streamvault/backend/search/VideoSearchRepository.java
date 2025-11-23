package com.streamvault.backend.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface VideoSearchRepository extends ElasticsearchRepository<VideoDocument, String> {

    List<VideoDocument> findByTitleContainingIgnoreCase(String title);

    List<VideoDocument> findByDescriptionContainingIgnoreCase(String description);

    Page<VideoDocument> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
        String title, String description, Pageable pageable
    );
}
