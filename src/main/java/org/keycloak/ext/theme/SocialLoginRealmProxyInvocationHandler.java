package org.keycloak.ext.theme;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RequiredCredentialModel;
import org.keycloak.representations.idm.CredentialRepresentation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Realm proxy that adds mock identity providers for the social login section of {@code login.ftl}.
 */
class SocialLoginRealmProxyInvocationHandler extends RealmProxyInvocationHandler {

    SocialLoginRealmProxyInvocationHandler(KeycloakSession session) {
        super(session);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Avoid OrganizationAwareIdentityProviderBean (Keycloak main): it wraps the social bean when both the
        // ORGANIZATION feature and realm.isOrganizationsEnabled() are true, changing getForLogin modes/filters.
        // Theme preview IdPs are synthetic; force realm-level behavior unless ?realm.organizationsEnabled=true.
        if (noArgs(args) && "isOrganizationsEnabled".equals(method.getName())) {
            String override = queryParameters.getFirst("realm.organizationsEnabled");
            if (override != null) {
                return Boolean.valueOf(override);
            }
            return Boolean.FALSE;
        }
        if (noArgs(args) && "getIdentityProvidersStream".equals(method.getName())) {
            return PreviewIdentityProviders.socialStream();
        }
        // RealmBean.isPassword() (used as realm.password in login.ftl) checks required credentials for PASSWORD,
        // not a RealmModel.isPassword() method — inject one so keycloak.v2 shows the social section.
        if (noArgs(args) && "getRequiredCredentialsStream".equals(method.getName())) {
            try (Stream<RequiredCredentialModel> parent = (Stream<RequiredCredentialModel>) super.invoke(proxy, method, args)) {
              List<RequiredCredentialModel> list = parent.collect(Collectors.toCollection(ArrayList::new));
              boolean hasPassword = list.stream()
                      .anyMatch(r -> Objects.equals(r.getType(), CredentialRepresentation.PASSWORD));
              if (!hasPassword) {
                  list.add(RequiredCredentialModel.PASSWORD);
              }
              return list.stream();
            }
        }
        return super.invoke(proxy, method, args);
    }

    private static boolean noArgs(Object[] args) {
        return args == null || args.length == 0;
    }
}
