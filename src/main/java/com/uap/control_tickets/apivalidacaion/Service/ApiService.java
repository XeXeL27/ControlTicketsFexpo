package com.uap.control_tickets.apivalidacaion.Service;

import com.uap.control_tickets.apivalidacaion.Dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class ApiService {

    private final SigseProperties sigseProperties;
    private final RestTemplate restTemplate;

    public ApiResponseDto informacion(Integer ru) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", sigseProperties.getApiKey());

        String jsonBody = String.format("{\"ru\":%d}", ru);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        ResponseEntity<ApiResponseDto> response = restTemplate.exchange(
                sigseProperties.getUrl(),
                HttpMethod.POST,
                entity,
                ApiResponseDto.class
        );

        return response.getBody();
    }
}