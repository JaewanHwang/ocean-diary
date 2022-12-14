package com.oceandiary.api.user.service;

import com.oceandiary.api.user.domain.Role;
import com.oceandiary.api.user.domain.SocialProvider;
import com.oceandiary.api.user.dto.KakaoApiResponse;
import com.oceandiary.api.user.dto.NaverApiResponse;
import com.oceandiary.api.user.entity.User;
import com.oceandiary.api.user.exception.UserNotFoundException;
import com.oceandiary.api.user.repository.SocialLoginUserRepository;
import com.oceandiary.api.user.repository.UserRepository;
import com.oceandiary.api.user.dto.ProviderRequest;
import com.oceandiary.api.user.dto.LoginResponse;
import com.oceandiary.api.user.security.token.Token;
import com.oceandiary.api.user.security.token.TokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpSession;
import java.net.URI;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class LoginService {
    private final UserRepository userRepository;
    private final SocialLoginUserRepository socialLoginUserRepository;
    private final TokenProvider tokenProvider;

    @Value("${NAVER_API_CLIENT_ID}")
    private String naverClientId;
    @Value("${NAVER_API_CLIENT_SECRET}")
    private String naverClientSecret;
    @Value("${KAKAO_API_CLIENT_ID}")
    private String kakaoClientId;

    public LoginResponse.Login naverLogin(ProviderRequest.LoginRequest request, HttpSession session){
        String accessToken = getNaverAccessToken(request, session);
        String naverUniqueId = getNaverUniqueId(accessToken);
        User foundUser = socialLoginUserRepository.findByProviderAndOauthId(SocialProvider.NAVER, naverUniqueId);
        return checkUser(foundUser, naverUniqueId);
    }

    public LoginResponse.Login kakaoLogin(ProviderRequest.LoginRequest request){
        String accessToken = getKakaoAccessToken(request);
        String kakaoUniqueId = getKakaoUniqueId(accessToken);
        User foundUser = socialLoginUserRepository.findByProviderAndOauthId(SocialProvider.KAKAO, kakaoUniqueId);
        return checkUser(foundUser, kakaoUniqueId);
    }

    @Transactional
    public LoginResponse.JoinWithToken join(ProviderRequest.JoinRequest request, String provider){
        User newUser = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .birth(request.getBirth())
                .provider(SocialProvider.valueOf(provider.toUpperCase()))
                .role(Role.USER)
                .oauthId(request.getOauthId())
                .build();

        socialLoginUserRepository.save(newUser);

        LoginResponse.JoinWithToken response = LoginResponse.JoinWithToken.builder()
                .accessToken(tokenProvider.generateAccessToken(newUser).getToken())
                .name(newUser.getName())
                .userId(newUser.getId())
                .refreshToken(tokenProvider.generateRefreshToken(newUser).getToken())
                .build();

        return response;
    }

    public LoginResponse.Login checkUser(User foundUser, String uniqueId){
        LoginResponse.Login response;
        if(foundUser != null && foundUser.getDeletedAt() == null){
            response = LoginResponse.Login.builder()
                    .name(foundUser.getName())
                    .userId(foundUser.getId())
                    .isExist(true)
                    .oauthId(uniqueId)
                    .accessToken(tokenProvider.generateAccessToken(foundUser).getToken())
                    .build();
        } else{
            response = LoginResponse.Login.builder()
                    .isExist(false)
                    .oauthId(uniqueId)
                    .build();
        }
        return response;
    }

    public String getNaverAccessToken(ProviderRequest.LoginRequest request, HttpSession session){
        URI uri = UriComponentsBuilder
                .fromUriString("https://nid.naver.com")
                .path("/oauth2.0/token")
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", naverClientId)
                .queryParam("client_secret", naverClientSecret)
                .queryParam("code", request.getCode())
                .queryParam("state", session.getAttribute("state"))
                .encode()
                .build()
                .toUri();

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<NaverApiResponse.AccessTokenApiResponse> responseEntity
                = restTemplate.getForEntity(uri, NaverApiResponse.AccessTokenApiResponse.class);
        log.info(responseEntity.getBody().getAccessToken());
        return responseEntity.getBody().getAccessToken();
    }

    public String getKakaoAccessToken(ProviderRequest.LoginRequest request) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://kauth.kakao.com")
                .path("/oauth/token")
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", kakaoClientId)
                .queryParam("redirect_uri", "https://i7a406.p.ssafy.io/oauth2/kakao")
                .queryParam("code", request.getCode())
                .encode()
                .build()
                .toUri();
        log.info("uri : " + uri );
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<NaverApiResponse.AccessTokenApiResponse> responseEntity
                = restTemplate.getForEntity(uri, NaverApiResponse.AccessTokenApiResponse.class);
        log.info(responseEntity.getBody().getAccessToken());
        return responseEntity.getBody().getAccessToken();
    }

    public String getNaverUniqueId(String accessToken){
        URI uri = UriComponentsBuilder
                .fromUriString("https://openapi.naver.com")
                .path("/v1/nid/me")
                .encode()
                .build()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity request = new HttpEntity(headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<NaverApiResponse.ProfileApiResponse> responseEntity = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                request,
                NaverApiResponse.ProfileApiResponse.class
        );
        log.info("NAVER Unique ID = {}", responseEntity.getBody().getResponse().getId());
        return responseEntity.getBody().getResponse().getId();
    }

    public String getKakaoUniqueId(String accessToken) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://kapi.kakao.com")
                .path("/v2/user/me")
                .encode()
                .build()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity request = new HttpEntity(headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<KakaoApiResponse.ProfileApiResponse> responseEntity = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                request,
                KakaoApiResponse.ProfileApiResponse.class
        );
        log.info("KAKAO Unique ID = {}", responseEntity.getBody().getId());
        return responseEntity.getBody().getId().toString();
    }
    public LoginResponse.Auth createAccessTokenAndRefreshToken(String refreshToken) {
        Long userId = tokenProvider.getUserIdFromRefreshToken(refreshToken);
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Token createdRefreshToken = tokenProvider.generateRefreshToken(user);
        return LoginResponse.Auth.build(user, tokenProvider.generateAccessToken(user), createdRefreshToken);
    }
}
