package org.keycloak.ext.theme;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.KeycloakSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Minimal {@link AuthenticationFlowContext} for theme preview so
 * {@link org.keycloak.forms.login.freemarker.model.AuthenticationContextBean#getAuthenticationSelections()} is non-empty.
 */
final class PreviewAuthenticationFlowContext {

    private PreviewAuthenticationFlowContext() {
    }

    static AuthenticationFlowContext create(KeycloakSession session) {
        return create(session, defaultSelections());
    }

    static AuthenticationFlowContext create(KeycloakSession session, List<PreviewAuthenticatorSelection> selections) {
        List<PreviewAuthenticatorSelection> list = selections == null || selections.isEmpty()
                ? defaultSelections()
                : selections;
        return (AuthenticationFlowContext) Proxy.newProxyInstance(
                PreviewAuthenticationFlowContext.class.getClassLoader(),
                new Class[]{AuthenticationFlowContext.class},
                new Handler(session, list));
    }

    private static List<PreviewAuthenticatorSelection> defaultSelections() {
        return List.of(
                new PreviewAuthenticatorSelection(
                        "preview-exec-password",
                        "auth-username-password-form-display-name",
                        "auth-username-password-form-help-text"),
                new PreviewAuthenticatorSelection(
                        "preview-exec-otp",
                        "otp-display-name",
                        "otp-help-text"),
                new PreviewAuthenticatorSelection(
                        "preview-exec-webauthn",
                        "webauthn-display-name",
                        "webauthn-help-text"));
    }

    private static final class Handler implements InvocationHandler {

        private final KeycloakSession session;
        private final List<PreviewAuthenticatorSelection> selections;

        Handler(KeycloakSession session, List<PreviewAuthenticatorSelection> selections) {
            this.session = session;
            this.selections = selections;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "theme-preview.AuthenticationFlowContext";
                    default -> null;
                };
            }
            return switch (method.getName()) {
                case "getAuthenticationSelections" -> Collections.unmodifiableList(selections);
                case "getUser" -> null;
                case "getAuthenticationSession" ->
                        new DummyAuthenticationSessionModel(session.getContext().getRealm());
                case "getRealm" -> session.getContext().getRealm();
                case "getSession" -> session;
                default -> defaultValue(method);
            };
        }
    }

    private static Object defaultValue(Method method) {
        Class<?> t = method.getReturnType();
        if (t == void.class) {
            return null;
        }
        if (t == boolean.class || t == Boolean.class) {
            return false;
        }
        if (t == int.class || t == Integer.class) {
            return 0;
        }
        if (t == long.class || t == Long.class) {
            return 0L;
        }
        if (!t.isPrimitive()) {
            if (t == String.class) {
                return null;
            }
            if (Map.class.isAssignableFrom(t)) {
                return Collections.emptyMap();
            }
            if (Set.class.isAssignableFrom(t)) {
                return Collections.emptySet();
            }
            if (Iterable.class.isAssignableFrom(t)) {
                return Collections.emptyList();
            }
            if (t == Stream.class) {
                return Stream.empty();
            }
        }
        return null;
    }
}
