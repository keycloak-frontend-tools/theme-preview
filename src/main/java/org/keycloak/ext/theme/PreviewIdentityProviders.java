package org.keycloak.ext.theme;

import org.keycloak.models.IdentityProviderModel;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Fake IdPs for theme preview so {@code login.ftl} can render the social provider block.
 */
final class PreviewIdentityProviders {

    private PreviewIdentityProviders() {
    }

    static Stream<IdentityProviderModel> socialStream() {
        return Stream.of(google(), github(), microsoft());
    }

    private static IdentityProviderModel google() {
        return model("preview-idp-google", "google", "google", "Google", mapOfGuiOrder("1"));
    }

    private static IdentityProviderModel github() {
        return model("preview-idp-github", "github", "github", "GitHub", mapOfGuiOrder("2"));
    }

    private static IdentityProviderModel microsoft() {
        return model("preview-idp-microsoft", "microsoft", "microsoft", "Microsoft", mapOfGuiOrder("3"));
    }

    private static Map<String, String> mapOfGuiOrder(String order) {
        Map<String, String> config = new HashMap<>();
        config.put("guiOrder", order);
        return config;
    }

    private static IdentityProviderModel model(
            String internalId, String alias, String providerId, String displayName, Map<String, String> config) {
        IdentityProviderModel m = new IdentityProviderModel();
        m.setInternalId(internalId);
        m.setAlias(alias);
        m.setProviderId(providerId);
        m.setEnabled(true);
        // Do not call setLinkOnly: newer Keycloak versions removed/changed this API (NoSuchMethodError at runtime).
        // A new IdentityProviderModel defaults linkOnly to false, which is what IdentityProviderBean expects.
        // hideOnLogin setter signature differs between releases (boolean vs Boolean); use reflection like setLinkOnly.
        applyHideOnLoginFalseIfSupported(m);
        m.setDisplayName(displayName);
        m.setConfig(config);
        return m;
    }

    private static void applyHideOnLoginFalseIfSupported(IdentityProviderModel m) {
        try {
            for (Class<?> param : new Class<?>[]{Boolean.class, boolean.class}) {
                try {
                    Method setter = IdentityProviderModel.class.getMethod("setHideOnLogin", param);
                    setter.invoke(m, param == boolean.class ? false : Boolean.FALSE);
                    return;
                } catch (NoSuchMethodException ignored) {
                    // try next overload
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Older/newer Keycloak versions may not expose this setter; defaults are acceptable for preview.
        }
    }
}
