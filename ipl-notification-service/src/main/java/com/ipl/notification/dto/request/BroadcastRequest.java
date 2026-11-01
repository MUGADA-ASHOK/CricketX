package com.ipl.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Message body is required")
    private String message;
}

