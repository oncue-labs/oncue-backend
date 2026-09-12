package com.oncue.auth.identity_provider;

public interface IdentityProviderClient {

    String provider();

    ExternalIdentity resolve(String authorizationCode, String codeVerifier);
}
