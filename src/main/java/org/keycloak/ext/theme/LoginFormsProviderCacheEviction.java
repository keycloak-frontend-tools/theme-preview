package org.keycloak.ext.theme;

import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.Provider;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * {@link org.keycloak.services.DefaultKeycloakSession} caches {@link LoginFormsProvider} (and other providers) in a
 * map keyed by {@code clazz.hashCode()}. Theme preview swaps realm proxies per URL; evicting the cached login forms
 * provider forces a new {@link org.keycloak.forms.login.freemarker.FreeMarkerLoginFormsProvider} whose constructor
 * reads the current realm from {@link org.keycloak.models.KeycloakContext}.
 */
final class LoginFormsProviderCacheEviction {

    private LoginFormsProviderCacheEviction() {
    }

    static void evictLoginFormsProvider(KeycloakSession session) {
        evict(session, LoginFormsProvider.class);
    }

    private static void evict(KeycloakSession session, Class<?> providerClass) {
        Map<Integer, Provider> map = findProvidersMap(session);
        if (map == null) {
            return;
        }
        Integer key = providerClass.hashCode();
        Provider removed = map.remove(key);
        if (removed != null) {
            try {
                removed.close();
            } catch (RuntimeException ignored) {
                // no-op
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Provider> findProvidersMap(KeycloakSession session) {
        for (Class<?> c = session.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("providers");
                f.setAccessible(true);
                return (Map<Integer, Provider>) f.get(session);
            } catch (NoSuchFieldException ignored) {
                // continue
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not access KeycloakSession.providers", e);
            }
        }
        return null;
    }
}
