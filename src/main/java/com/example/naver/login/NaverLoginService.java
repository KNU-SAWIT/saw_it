package com.example.naver.service;

import com.example.naver.entity.NaverLoginProfile;
import com.example.naver.login.vo.NaverLoginProfileResponse;
import com.example.naver.login.vo.NaverLoginVo;
import com.example.naver.service.ProfileNotFoundException;
import com.example.naver.repository.NaverLoginProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class NaverLoginService {

    @Autowired
    private WebClient webClient;

    @Autowired
    private NaverLoginProfileRepository naverLoginProfileRepository;

    @Value("${api.naver.client_id}")
    private String clientId;

    @Value("${api.naver.client_secret}")
    private String clientSecret;

    @Transactional
    public NaverLoginProfile processNaverLogin(Map<String, String> callbackParams) {
        NaverLoginVo naverLoginVo = requestNaverLoginAccessToken(callbackParams);
        NaverLoginProfile naverLoginProfile = requestAndSaveNaverLoginProfile(naverLoginVo);
        return naverLoginProfile;
    }

    public NaverLoginVo requestNaverLoginAccessToken(Map<String, String> callbackParams) {
        final String code = callbackParams.get("code");
        final String state = callbackParams.get("state");

        String uri = UriComponentsBuilder.fromUriString("https://nid.naver.com/oauth2.0/token")
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("code", code)
                .queryParam("state", state)
                .build().encode().toUriString();

        return webClient.get().uri(uri)
                .retrieve()
                .bodyToMono(NaverLoginVo.class)
                .block();
    }

    public NaverLoginProfile requestAndSaveNaverLoginProfile(NaverLoginVo naverLoginVo) {
        String profileUri = "https://openapi.naver.com/v1/nid/me";

        NaverLoginProfileResponse profileResponse = webClient.get().uri(profileUri)
                .headers(headers -> headers.setBearerAuth(naverLoginVo.getAccess_token()))
                .retrieve()
                .bodyToMono(NaverLoginProfileResponse.class)
                .block();

        NaverLoginProfileResponse.Response profileData = profileResponse.getResponse();

        // 현재 시간을 접속 시각으로 설정
        LocalDateTime loginTime = LocalDateTime.now();

        NaverLoginProfile naverLoginProfile = NaverLoginProfile.builder()
                .name(profileData.getName())
                .email(profileData.getEmail())
                .gender(profileData.getGender())
                .birthday(profileData.getBirthday())
                .birthyear(profileData.getBirthyear())
                .mobile(profileData.getMobile())
                .loginTime(loginTime)  // 접속 시각 설정
                .build();

        return naverLoginProfileRepository.save(naverLoginProfile);
    }

    @Transactional(readOnly = true)
    public NaverLoginProfile getLastNaverProfile() {
        List<NaverLoginProfile> profiles = naverLoginProfileRepository.findLatestProfiles(); // 수정된 메서드 호출
        if (profiles.isEmpty()) {
            throw new ProfileNotFoundException("No profiles found.");
        } else if (profiles.size() > 1) {
            // 적절한 처리: 예를 들어, 첫 번째 결과만 사용하거나 로그를 기록합니다.
            System.err.println("Warning: Multiple profiles found, returning the first one.");
        }
        return profiles.get(0);
    }
}
