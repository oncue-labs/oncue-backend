package com.oncue.auth.identity_provider;

import com.oncue.auth.controller.request.LoginRequest;

public interface IdentityProviderClient {

    String provider();

    ExternalIdentity resolve(LoginRequest request);
}
