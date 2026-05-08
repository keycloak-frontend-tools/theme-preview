package org.keycloak.ext.theme;

import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.forms.login.freemarker.FreeMarkerLoginFormsProvider;
import org.keycloak.models.KeycloakSession;

import java.lang.reflect.Field;

/**
 * {@link FreeMarkerLoginFormsProvider} reads realm/client/URI from {@link org.keycloak.models.KeycloakContext} only in
 * its constructor, but {@link org.keycloak.services.DefaultKeycloakSession} caches the provider instance for the
 * lifetime of the session. Theme preview switches realm proxies per page, so we must push the current context onto the
 * cached provider before each render.
 */
final class FreeMarkerLoginFormsContextSync {

    private FreeMarkerLoginFormsContextSync() {
    }

    static void apply(LoginFormsProvider forms, KeycloakSession session) {
        if (!(forms instanceof FreeMarkerLoginFormsProvider)) {
            return;
        }
        try {
            var ctx = session.getContext();
            setOnClassHierarchy(forms, "realm", ctx.getRealm());
            setOnClassHierarchy(forms, "client", ctx.getClient());
            setOnClassHierarchy(forms, "uriInfo", ctx.getUri());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not sync FreeMarkerLoginFormsProvider with KeycloakContext", e);
        }
    }

    private static void setOnClassHierarchy(Object target, String fieldName, Object value)
            throws ReflectiveOperationException {
        ReflectiveOperationException last = null;
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new NoSuchFieldException(fieldName);
    }
}
