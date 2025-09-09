package com.back;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class Article {

    private Long id;
    private String title;
    private String body;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
    private boolean isBlind;
}
