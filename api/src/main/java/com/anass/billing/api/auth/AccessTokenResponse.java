package com.anass.billing.api.auth;

public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {
}
