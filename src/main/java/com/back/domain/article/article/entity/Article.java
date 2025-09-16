package com.back.domain.article.article.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Article {
    long id;
    LocalDateTime createdDate;
    LocalDateTime modifiedDate;
    String title;
    String body;
    @JsonProperty("isBlind") boolean isBlind;
}
