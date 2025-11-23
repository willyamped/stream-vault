package com.streamvault.backend.search;

import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import jakarta.persistence.Id;
import lombok.*;

import java.time.Instant;

@Document(indexName = "videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String thumbnailPath;

    @Field(type = FieldType.Integer)
    private Long durationSeconds;

    @Field(type = FieldType.Integer)
    private Integer width;

    @Field(type = FieldType.Integer)
    private Integer height;

    @Field(type = FieldType.Date)
    private Instant processedAt;

    @Field(type = FieldType.Boolean)
    private Boolean readyForStreaming;
}
