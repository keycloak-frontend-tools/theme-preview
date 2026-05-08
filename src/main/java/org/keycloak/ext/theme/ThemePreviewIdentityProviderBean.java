package org.keycloak.ext.theme;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.forms.login.freemarker.model.IdentityProviderBean;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Supplies synthetic identity providers for theme preview without relying on
 * {@code session.identityProviders().getForLogin} or realm storage (Quarkus-friendly).
 */
final class ThemePreviewIdentityProviderBean extends IdentityProviderBean {

    ThemePreviewIdentityProviderBean(
            KeycloakSession session,
            RealmModel realm,
            URI baseURI,
            AuthenticationFlowContext context) {
        super(session, realm, baseURI, context);
    }

    @Override
    protected List<IdentityProviderBean.IdentityProvider> searchForIdentityProviders(String existingIDP) {
        return PreviewIdentityProviders.socialStream()
                .filter(idp -> !Objects.equals(existingIDP, idp.getAlias()))
                .map(idp -> createIdentityProvider(realm, baseURI, idp))
                .sorted(IDP_COMPARATOR_INSTANCE)
                .toList();
    }

    /**
     * Runs after {@code createCommonAttributes}; replaces the {@code social} template attribute with a bean that
     * renders preview brokers regardless of realm IdP storage.
     */
    static Function<Map<String, Object>, Map<String, Object>> socialAttributeMapper() {
        return map -> {
            Object social = map.get("social");
            if (social instanceof IdentityProviderBean bean && !(social instanceof ThemePreviewIdentityProviderBean)) {
                HashMap<String, Object> copy = new HashMap<>(map);
                copy.put(
                        "social",
                        new ThemePreviewIdentityProviderBean(
                                bean.getSession(), bean.getRealm(), bean.getBaseURI(), bean.getFlowContext()));
                return copy;
            }
            return map;
        };
    }
}
